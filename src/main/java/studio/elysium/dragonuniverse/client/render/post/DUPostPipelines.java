package studio.elysium.dragonuniverse.client.render.post;

import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import studio.elysium.dragonuniverse.DragonUniverse;

/**
 * <p>All are built on vanilla's {@code POST_PROCESSING_SNIPPET} (EMPTY vertex format, drawn
 * vertexless via {@code draw(0, 3)}) and reuse vanilla's {@code core/screenquad} fullscreen
 * vertex shader — exactly how {@code PostChain.createPass} assembles a pass. Each declares an
 * explicit no-blend color state and a disabled depth state ({@code ALWAYS_PASS}, no write) so the
 * passes don't depend on a depth attachment (the bloom targets have none).</p>
 *
 * <p>Chain order at runtime (see {@link DUPostChain}): fog → bright-extract → blur H → blur V →
 * composite.</p>
 */
@EventBusSubscriber(modid = DragonUniverse.MODID, value = Dist.CLIENT)
public final class DUPostPipelines {
    private DUPostPipelines() {}

    private static final ColorTargetState COLOR = ColorTargetState.DEFAULT; // no blend, write all
    private static final DepthStencilState NO_DEPTH = new DepthStencilState(CompareOp.ALWAYS_PASS, false);

    private static Identifier du(String path) {
        return Identifier.fromNamespaceAndPath(DragonUniverse.MODID, path);
    }

    private static RenderPipeline.Builder post(String location, String fragShader) {
        return RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(du("pipeline/" + location))
                .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
                .withFragmentShader(du("core/" + fragShader))
                .withColorTargetState(COLOR)
                .withDepthStencilState(NO_DEPTH);
    }

    /** Depth fog: blends scene toward the sky-tint fog color by linearized depth. */
    public static final RenderPipeline FOG = post("du_post_fog", "du_fog")
            .withSampler("InSampler")
            .withSampler("DepthSampler")
            .withUniform("DUFrame", UniformType.UNIFORM_BUFFER)
            .build();

    /** Bloom bright-extract: keeps pixels above a luma threshold. */
    public static final RenderPipeline BRIGHT = post("du_post_bright", "du_bright")
            .withSampler("InSampler")
            .build();

    /** Separable Gaussian blur, horizontal pass (HORIZONTAL define set). */
    public static final RenderPipeline BLUR_H = post("du_post_blur_h", "du_blur")
            .withShaderDefine("HORIZONTAL")
            .withSampler("InSampler")
            .build();

    /** Separable Gaussian blur, vertical pass. */
    public static final RenderPipeline BLUR_V = post("du_post_blur_v", "du_blur")
            .withSampler("InSampler")
            .build();

    /** Final composite: (fogged) scene * tint * pulse + bloom * intensity. */
    public static final RenderPipeline COMPOSITE = post("du_post_composite", "du_post")
            .withSampler("InSampler")
            .withSampler("BloomSampler")
            .withUniform("DUFrame", UniformType.UNIFORM_BUFFER)
            .build();

    /** Debug-only: linearized grayscale depth for the Target Inspector. */
    public static final RenderPipeline DEPTHVIZ = post("du_post_depthviz", "du_depthviz")
            .withSampler("DepthSampler")
            .withUniform("DUFrame", UniformType.UNIFORM_BUFFER)
            .build();

    @SubscribeEvent
    public static void onRegisterPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(FOG);
        event.registerPipeline(BRIGHT);
        event.registerPipeline(BLUR_H);
        event.registerPipeline(BLUR_V);
        event.registerPipeline(COMPOSITE);
        event.registerPipeline(DEPTHVIZ);
    }
}
