package studio.elysium.dragonuniverse.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.elysium.dragonuniverse.client.render.post.DUEntityNormals;

/**
 * Enables the entity geometry-normals MRT only while an entity (non-translucent) pipeline draws
 * to the main target. Injected at the TAIL of {@code setPipeline} — by then {@code getOrCompilePipeline}
 * has compiled the program (so our shader inject has run and readiness is known), and the pass's FBO is
 * already bound (from {@code createRenderPass}), so the raw-GL attach/draw-buffer flip lands on it and
 * persists through the subsequent draws. {@link DUEntityNormals} state-tracks so GL is only touched on a
 * transition.
 *
 * <p>The {@code close()} HEAD inject restores the single color draw buffer if this pass left MRT on —
 * done here (not at the next {@code createRenderPass}) because {@code close()}'s {@code finishRenderPass}
 * binds the <i>default</i> framebuffer (0); detaching afterward would hit FBO 0 and raise GL errors. At
 * {@code close()} HEAD the pass's own FBO is still bound.</p>
 *
 * <p>{@code require = 0} fail-soft.</p>
 */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlRenderPass")
public class GlRenderPassNormalScopeMixin {

    @Inject(method = "setPipeline", at = @At("TAIL"), require = 0)
    private void dragonuniverse$scopeNormals(RenderPipeline pipeline, CallbackInfo ci) {
        try {
            DUEntityNormals.onSetPipeline(pipeline);
        } catch (Throwable ignored) {
            // never let normal scoping disturb the draw
        }
    }

    @Inject(method = "close", at = @At("HEAD"), require = 0)
    private void dragonuniverse$restoreNormalsOnClose(CallbackInfo ci) {
        try {
            DUEntityNormals.onClosePass();
        } catch (Throwable ignored) {
            // never let normal scoping disturb pass teardown
        }
    }
}
