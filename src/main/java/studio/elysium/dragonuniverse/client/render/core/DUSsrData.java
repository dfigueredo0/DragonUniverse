package studio.elysium.dragonuniverse.client.render.core;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * std140 UBO for SSR (screen-space reflections off water). Sibling to {@link DUSsaoData}/{@link DUViewData};
 * consumed by the {@code du_ssr} pass, which also reads {@link DUViewData} (camera matrices for the
 * view-space raymarch + screen reprojection) and {@link DUGodrayData} (world sun direction + sun colour for
 * the sky fallback).
 *
 * <p>Layout (five vec4, 80 bytes):</p>
 * <ol start="0">
 *   <li>{@code (maxSteps, maxDistance, thickness, stride)} — raymarch step cap, max view-space distance
 *       (blocks), depth-difference acceptance window (blocks), and per-step march length (blocks).</li>
 *   <li>{@code (refineSteps, edgeFade, fallbackEnabled, fresnelStrength)} — binary-search refinement
 *       iterations, screen-edge fade width (reflection fades to the sky fallback near the borders),
 *       sky-fallback toggle, and the fresnel blend strength (scales how strongly the reflection
 *       replaces the water surface colour; grazing reflects more, top-down less).</li>
 *   <li>{@code uSky0 = (zenithR, zenithG, zenithB, sunStrength)} — fallback zenith colour + sun-glow gain.</li>
 *   <li>{@code uSky1 = (horizonR, horizonG, horizonB, sunSharpness)} — fallback horizon colour + sun-glow
 *       exponent (tightness of the reflected sun disc).</li>
 *   <li>{@code uP2 = (roughness, temporalEnabled, feedback, pad)} — roughness tap-blur amount
 *       (0 = mirror; a few jittered taps soften the on-screen reflection so wave normals read as
 *       broken-up, not glassy), and temporal accumulation: the enable flag + the per-frame
 *       <i>effective</i> history feedback (max feedback scaled by camera stillness, computed CPU-side).</li>
 * </ol> </p>
 */
public final class DUSsrData {
    public static final int STD140_SIZE = 80; // five vec4

    public static boolean enabled = false;

    // Raymarch tunables.
    public static int maxSteps = 64;
    public static float maxDistance = 64.0F;   // view-space units (blocks)
    public static float thickness = 1.0F;      // depth-diff acceptance window (blocks)
    public static float stride = 0.5F;         // per-step march length (blocks)

    // Robustness.
    public static int refineSteps = 5;         // binary-search iterations around the first crossing
    public static float edgeFade = 0.1F;       // screen-edge fade width (0..0.5); reflection -> sky near borders
    public static boolean fallbackEnabled = true;

    // Fresnel blend strength (how strongly the reflection shows over the water tint) + roughness
    // tap-blur amount (0 = mirror-sharp; >0 softens the on-screen reflection a few taps).
    public static float fresnelStrength = 1.0F;
    public static float roughness = 0.0F;

    // Temporal accumulation. No motion-vector G-buffer exists in the stack, so accumulation is
    // gated by camera stillness: a still camera blends strongly with history (averaging out the animated
    // wave shimmer), motion drops feedback toward 0 (avoids ghosting/smearing). maxFeedback is the
    // still-camera blend weight; motionSensitivity controls how fast motion kills accumulation.
    public static boolean temporalEnabled = true;
    public static float maxFeedback = 0.9F;
    public static float motionSensitivity = 30.0F;
    /** Per-frame effective feedback (maxFeedback * stillness), set by DUPostChain before {@link #upload}. */
    public static float runtimeFeedback = 0.0F;

    // Sky fallback colours (for rays that find no on-screen hit). A simple horizon->zenith gradient,
    // plus a reflected sun disc tinted by the look stack's sun colour (DUGodray).
    public static final Vector3f skyZenith = new Vector3f(0.17F, 0.34F, 0.62F);
    public static final Vector3f skyHorizon = new Vector3f(0.62F, 0.72F, 0.86F);
    public static float sunStrength = 3.0F;
    public static float sunSharpness = 400.0F;

    private static GpuBuffer buffer;
    private static ByteBuffer cpu;

    private DUSsrData() {}

    private static void ensure() {
        if (buffer != null) {
            return;
        }
        cpu = MemoryUtil.memAlloc(STD140_SIZE);
        buffer = RenderSystem.getDevice().createBuffer(
                () -> "DragonUniverse DUSsr UBO",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                STD140_SIZE
        );
    }

    public static void upload() {
        ensure();
        cpu.clear();
        cpu.putFloat((float) maxSteps).putFloat(maxDistance).putFloat(thickness).putFloat(stride);
        cpu.putFloat((float) refineSteps).putFloat(edgeFade).putFloat(fallbackEnabled ? 1.0F : 0.0F)
                .putFloat(fresnelStrength);
        cpu.putFloat(skyZenith.x).putFloat(skyZenith.y).putFloat(skyZenith.z).putFloat(sunStrength);
        cpu.putFloat(skyHorizon.x).putFloat(skyHorizon.y).putFloat(skyHorizon.z).putFloat(sunSharpness);
        cpu.putFloat(roughness).putFloat(temporalEnabled ? 1.0F : 0.0F).putFloat(runtimeFeedback).putFloat(0F);
        cpu.flip();
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(0, STD140_SIZE), cpu);
    }

    public static GpuBufferSlice slice() {
        ensure();
        return buffer.slice(0, STD140_SIZE);
    }

    public static void close() {
        if (buffer != null) {
            buffer.close();
            buffer = null;
        }
        if (cpu != null) {
            MemoryUtil.memFree(cpu);
            cpu = null;
        }
    }
}
