package studio.elysium.dragonuniverse.client.render.sky;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import studio.elysium.dragonuniverse.DragonUniverse;

/**
 * Fullscreen sky-layer pipelines. Each reuses the {@code POST_PROCESSING_SNIPPET} +
 * vanilla {@code core/screenquad} fullscreen-triangle vertex shader (same as the post chain) with
 * its own procedural fragment shader, declares the two sky UBOs, disables depth (so terrain still
 * draws over the sky), and picks a blend mode appropriate to the layer.
 */
@EventBusSubscriber(modid = DragonUniverse.MODID, value = Dist.CLIENT)
public final class DUSkyPipelines {
    private DUSkyPipelines() {}

    private static final DepthStencilState NO_DEPTH = new DepthStencilState(CompareOp.ALWAYS_PASS, false);

    private static RenderPipeline.Builder base(String location, String frag, BlendFunction blend) {
        return RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(DragonUniverse.MODID, "pipeline/" + location))
                .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(DragonUniverse.MODID, "core/" + frag))
                .withUniform("DUSkyView", UniformType.UNIFORM_BUFFER)
                .withUniform("DUSkyLayer", UniformType.UNIFORM_BUFFER)
                .withColorTargetState(new ColorTargetState(blend))
                .withDepthStencilState(NO_DEPTH);
    }

    private static RenderPipeline build(String location, String frag, BlendFunction blend) {
        return base(location, frag, blend).build();
    }

    private static RenderPipeline buildSampled(String location, String frag, BlendFunction blend) {
        return base(location, frag, blend).withSampler("Sampler0").build();
    }

    public static final RenderPipeline STARFIELD = build("du_sky_stars", "du_sky_stars", BlendFunction.ADDITIVE);
    public static final RenderPipeline NEBULA = build("du_sky_nebula", "du_sky_nebula", BlendFunction.ADDITIVE);
    public static final RenderPipeline ATMOSPHERE = build("du_sky_atmosphere", "du_sky_atmosphere", BlendFunction.TRANSLUCENT);
    public static final RenderPipeline TEXTURE_ADDITIVE = buildSampled("du_sky_tex_add", "du_sky_texture", BlendFunction.ADDITIVE);
    public static final RenderPipeline TEXTURE_ALPHA = buildSampled("du_sky_tex_alpha", "du_sky_texture", BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA);
    public static final RenderPipeline CUBEMAP_ADDITIVE = buildSampled("du_sky_cube_add", "du_sky_cubemap", BlendFunction.ADDITIVE);
    public static final RenderPipeline CUBEMAP_ALPHA = buildSampled("du_sky_cube_alpha", "du_sky_cubemap", BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA);

    public static RenderPipeline forLayer(DUSkyLayer layer) {
        return switch (layer.type) {
            case STARFIELD -> STARFIELD;
            case NEBULA -> NEBULA;
            case ATMOSPHERE -> ATMOSPHERE;
            case TEXTURE -> layer.additive ? TEXTURE_ADDITIVE : TEXTURE_ALPHA;
            case CUBEMAP -> layer.additive ? CUBEMAP_ADDITIVE : CUBEMAP_ALPHA;
        };
    }

    @SubscribeEvent
    public static void onRegisterPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(STARFIELD);
        event.registerPipeline(NEBULA);
        event.registerPipeline(ATMOSPHERE);
        event.registerPipeline(TEXTURE_ADDITIVE);
        event.registerPipeline(TEXTURE_ALPHA);
        event.registerPipeline(CUBEMAP_ADDITIVE);
        event.registerPipeline(CUBEMAP_ALPHA);
    }
}
