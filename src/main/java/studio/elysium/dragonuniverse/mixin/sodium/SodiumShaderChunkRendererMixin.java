package studio.elysium.dragonuniverse.mixin.sodium;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.elysium.dragonuniverse.DragonUniverse;
import studio.elysium.dragonuniverse.client.render.core.DUSsrData;
import studio.elysium.dragonuniverse.client.render.core.DUWaterData;
import studio.elysium.dragonuniverse.client.render.post.DUNormalBuffer;
import studio.elysium.dragonuniverse.client.render.post.DUWaterGbuffer;
import studio.elysium.dragonuniverse.client.render.water.DUWater;

import java.lang.reflect.Method;

/**
 * Binds the geometry-normals texture as {@code COLOR_ATTACHMENT1} for Sodium's opaque/cutout terrain
 * passes, so the injected terrain shader (see {@link SodiumShaderLoaderMixin}) writes world-space
 * normals via MRT into the look stack's normal buffer.
 *
 * <p> {@code begin} binds the target's real GL FBO via raw GL ({@code GlTexture.getFbo} → {@code _glBindFramebuffer})
 * and then applies pipeline state, so at {@code begin}'s TAIL the FBO is current and raw-GL MRT manipulation is valid.
 * {@code end} runs while that same FBO is still bound (nothing rebinds during the draw), so it's where we restore
 * the single color draw buffer and detach.</p>
 *
 * <p> Opaque/cutout/translucent share one program, and must not let translucent's blend state bleed into the
 * normal/coverage output — so the MRT binding is scoped to non-translucent passes only. </p>
 *
 * <p>The {@code TerrainRenderPass} param is taken via {@link Coerce} and {@code isTranslucent()} read
 * reflectively (the same soft-compat approach as {@code DUIrisCompat}). The bound FBO is the main target
 * regardless, so size the normals texture from {@link Minecraft#getMainRenderTarget()}.</p>
 *
 * <p><b>Fail-soft:</b> {@code require = 0} so a renamed method on a newer Sodium silently no-ops instead
 * of crashing; reflection/GL failures disable capture (treated as "translucent" = skip) rather than
 * killing terrain.</p>
 */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer", remap = false)
public class SodiumShaderChunkRendererMixin {

    /** True between a non-translucent {@code begin}'s attach and its matching {@code end} detach. */
    @Unique
    private static boolean dragonuniverse$attached = false;

    /** True between the translucent water-gbuf attach and its matching {@code end} detach. */
    @Unique
    private static boolean dragonuniverse$waterGbufAttached = false;

    @Unique
    private static Method dragonuniverse$isTranslucent;
    @Unique
    private static boolean dragonuniverse$reflectFailed = false;

    @Inject(method = "begin", at = @At("TAIL"), require = 0)
    private void dragonuniverse$attachNormals(@Coerce Object pass, @Coerce Object fog, @Coerce Object sampler,
                                              CallbackInfo ci) {
        if (dragonuniverse$isTranslucent(pass) || !DUNormalBuffer.shouldCapture()) {
            return;
        }
        try {
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            if (main == null || main.width <= 0 || main.height <= 0) {
                return;
            }
            DUNormalBuffer.attach(main.width, main.height);
            dragonuniverse$attached = true;
        } catch (Throwable t) {
            dragonuniverse$attached = false;
            DUNormalBuffer.markShaderFailed("MRT attach threw: " + t.getClass().getSimpleName());
            DragonUniverse.LOGGER.warn("[Dragon Universe] Geometry-normals MRT attach failed; disabling.", t);
        }
    }

    /**
     * Snapshot the opaque depth just before the <b>translucent</b> terrain pass draws, so the injected
     * water fragment (see {@link SodiumShaderLoaderMixin}) can read the geometry behind the surface.
     * HEAD = before Sodium binds its own FBO/program, so the copy doesn't disturb its state.
     */
    @Inject(method = "begin", at = @At("HEAD"), require = 0)
    private void dragonuniverse$captureWaterDepth(@Coerce Object pass, @Coerce Object fog, @Coerce Object sampler,
                                                  CallbackInfo ci) {
        if (!dragonuniverse$isTranslucent(pass) || !DUWaterData.enabled || !DUWater.shaderReady()) {
            return;
        }
        try {
            DUWater.captureOpaqueDepth();
        } catch (Throwable t) {
            DUWater.markFailed("depth capture threw: " + t.getClass().getSimpleName());
        }
    }

    /**
     * Bind the water uniforms (camera data, opaque-depth sampler, material params) onto
     * Sodium's now-current terrain program at the translucent {@code begin}'s TAIL. Runs whenever the
     * inject is present so {@code du_WaterEnabled} reliably turns the branch off when water
     * is disabled, rather than leaving it reading a stale binding.
     */
    @Inject(method = "begin", at = @At("TAIL"), require = 0)
    private void dragonuniverse$bindWater(@Coerce Object pass, @Coerce Object fog, @Coerce Object sampler,
                                          CallbackInfo ci) {
        if (!dragonuniverse$isTranslucent(pass) || !DUWater.shaderReady()) {
            return;
        }
        try {
            DUWater.bindWaterUniforms();
        } catch (Throwable t) {
            DUWater.markFailed("water bind threw: " + t.getClass().getSimpleName());
        }
    }

    /**
     * Attach the water surface G-buffer at {@code COLOR_ATTACHMENT2} for the <b>translucent</b>
     * terrain pass, so the injected water fragment can stash its surface normal/fresnel/depth for the
     * composite-stage SSR raymarch. Gated on SSR being enabled so the MRT costs nothing when it's off.
     */
    @Inject(method = "begin", at = @At("TAIL"), require = 0)
    private void dragonuniverse$attachWaterGbuf(@Coerce Object pass, @Coerce Object fog, @Coerce Object sampler,
                                                CallbackInfo ci) {
        if (!dragonuniverse$isTranslucent(pass) || !DUSsrData.enabled || !DUWater.shaderReady()) {
            return;
        }
        try {
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            if (main == null || main.width <= 0 || main.height <= 0) {
                return;
            }
            DUWaterGbuffer.attach(main.width, main.height);
            dragonuniverse$waterGbufAttached = true;
        } catch (Throwable t) {
            dragonuniverse$waterGbufAttached = false;
            DragonUniverse.LOGGER.warn("[Dragon Universe] Water SSR G-buffer attach failed; SSR disabled "
                    + "this frame (terrain + water unaffected).", t);
        }
    }

    @Inject(method = "end", at = @At("HEAD"), require = 0)
    private void dragonuniverse$detachNormals(@Coerce Object pass, CallbackInfo ci) {
        if (!dragonuniverse$attached) {
            return;
        }
        dragonuniverse$attached = false;
        try {
            DUNormalBuffer.detach();
        } catch (Throwable t) {
            DragonUniverse.LOGGER.warn("[Dragon Universe] Geometry-normals MRT detach failed.", t);
        }
    }

    @Inject(method = "end", at = @At("HEAD"), require = 0)
    private void dragonuniverse$detachWaterGbuf(@Coerce Object pass, CallbackInfo ci) {
        if (!dragonuniverse$waterGbufAttached) {
            return;
        }
        dragonuniverse$waterGbufAttached = false;
        try {
            DUWaterGbuffer.detach();
        } catch (Throwable t) {
            DragonUniverse.LOGGER.warn("[Dragon Universe] Water SSR G-buffer detach failed.", t);
        }
    }

    /** Reflectively read {@code TerrainRenderPass.isTranslucent()}; on any failure, default to true
     * (skip capture) so we never attach MRT to a pass we can't classify. */
    private static boolean dragonuniverse$isTranslucent(Object pass) {
        if (dragonuniverse$reflectFailed || pass == null) {
            return true;
        }
        try {
            if (dragonuniverse$isTranslucent == null) {
                dragonuniverse$isTranslucent = pass.getClass().getMethod("isTranslucent");
            }
            return (boolean) dragonuniverse$isTranslucent.invoke(pass);
        } catch (Throwable t) {
            dragonuniverse$reflectFailed = true;
            DUNormalBuffer.markShaderFailed("isTranslucent() reflect failed: " + t.getClass().getSimpleName());
            return true;
        }
    }
}
