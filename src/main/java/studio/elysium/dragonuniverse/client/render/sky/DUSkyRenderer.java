package studio.elysium.dragonuniverse.client.render.sky;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import studio.elysium.dragonuniverse.DragonUniverse;
import studio.elysium.dragonuniverse.client.render.compat.DUIrisCompat;
import studio.elysium.dragonuniverse.client.render.sky.planet.DUPlanetRenderer;

import java.util.HashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Composites the active {@link DUSkybox}'s layers onto the main color target at
 * {@code AfterSky} (right after vanilla draws the sky, before terrain). Each layer is a fullscreen
 * pass with no depth write, so terrain/entities still draw over the sky. Reuses the {@code DUPostChain}
 * fullscreen-pass pattern; guarded so it degrades gracefully if the hook is inside an open pass.
 */
@EventBusSubscriber(modid = DragonUniverse.MODID, value = Dist.CLIENT)
public final class DUSkyRenderer {
    private DUSkyRenderer() {}

    private static boolean enabled = false;
    private static boolean warnedOnce = false;
    private static DUSkybox active = DUSkybox.airlessStarfield();

    // Reused each frame so we don't churn a new Matrix4f (and its native-free analogues) per frame.
    private static final Matrix4f INV_VIEW_PROJ = new Matrix4f();
    private static final Vector3f SUN_DIR = new Vector3f();

    /**
     * Dimensions in which the custom skybox is allowed to composite. Explicit + configurable so it
     * never overrides a dimension's sky unintentionally. Defaults to the Overworld for testing;
     * for production, narrow this to your space/planet dimensions.
     */
    private static final Set<Identifier> ALLOWED_DIMENSIONS = new HashSet<>(Set.of(Level.OVERWORLD.identifier()));

    /** Equirect sampling: wrap horizontally (U), clamp at the poles (V). */
    private static GpuSampler equirectSampler() {
        return RenderSystem.getSamplerCache().getSampler(
                AddressMode.REPEAT, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, false);
    }

    /** Cubemap sampling: clamp both axes. */
    private static GpuSampler cubeSampler() {
        return RenderSystem.getSamplerCache().getSampler(
                AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, false);
    }

    public static boolean isDimensionAllowed(Identifier dimension) {
        return ALLOWED_DIMENSIONS.contains(dimension);
    }

    public static void setDimensionAllowed(Identifier dimension, boolean allow) {
        if (allow) {
            ALLOWED_DIMENSIONS.add(dimension);
        } else {
            ALLOWED_DIMENSIONS.remove(dimension);
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            // Deterministically release the UBOs + native ByteBuffers + cubemap textures when the
            // skybox is off; they lazily re-create on the next enabled frame.
            DUSkyData.close();
            DUCubemap.closeAll();
        }
    }

    public static DUSkybox active() {
        return active;
    }

    public static void setActive(DUSkybox skybox) {
        active = skybox;
    }

    @SubscribeEvent
    public static void onAfterSky(RenderLevelStageEvent.AfterSky event) {
        boolean planets = DUPlanetRenderer.isEnabled();
        if (!enabled && !planets) {
            return;
        }
        // When an Iris shaderpack is active it replaces the sky pass; stand down and let it drive.
        if (DUIrisCompat.shaderPackActive()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        // Dimension gate: only composite where explicitly allowed (don't override other skies).
        if (mc.level == null || !ALLOWED_DIMENSIONS.contains(mc.level.dimension().identifier())) {
            return;
        }
        RenderTarget main = mc.getMainRenderTarget();
        GpuTextureView colorView = main.getColorTextureView();
        if (colorView == null) {
            return;
        }

        List<DUSkyLayer> layers = active.layers();

        // Count drawable sky layers; planets are tracked separately so the planet pass can run even
        // when no sky layer will draw (and vice-versa).
        int drawable = 0;
        if (enabled) {
            for (DUSkyLayer l : layers) {
                if (l.enabled && l.opacity > 0.0f) {
                    drawable++;
                }
            }
        }
        // Nothing to draw at all -> skip every per-frame GPU op (uploads, encoder, passes).
        if (drawable == 0 && !planets) {
            return;
        }

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);

        // Real, dimension-aware sun: use the same SUN_ANGLE the vanilla sky renderer uses, and the
        // same rotation math (YP -90 then XP(sunAngle) applied to local up) -> (-sin, cos, 0).
        Camera camera = mc.gameRenderer.getMainCamera();
        float sunAngle = camera.attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, partialTick)
                * (float) (Math.PI / 180.0);
        SUN_DIR.set(-(float) Math.sin(sunAngle), (float) Math.cos(sunAngle), 0f);

        // Robust per-pixel ray reconstruction: invert (projection * rotation-only modelview). The
        // event's modelview is the camera view rotation (no translation) at the sky pass, so the
        // unprojected far point is a pure world-space direction. Projection comes from the level's
        // camera render state (the event has no projection getter). Reuses INV_VIEW_PROJ.
        Matrix4f invViewProj = INV_VIEW_PROJ
                .set(event.getLevelRenderState().cameraRenderState.projectionMatrix)
                .mul(event.getModelViewMatrix())
                .invert();
        float time = mc.level.getGameTime() + partialTick;

        try {
            DUSkyData.uploadView(invViewProj, SUN_DIR, time);

            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

            if (drawable > 0) {
                DUSkyData.uploadLayers(layers);
                for (int i = 0; i < layers.size() && i < DUSkyData.MAX_LAYERS; i++) {
                    DUSkyLayer layer = layers.get(i);
                    if (!layer.enabled || layer.opacity <= 0.0f) {
                        continue;
                    }
                    RenderPipeline pipeline = DUSkyPipelines.forLayer(layer);

                    // Texture/cubemap layers bind their image to Sampler0; skip if not loaded yet.
                    GpuTextureView bindView = null;
                    GpuSampler bindSampler = null;
                    if (layer.type == DUSkyLayer.Type.TEXTURE && layer.texture != null) {
                        AbstractTexture tex = mc.getTextureManager().getTexture(layer.texture);
                        bindView = tex == null ? null : tex.getTextureView();
                        bindSampler = equirectSampler();
                    } else if (layer.type == DUSkyLayer.Type.CUBEMAP && layer.texture != null) {
                        bindView = DUCubemap.getOrLoad(layer.texture);
                        bindSampler = cubeSampler();
                    }
                    boolean sampled = layer.type == DUSkyLayer.Type.TEXTURE || layer.type == DUSkyLayer.Type.CUBEMAP;
                    if (sampled && bindView == null) {
                        continue;
                    }

                    int idx = i;
                    GpuTextureView view = bindView;
                    GpuSampler sampler = bindSampler;
                    try (RenderPass pass = encoder.createRenderPass(
                            () -> "DU sky layer", colorView, OptionalInt.empty(), null, OptionalDouble.empty())) {
                        pass.setPipeline(pipeline);
                        pass.setUniform("DUSkyView", DUSkyData.viewSlice());
                        pass.setUniform("DUSkyLayer", DUSkyData.layerSlice(idx));
                        if (view != null) {
                            pass.bindTexture("Sampler0", view, sampler);
                        }
                        pass.draw(0, 3);
                    }
                }
            }

            // Planets composite after the sky layers (so they sit in front of the starfield) but at
            // the same AfterSky stage, so terrain still overdraws them (correct occlusion for orbital
            // bodies beyond render distance — no depth sampling needed; terrain depth isn't populated
            // at AfterSky anyway). Decision 4 (ratified): keep this. If a future effect needs a planet
            // partially occluded by a NEAR hill while still in front of mid-scene geometry, add a
            // sibling pass at AfterSolidBlocks that samples the now-populated depth buffer (reuse
            // du_fog.fsh's linearizeDepth) — do NOT build it now. The per-planet light source is
            // SUN_DIR here (a normal dimension); a space dimension would pass the star direction.
            if (planets) {
                DUPlanetRenderer.composite(encoder, colorView, camera.position(), SUN_DIR);
            }
        } catch (Exception e) {
            if (!warnedOnce) {
                warnedOnce = true;
                DragonUniverse.LOGGER.warn("[Dragon Universe] Sky/planet composite failed; disabling. "
                        + "The AFTER_SKY hook may need to move.", e);
            }
            enabled = false;
            DUPlanetRenderer.setEnabled(false);
        }
    }
}
