package studio.elysium.dragonuniverse.client.render.sky.planet;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import studio.elysium.dragonuniverse.client.render.sky.DUSkyData;
import studio.elysium.dragonuniverse.world.planet.AtmosphereDef;
import studio.elysium.dragonuniverse.world.planet.PlanetDef;
import studio.elysium.dragonuniverse.world.planet.PlanetRegistry;
import studio.elysium.dragonuniverse.world.planet.RingDef;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Composites the orbital ray-marched planets onto the main color target. Not its own event
 * subscriber: it is driven from {@code DUSkyRenderer}'s single {@code AfterSky} composite (one
 * fullscreen pass per planet, sorted far→near) so there is exactly one AfterSky path, sharing the
 * {@code DUSkyView} ray UBO.
 *
 * <p>Each planet is one fullscreen pass that ray-marches a single sphere (camera-relative). One
 * pass per planet, painter-sorted far→near, is the simpler-to-get-correct option vs. an array-UBO
 * loop: translucent atmosphere halos and rings then blend in the right order for free. Tradeoff: up
 * to {@link DUPlanetData#MAX_PLANETS} passes/frame instead of one — negligible for a handful of
 * visible bodies, and it caps automatically.</p>
 */
public final class DUPlanetRenderer {
    private DUPlanetRenderer() {}

    private static boolean enabled = false;

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            DUPlanetData.close();
        }
    }

    /** Equirect surface sampling: wrap horizontally (U), clamp at the poles (V). */
    private static GpuSampler surfaceSampler() {
        return RenderSystem.getSamplerCache().getSampler(
                AddressMode.REPEAT, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, false);
    }

    /** Ring (1D radial strip) sampling: clamp both axes; the shader discards outside [inner,outer]. */
    private static GpuSampler ringSampler() {
        return RenderSystem.getSamplerCache().getSampler(
                AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, false);
    }

    /**
     * Draw all registered planets into {@code colorView}. Assumes {@code DUSkyData.uploadView} has
     * already run this frame (the caller does it for the shared sky/planet view UBO).
     *
     * @param sceneLight per-scene light direction. In a normal dimension this is the real sun dir the
     *                   skybox derives from SUN_ANGLE. In a space dimension the caller should instead
     *                   pass the direction toward the ray-marched star. See the per-planet TODO below.
     */
    public static void composite(CommandEncoder encoder, GpuTextureView colorView, Vec3 cameraPos, Vector3f sceneLight) {
        List<PlanetDef> defs = new ArrayList<>(PlanetRegistry.all());
        if (defs.isEmpty()) {
            return;
        }

        // Prepare camera-relative geometry and sort far->near (painter's order for translucency).
        List<Prepared> prepared = new ArrayList<>(defs.size());
        for (PlanetDef def : defs) {
            prepared.add(prepare(def, cameraPos, sceneLight));
        }
        prepared.sort((a, b) -> Float.compare(b.distSq, a.distSq));
        if (prepared.size() > DUPlanetData.MAX_PLANETS) {
            prepared = prepared.subList(0, DUPlanetData.MAX_PLANETS);
        }

        DUPlanetData.uploadPlanets(prepared);

        Minecraft mc = Minecraft.getInstance();
        GpuSampler surfaceSampler = surfaceSampler();
        GpuSampler ringSampler = ringSampler();

        for (int idx = 0; idx < prepared.size(); idx++) {
            Prepared p = prepared.get(idx);

            GpuTextureView surfaceView = textureView(mc, p.def.texture());
            if (surfaceView == null) {
                continue; // surface map not loaded yet; skip this planet this frame
            }
            // Ring sampler must always be bound (pipeline declares it). When there is no ring, bind
            // the surface map as a harmless stand-in; the shader never samples it (hasRing == 0).
            GpuTextureView ringView = surfaceView;
            if (p.hasRing && p.ringTexture != null) {
                GpuTextureView rv = textureView(mc, p.ringTexture);
                if (rv != null) {
                    ringView = rv;
                }
            }

            GpuTextureView sView = surfaceView;
            GpuTextureView rView = ringView;
            try (RenderPass pass = encoder.createRenderPass(
                    () -> "DU planet", colorView, OptionalInt.empty(), null, OptionalDouble.empty())) {
                pass.setPipeline(DUPlanetPipelines.PLANET);
                pass.setUniform("DUSkyView", DUSkyData.viewSlice());
                pass.setUniform("DUPlanet", DUPlanetData.planetSlice(idx));
                pass.bindTexture("Sampler0", sView, surfaceSampler);
                pass.bindTexture("Sampler1", rView, ringSampler);
                pass.draw(0, 3);
            }
        }
    }

    private static GpuTextureView textureView(Minecraft mc, Identifier id) {
        AbstractTexture tex = mc.getTextureManager().getTexture(id);
        return tex == null ? null : tex.getTextureView();
    }

    private static Prepared prepare(PlanetDef def, Vec3 cameraPos, Vector3f sceneLight) {
        Prepared p = new Prepared();
        p.def = def;

        Vector3f pos = def.position();
        p.cx = pos.x - (float) cameraPos.x;
        p.cy = pos.y - (float) cameraPos.y;
        p.cz = pos.z - (float) cameraPos.z;
        p.radius = def.radius();
        p.distSq = p.cx * p.cx + p.cy * p.cy + p.cz * p.cz;

        // TODO Phase: space-dimension lighting — when this body lives in a space dimension, the light
        // direction must be (star position - planet position) normalized, not the scene sun. Until the
        // ray-marched star exists, use the per-scene sun the caller supplies (correct for normal dims).
        Vector3f l = new Vector3f(sceneLight);
        if (l.lengthSquared() < 1.0e-6f) {
            l.set(0f, 1f, 0f);
        }
        l.normalize();
        p.lx = l.x;
        p.ly = l.y;
        p.lz = l.z;
        p.hasTexture = true;

        AtmosphereDef atmo = def.atmosphere();
        p.atmoSize = atmo.size();
        p.atmoFalloff = atmo.falloff();
        p.atmoDensity = atmo.density();
        p.atmoBrightness = atmo.brightnessFactor();
        p.scatterR = atmo.scatterCoefficients().x;
        p.scatterG = atmo.scatterCoefficients().y;
        p.scatterB = atmo.scatterCoefficients().z;
        p.atmoDensityFactor = atmo.densityFactor();

        Optional<RingDef> ring = def.ring();
        if (ring.isPresent()) {
            RingDef r = ring.get();
            p.hasRing = true;
            p.ringTexture = r.texture();
            p.ringInner = r.innerRadius();
            p.ringOuter = r.outerRadius();
            // Ring plane normal: planet's equatorial plane (up), tilted around the X axis by `tilt`.
            float tilt = (float) Math.toRadians(r.tilt().orElse(0f));
            float ny = (float) Math.cos(tilt);
            float nz = (float) Math.sin(tilt);
            float len = (float) Math.sqrt(ny * ny + nz * nz);
            p.ringNx = 0f;
            p.ringNy = ny / len;
            p.ringNz = nz / len;
        }
        return p;
    }

    /** Flat, mutable carrier of one planet's per-frame params, consumed by {@link DUPlanetData}. */
    public static final class Prepared {
        public PlanetDef def;
        public float distSq;

        public float cx, cy, cz, radius;
        public float lx, ly, lz;
        public boolean hasTexture;

        public float atmoSize, atmoFalloff, atmoDensity, atmoBrightness;
        public float scatterR, scatterG, scatterB, atmoDensityFactor;

        public boolean hasRing;
        public Identifier ringTexture;
        public float ringInner, ringOuter;
        public float ringNx, ringNy = 1f, ringNz;
    }
}
