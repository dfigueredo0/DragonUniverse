package studio.elysium.dragonuniverse.mixin.sodium;

import com.mojang.blaze3d.textures.GpuSampler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import studio.elysium.dragonuniverse.client.render.post.DUShadowPass;

/**
 * Drives the DU shadow stack's sun's-eye geometry pass (Stage A). Sodium's
 * {@code DefaultChunkRenderer.render(matrices, …)} takes its view-projection as a parameter, so a
 * second pass from the light's POV is just that method re-invoked with a light-space matrix — see
 * {@link DUShadowPass}. We fire it from the TAIL of the frame's first non-translucent (opaque)
 * terrain {@code render()}, reusing that call's own visible-section lists / camera / fog / sampler.
 *
 * <p>All Sodium params are taken via {@link Coerce} (the project never links Sodium types — the same
 * soft-compat approach as the normals/water mixins). {@code require = 0} so a renamed method on a
 * newer Sodium silently no-ops; {@link DUShadowPass} is fail-soft on any reflection/GL error.</p>
 */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer", remap = false)
public class DefaultChunkRendererMixin {

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void dragonuniverse$shadowPass(@Coerce Object matrices, @Coerce Object commandList,
                                           @Coerce Object renderLists, @Coerce Object renderPass,
                                           @Coerce Object camera, @Coerce Object fog, boolean sort,
                                           GpuSampler sampler, CallbackInfo ci) {
        // Reentrancy: our own re-invoke also reaches this TAIL — never recurse.
        if (DUShadowPass.isActive() || !DUShadowPass.isEnabled()) {
            return;
        }
        // Fire on each non-translucent terrain pass — opaque (solid) AND cutout (foliage), so leaves
        // cast dappled shade. The pass clears its depth once per frame and accumulates the rest.
        if (DUShadowPass.isTranslucent(renderPass)) {
            // Stage D (colored shadows): the translucent pass feeds the SECOND target — translucent
            // occluder depth + tint — via its own re-invoke. Off → nothing happens here (opaque-only).
            if (DUShadowPass.isColoredEnabled()) {
                DUShadowPass.renderTranslucentFromSun(this, commandList, renderLists, renderPass, camera, fog, sampler);
            }
            return;
        }
        DUShadowPass.renderFromSun(this, matrices, commandList, renderLists, renderPass, camera, fog, sampler);
    }

    /**
     * Defeat Sodium's per-section <b>camera</b>-facing cull during the sun's-eye re-invoke. Sodium draws
     * only the quads facing the camera (an occlusion optimization); reused for the light POV that drops
     * every face not pointing at the camera — but a shadow caster occludes with its <i>sun</i>-facing
     * faces. So while the shadow pass is active, return "all faces" ({@code -1}); {@code fillCommandBuffer}
     * uses {@code getVisibleFaces(...) & sliceMask}, and {@code -1 & sliceMask == sliceMask}, i.e. every
     * face the section actually has. The normal camera render is untouched (gated on {@link DUShadowPass#isActive}).
     *
     * <p>This is the per-<i>face</i> counterpart to the own-visible-set gather (which fixed per-<i>section</i>
     * selection). Without it, a caster only shadows when its sun-facing side happens to face the camera, so
     * thin/short casters and the lower faces of tall columns silently drop their ground shadow. {@code require = 0}
     * → a renamed method on a newer Sodium silently falls back to the cull (bug returns, no crash).</p>
     */
    @Inject(method = "getVisibleFaces", at = @At("HEAD"), cancellable = true, require = 0)
    private static void dragonuniverse$allFacesForShadow(int camX, int camY, int camZ, int secX, int secY,
                                                         int secZ, CallbackInfoReturnable<Integer> cir) {
        if (DUShadowPass.isActive()) {
            cir.setReturnValue(-1);
        }
    }
}
