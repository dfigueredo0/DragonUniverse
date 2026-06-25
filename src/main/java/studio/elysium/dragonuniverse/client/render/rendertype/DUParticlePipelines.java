package studio.elysium.dragonuniverse.client.render.rendertype;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import studio.elysium.dragonuniverse.DragonUniverse;

/**
 * <p>Mirrors {@code RenderPipelines.TRANSLUCENT_PARTICLE} (built on {@code PARTICLE_SNIPPET} =
 * {@code core/particle} shader + PARTICLE vertex format) but swaps the blend to ADDITIVE. Exposed
 * as a {@link SingleQuadParticle.Layer} keyed to the particle atlas so configured particles can
 * choose additive vs translucent.</p>
 */
@EventBusSubscriber(modid = DragonUniverse.MODID, value = Dist.CLIENT)
public final class DUParticlePipelines {
    private DUParticlePipelines() {}

    public static final RenderPipeline ADDITIVE_PARTICLE = RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(DragonUniverse.MODID, "pipeline/du_additive_particle"))
            .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
            .build();

    /** Additive glow layer on the particle atlas. Use for sparks, twinkles, energy dust. */
    public static final SingleQuadParticle.Layer ADDITIVE =
            new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, ADDITIVE_PARTICLE);

    /** Standard translucent layer (vanilla pipeline) for soft, non-glowing VFX. */
    public static final SingleQuadParticle.Layer TRANSLUCENT = SingleQuadParticle.Layer.TRANSLUCENT;

    @SubscribeEvent
    public static void onRegisterPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(ADDITIVE_PARTICLE);
    }
}
