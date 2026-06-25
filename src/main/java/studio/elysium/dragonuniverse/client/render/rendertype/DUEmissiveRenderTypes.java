package studio.elysium.dragonuniverse.client.render.rendertype;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import studio.elysium.dragonuniverse.DragonUniverse;

/**
 * <p>Two layers are provided:</p>
 * <ul>
 *   <li>{@link #GLOW_LAYER} — the cheap "unlit emissive" overlay. Reuses the Phase-1 GLOW
 *       pattern (POSITION_TEX_COLOR + {@code core/du_glow} + additive blend). No custom shader
 *       beyond the one we already ship; meant to be drawn over a base model as a halo.</li>
 *   <li>{@link #EMISSIVE} — the "true emissive" path. Built from vanilla's
 *       {@code ENTITY_EMISSIVE_SNIPPET} and mirroring {@code RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE}
 *       (the stock {@code core/entity} shader with the {@code EMISSIVE} define, which skips the
 *       lightmap multiply). Samples an emissive map via Sampler0 and blends translucent.</li>
 * </ul>
 *
 * <p>Both pipelines are registered through {@link RegisterRenderPipelinesEvent} exactly like
 * {@link DURenderTypes#GLOW_PIPELINE}.</p>
 */
@EventBusSubscriber(modid = DragonUniverse.MODID, value = Dist.CLIENT)
public final class DUEmissiveRenderTypes {
    private DUEmissiveRenderTypes() {}

    public static final Identifier GLOW_LAYER_LOCATION =
            Identifier.fromNamespaceAndPath(DragonUniverse.MODID, "pipeline/du_glow_layer");
    public static final Identifier EMISSIVE_LOCATION =
            Identifier.fromNamespaceAndPath(DragonUniverse.MODID, "pipeline/du_emissive");

    /** Texture sampled by both layers. Reuses the Phase-1 glow sprite as a stand-in emissive map. */
    public static final Identifier DEMO_TEX =
            Identifier.fromNamespaceAndPath(DragonUniverse.MODID, "textures/vfx/glow.png");

    // --- Cheap additive glow overlay (model halo). Mirrors the GLOW pipeline config. ---
    public static final RenderPipeline GLOW_LAYER_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(GLOW_LAYER_LOCATION)
            .withVertexShader(Identifier.fromNamespaceAndPath(DragonUniverse.MODID, "core/du_glow"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(DragonUniverse.MODID, "core/du_glow"))
            .withSampler("Sampler0")
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
            .withCull(false)
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .build();

    // --- True emissive. Mirror of RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE with a DU location.
    // ENTITY_EMISSIVE_SNIPPET supplies core/entity + DefaultVertexFormat.ENTITY + the EMISSIVE
    // define + Sampler0; we add the translucent-specific blend/depth and the overlay sampler.
    public static final RenderPipeline EMISSIVE_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation(EMISSIVE_LOCATION)
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("PER_FACE_LIGHTING")
            .withSampler("Sampler1")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .build();

    public static final RenderType GLOW_LAYER = RenderType.create(
            "dragonuniverse:glow_layer",
            RenderSetup.builder(GLOW_LAYER_PIPELINE)
                    .withTexture("Sampler0", DEMO_TEX)
                    .bufferSize(RenderType.SMALL_BUFFER_SIZE)
                    .sortOnUpload()
                    .createRenderSetup()
    );

    public static final RenderType EMISSIVE = RenderType.create(
            "dragonuniverse:emissive",
            RenderSetup.builder(EMISSIVE_PIPELINE)
                    .withTexture("Sampler0", DEMO_TEX)
                    .useOverlay() // binds Sampler1 (overlay), matching vanilla ENTITY_TRANSLUCENT_EMISSIVE
                    .bufferSize(RenderType.SMALL_BUFFER_SIZE)
                    .sortOnUpload()
                    .createRenderSetup()
    );

    @SubscribeEvent
    public static void onRegisterPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(GLOW_LAYER_PIPELINE);
        event.registerPipeline(EMISSIVE_PIPELINE);
    }
}
