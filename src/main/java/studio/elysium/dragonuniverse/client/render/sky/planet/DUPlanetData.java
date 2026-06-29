package studio.elysium.dragonuniverse.client.render.sky.planet;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * Per-planet std140 UBO for the ray-marched orbital renderer. Mirrors {@code DUSkyData}'s
 * per-layer buffer: one 256-aligned slot per planet so several planets composited in one frame don't
 * stomp each other's binding.
 *
 * <p>The view ray + time come from {@code DUSkyData}'s shared {@code DUSkyView} UBO (the same
 * inverse-VP reconstruction the skybox uses). This buffer carries only per-planet params. Geometry
 * is authored in <b>camera-relative</b> space: the renderer uploads {@code center = planetPos -
 * cameraPos} so the shader can intersect with ray origin at (0,0,0) and avoid large-coordinate
 * precision loss.</p>
 *
 * <p>Slot layout (std140, 6 vec4 used of the 256-byte slot):</p>
 * <pre>
 *   0  vec4 centerRadius  xyz = planet center (camera-relative), w = radius
 *  16  vec4 light         xyz = light direction (toward star), w = hasTexture (1/0)
 *  32  vec4 atmo0         x = size, y = falloff, z = density, w = brightnessFactor
 *  48  vec4 atmo1         xyz = Rayleigh scatter coefficients, w = densityFactor
 *  64  vec4 ring0         x = hasRing (1/0), y = innerRadius, z = outerRadius, w = unused
 *  80  vec4 ringNormal    xyz = ring plane normal, w = unused
 * </pre>
 */
public final class DUPlanetData {
    private DUPlanetData() {}

    public static final int PLANET_SLOT = 256;   // std140 UBO offset alignment
    public static final int MAX_PLANETS = 8;

    private static GpuBuffer planetBuffer;
    private static ByteBuffer planetCpu;

    private static void ensure() {
        if (planetBuffer != null) {
            return;
        }
        planetCpu = MemoryUtil.memAlloc(PLANET_SLOT * MAX_PLANETS);
        planetBuffer = RenderSystem.getDevice().createBuffer(
                () -> "DragonUniverse DUPlanet UBO",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, PLANET_SLOT * MAX_PLANETS);
    }

    /** Writes every prepared planet's params into its own aligned slot in one buffer. */
    public static void uploadPlanets(List<DUPlanetRenderer.Prepared> planets) {
        ensure();
        planetCpu.clear();
        int count = Math.min(planets.size(), MAX_PLANETS);
        for (int i = 0; i < count; i++) {
            DUPlanetRenderer.Prepared p = planets.get(i);
            planetCpu.position(i * PLANET_SLOT);
            // centerRadius
            planetCpu.putFloat(p.cx).putFloat(p.cy).putFloat(p.cz).putFloat(p.radius);
            // light + hasTexture
            planetCpu.putFloat(p.lx).putFloat(p.ly).putFloat(p.lz).putFloat(p.hasTexture ? 1f : 0f);
            // atmo0
            planetCpu.putFloat(p.atmoSize).putFloat(p.atmoFalloff).putFloat(p.atmoDensity).putFloat(p.atmoBrightness);
            // atmo1
            planetCpu.putFloat(p.scatterR).putFloat(p.scatterG).putFloat(p.scatterB).putFloat(p.atmoDensityFactor);
            // ring0
            planetCpu.putFloat(p.hasRing ? 1f : 0f).putFloat(p.ringInner).putFloat(p.ringOuter).putFloat(0f);
            // ringNormal
            planetCpu.putFloat(p.ringNx).putFloat(p.ringNy).putFloat(p.ringNz).putFloat(0f);
        }
        planetCpu.position(0);
        planetCpu.limit(count * PLANET_SLOT);
        RenderSystem.getDevice().createCommandEncoder()
                .writeToBuffer(planetBuffer.slice(0, count * PLANET_SLOT), planetCpu);
        planetCpu.clear();
    }

    public static GpuBufferSlice planetSlice(int index) {
        ensure();
        return planetBuffer.slice(index * PLANET_SLOT, PLANET_SLOT);
    }

    public static void close() {
        if (planetBuffer != null) { planetBuffer.close(); planetBuffer = null; }
        if (planetCpu != null) { MemoryUtil.memFree(planetCpu); planetCpu = null; }
    }
}
