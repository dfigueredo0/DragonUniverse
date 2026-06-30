package studio.elysium.dragonuniverse.client.render.post;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
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

    // Multiply blend: result.rgb = src.rgb * dst.rgb (srcFactor DST_COLOR, dstFactor ZERO); alpha is
    // left as the destination's (srcAlpha ZERO, dstAlpha ONE). Used by the SSAO composite to darken
    // the scene by the occlusion factor.
    private static final BlendFunction MULTIPLY =
            new BlendFunction(SourceFactor.DST_COLOR, DestFactor.ZERO, SourceFactor.ZERO, DestFactor.ONE);

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

    /** Like {@link #post} but additive-blended onto the target (for composites that add light). */
    private static RenderPipeline.Builder postAdditive(String location, String fragShader) {
        return RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(du("pipeline/" + location))
                .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
                .withFragmentShader(du("core/" + fragShader))
                .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
                .withDepthStencilState(NO_DEPTH);
    }

    /** Like {@link #post} but multiply-blended onto the target (for AO that darkens the scene). */
    private static RenderPipeline.Builder postMultiply(String location, String fragShader) {
        return RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(du("pipeline/" + location))
                .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
                .withFragmentShader(du("core/" + fragShader))
                .withColorTargetState(new ColorTargetState(MULTIPLY))
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

    /** Bloom: soft-knee bright-extract from the HDR overlay (threshold from the DULook UBO). */
    public static final RenderPipeline BLOOM_PREFILTER = post("du_post_bloom_prefilter", "du_bloom_prefilter")
            .withSampler("InSampler")
            .withUniform("DULook", UniformType.UNIFORM_BUFFER)
            .build();

    /** SSAO: view-space hemisphere occlusion from depth + geometry normals (+ optional Hi-Z). */
    public static final RenderPipeline SSAO = post("du_post_ssao", "du_ssao")
            .withSampler("DepthSampler")
            .withSampler("NormalSampler")
            .withSampler("HiZSampler")
            .withUniform("DUView", UniformType.UNIFORM_BUFFER)
            .withUniform("DUSsao", UniformType.UNIFORM_BUFFER)
            .build();

    /** SSAO: depth-aware (bilateral) blur of the raw AO to kill the IGN noise. */
    public static final RenderPipeline SSAO_BLUR = post("du_post_ssao_blur", "du_ssao_blur")
            .withSampler("InSampler")
            .withSampler("DepthSampler")
            .withUniform("DUView", UniformType.UNIFORM_BUFFER)
            .build();

    /** SSAO: multiply the blurred AO into the scene (before the additive passes). */
    public static final RenderPipeline SSAO_COMPOSITE = postMultiply("du_post_ssao_composite", "du_ssao_composite")
            .withSampler("InSampler")
            .build();

    /** Hi-Z pyramid level 0: linearize the scene depth into packed RGBA8. */
    public static final RenderPipeline HIZ_INIT = post("du_post_hiz_init", "du_hiz_init")
            .withSampler("DepthSampler")
            .withUniform("DUView", UniformType.UNIFORM_BUFFER)
            .build();

    /** Hi-Z pyramid: min (closest) downsample of the previous level. */
    public static final RenderPipeline HIZ_DOWN = post("du_post_hiz_down", "du_hiz_down")
            .withSampler("InSampler")
            .build();

    /** Hi-Z debug viz: unpack a chosen level to grayscale for the Target Inspector. */
    public static final RenderPipeline HIZ_VIZ = post("du_post_hiz_viz", "du_hiz_viz")
            .withSampler("InSampler")
            .build();

    /** Shadow-map debug viz (Stage A): raw sun's-eye depth as grayscale for the Target Inspector. */
    public static final RenderPipeline SHADOW_VIZ = post("du_post_shadow_viz", "du_shadow_viz")
            .withSampler("DepthSampler")
            .build();

    /** Shadow apply (Stage B + Stage D colored): sample the opaque sun's-eye map per fragment, multiply-darken
     *  shadowed terrain, and tint by the translucent occluder map where one occludes a sun-lit fragment. */
    public static final RenderPipeline SHADOW_APPLY = postMultiply("du_post_shadow_apply", "du_shadow_apply")
            .withSampler("DepthSampler")
            .withSampler("NormalSampler")
            .withSampler("ShadowSampler")
            .withSampler("ColoredDepthSampler")
            .withSampler("ColoredTintSampler")
            .withUniform("DUShadow", UniformType.UNIFORM_BUFFER)
            .build();

    /** final color grade + directional sky-tint (overwrites the composited frame). No blend. */
    public static final RenderPipeline COLOR_GRADE = post("du_post_color_grade", "du_color_grade")
            .withSampler("InSampler")
            .withUniform("DUGrade", UniformType.UNIFORM_BUFFER)
            .withUniform("DUView", UniformType.UNIFORM_BUFFER)
            .withUniform("DUGodray", UniformType.UNIFORM_BUFFER)
            .build();

    /** ripple sim: advance the height field (reproject + damped wave eq + injection) into the
     * float ping-pong target. Reads the previous field; written by {@code DUWaterSim} at AfterSky. */
    public static final RenderPipeline WATER_SIM = post("du_post_water_sim", "du_water_sim")
            .withSampler("PrevField")
            .withUniform("DUWaterSim", UniformType.UNIFORM_BUFFER)
            .build();

    /** SSR: refined raymarch + sky fallback + fresnel blend + roughness + temporal accum. */
    public static final RenderPipeline SSR = post("du_post_ssr", "du_ssr")
            .withSampler("SceneColorSampler")
            .withSampler("SceneDepthSampler")
            .withSampler("WaterGbufSampler")
            .withSampler("HistorySampler")
            .withUniform("DUView", UniformType.UNIFORM_BUFFER)
            .withUniform("DUSsr", UniformType.UNIFORM_BUFFER)
            .withUniform("DUGodray", UniformType.UNIFORM_BUFFER)
            .build();

    /** underwater scene fog: exp distance-tint the scene toward the underwater colour. */
    public static final RenderPipeline UNDERWATER = post("du_post_underwater", "du_underwater")
            .withSampler("InSampler")
            .withSampler("DepthSampler")
            .withUniform("DUView", UniformType.UNIFORM_BUFFER)
            .withUniform("DUWaterFog", UniformType.UNIFORM_BUFFER)
            .build();

    /** god rays: sky/occlusion mask from depth. */
    public static final RenderPipeline GODRAY_MASK = post("du_post_godray_mask", "du_godray_mask")
            .withSampler("DepthSampler")
            .build();

    /** god rays: radial light-scattering blur of the mask toward the sun. */
    public static final RenderPipeline GODRAY_BLUR = post("du_post_godray_blur", "du_godray_blur")
            .withSampler("InSampler")
            .withUniform("DUGodray", UniformType.UNIFORM_BUFFER)
            .build();

    /** god rays: tint by sun colour/gain and add onto the scene. */
    public static final RenderPipeline GODRAY_COMPOSITE = postAdditive("du_post_godray_composite", "du_godray_composite")
            .withSampler("InSampler")
            .withUniform("DUGodray", UniformType.UNIFORM_BUFFER)
            .build();

    /** rim light: Fresnel edge glow from the Task 3 normals, added onto the scene. */
    public static final RenderPipeline RIM = postAdditive("du_post_rim", "du_rim")
            .withSampler("NormalSampler")
            .withSampler("DepthSampler")
            .withUniform("DUView", UniformType.UNIFORM_BUFFER)
            .withUniform("DUGodray", UniformType.UNIFORM_BUFFER)
            .build();

    /**
     * HDR foundation + bloom: tone map (exposure + ACES/Reinhard) the float HDR
     * VFX-overlay <b>plus</b> the blurred bloom, and composite it <b>additively</b> onto the pristine
     * main target. Additive (not overwrite) so the base scene is untouched and only the glow overlay
     * is added — where overlay and bloom are empty, {@code tonemap(0) == 0} adds nothing.
     */
    public static final RenderPipeline TONEMAP = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(du("pipeline/du_post_tonemap"))
            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
            .withFragmentShader(du("core/du_tonemap"))
            .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
            .withDepthStencilState(NO_DEPTH)
            .withSampler("InSampler")
            .withSampler("BloomSampler")
            .withUniform("DULook", UniformType.UNIFORM_BUFFER)
            .build();

    @SubscribeEvent
    public static void onRegisterPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(FOG);
        event.registerPipeline(BRIGHT);
        event.registerPipeline(BLUR_H);
        event.registerPipeline(BLUR_V);
        event.registerPipeline(COMPOSITE);
        event.registerPipeline(DEPTHVIZ);
        event.registerPipeline(BLOOM_PREFILTER);
        event.registerPipeline(SSAO);
        event.registerPipeline(SSAO_BLUR);
        event.registerPipeline(SSAO_COMPOSITE);
        event.registerPipeline(HIZ_INIT);
        event.registerPipeline(HIZ_DOWN);
        event.registerPipeline(HIZ_VIZ);
        event.registerPipeline(SHADOW_VIZ);
        event.registerPipeline(SHADOW_APPLY);
        event.registerPipeline(COLOR_GRADE);
        event.registerPipeline(WATER_SIM);
        event.registerPipeline(SSR);
        event.registerPipeline(UNDERWATER);
        event.registerPipeline(GODRAY_MASK);
        event.registerPipeline(GODRAY_BLUR);
        event.registerPipeline(GODRAY_COMPOSITE);
        event.registerPipeline(RIM);
        event.registerPipeline(TONEMAP);
    }
}
