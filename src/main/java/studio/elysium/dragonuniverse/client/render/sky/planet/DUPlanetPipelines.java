package studio.elysium.dragonuniverse.client.render.sky.planet;

import com.mojang.blaze3d.pipeline.BlendFunction;
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
 * Fullscreen ray-marched planet pipeline. Same proven recipe as {@code DUSkyPipelines}:
 * the vanilla {@code core/screenquad} fullscreen-triangle vertex shader + a procedural fragment
 * shader, depth disabled (so terrain still overdraws — planets sit behind terrain at AfterSky),
 * standard translucent blend so painter-ordered (far->near) planets and their atmosphere halos
 * composite correctly.
 *
 * <p>Reuses the shared {@code DUSkyView} UBO for the inverse-VP ray + time, plus a per-planet
 * {@code DUPlanet} UBO. Two samplers: {@code Sampler0} = equirectangular surface map,
 * {@code Sampler1} = radial ring strip. Both are always bound by the renderer (the ring sampler is
 * fed the surface texture when a planet has no ring; the shader gates on the {@code hasRing} flag).</p>
 *
 * <p>Note: there is intentionally no custom {@code du_planet.vsh}. The fullscreen triangle is the
 * vanilla {@code core/screenquad} vertex shader, exactly as every {@code DUSkyPipelines} entry does;
 * authoring a duplicate vsh would only risk drift from vanilla's varyings.</p>
 */
@EventBusSubscriber(modid = DragonUniverse.MODID, value = Dist.CLIENT)
public final class DUPlanetPipelines {
    private DUPlanetPipelines() {}

    private static final DepthStencilState NO_DEPTH = new DepthStencilState(CompareOp.ALWAYS_PASS, false);

    public static final RenderPipeline PLANET = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(DragonUniverse.MODID, "pipeline/du_planet"))
            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(DragonUniverse.MODID, "core/du_planet"))
            .withUniform("DUSkyView", UniformType.UNIFORM_BUFFER)
            .withUniform("DUPlanet", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .withSampler("Sampler1")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(NO_DEPTH)
            .build();

    @SubscribeEvent
    public static void onRegisterPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(PLANET);
    }
}
