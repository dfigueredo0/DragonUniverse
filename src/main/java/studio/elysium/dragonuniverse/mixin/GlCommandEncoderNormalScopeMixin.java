package studio.elysium.dragonuniverse.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.lwjgl.opengl.GL30C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import studio.elysium.dragonuniverse.client.render.post.DUEntityNormals;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * Records whether each new render pass targets the main color (the only target we ever enable
 * the entity geometry-normals MRT for). The per-pipeline attach and the close-time restore both happen in
 * {@link GlRenderPassNormalScopeMixin} — restore must not happen at the next {@code createRenderPass},
 * because by then the previous pass's {@code finishRenderPass} has bound the default framebuffer (0) and
 * a draw-buffer/attachment call would hit FBO 0 (GL error).
 *
 * <p>Targets the 5-arg overload (the one that actually binds the FBO); the 3-arg overload delegates to
 * it. {@code require = 0} fail-soft.</p>
 *
 * <p>The {@code applyPipelineState} hook disables blending on draw buffer 1 (the normal attachment) while
 * the MRT is active, so translucent normal-writing pipelines (clouds) write the normal <i>unblended</i>
 * while their color output keeps its {@code TRANSLUCENT} blend on buffer 0. {@code applyPipelineState}
 * runs on pipeline change and sets blend with the non-indexed {@code glEnable/glBlendFunc} (all buffers),
 * so this per-index override at TAIL sticks until the next pipeline change.</p>
 */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
public class GlCommandEncoderNormalScopeMixin {

    private static final int GL_BLEND = 0x0BE2;

    @Inject(
            method = "createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalInt;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/blaze3d/systems/RenderPassBackend;",
            at = @At("HEAD"),
            require = 0
    )
    private void dragonuniverse$scopeNormalsCreatePass(Supplier<String> label, GpuTextureView colorTexture,
                                                       OptionalInt clearColor, GpuTextureView depthTexture,
                                                       OptionalDouble clearDepth, CallbackInfoReturnable<?> cir) {
        try {
            DUEntityNormals.onCreateRenderPass(colorTexture);
        } catch (Throwable ignored) {
            // never let normal scoping disturb the render pass
        }
    }

    @Inject(method = "applyPipelineState", at = @At("TAIL"), require = 0)
    private void dragonuniverse$disableNormalBlend(RenderPipeline pipeline, CallbackInfo ci) {
        try {
            if (DUEntityNormals.isMrtActive()) {
                // Draw buffer 1 = the normal attachment: never blend it (harmless no-op for non-blending
                // entity pipelines; essential for the translucent cloud pipeline).
                GL30C.glDisablei(GL_BLEND, 1);
            }
        } catch (Throwable ignored) {
            // never let normal scoping disturb pipeline state
        }
    }
}
