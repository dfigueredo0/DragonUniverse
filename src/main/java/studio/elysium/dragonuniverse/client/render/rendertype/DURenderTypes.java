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
 * <p>A {@link RenderType} wraps a pipeline with
 *  texture/uniform setup and gives you automatic vertex batching through
 *  {@code MultiBufferSource} — so you get the camera transform, depth integration,
 *  and draw submission for free instead of hand-rolling a RenderPass.</p>
 */
@EventBusSubscriber(modid = DragonUniverse.MODID, value = Dist.CLIENT)
public final class DURenderTypes {
    private DURenderTypes() {}

    public static final Identifier GLOW_LOCATION =
            Identifier.fromNamespaceAndPath(DragonUniverse.MODID, "pipeline/du_glow");

    private static final Identifier GLOW_TEX =
            Identifier.fromNamespaceAndPath(DragonUniverse.MODID, "textures/vfx/glow.png");

    public static final RenderPipeline GLOW_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(GLOW_LOCATION)
            .withVertexShader(Identifier.fromNamespaceAndPath(DragonUniverse.MODID, "core/du_glow"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(DragonUniverse.MODID, "core/du_glow"))
            .withSampler("Sampler0") // Sampler0 = the trail/beam texture; bind the frame UBO in the RenderType setup below.
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS) // POSITION_TEX_COLOR: xyz + uv + rgba — enough for a textured, tinted ribbon.
            .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE)) // Additive blend: SRC_ALPHA, ONE  (glow accumulates toward white).
            .withCull(false)
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .build();

    public static final RenderType GLOW = RenderType.create(
            "dragonuniverse:glow",
            RenderSetup.builder(GLOW_PIPELINE)
                    .withTexture("Sampler0", GLOW_TEX)
                    .bufferSize(RenderType.SMALL_BUFFER_SIZE)
                    .sortOnUpload()
                    .createRenderSetup()
    );

    @SubscribeEvent
    public static void onRegisterPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(GLOW_PIPELINE);
    }
}
