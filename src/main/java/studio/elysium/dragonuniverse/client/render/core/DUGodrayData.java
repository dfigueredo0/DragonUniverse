package studio.elysium.dragonuniverse.client.render.core;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * std140 UBO for god rays (screen-space radial light scattering) and the optional rim light.
 * Sibling to {@link DULookData}/{@link DUViewData}; consumed by the godray blur/composite passes and
 * the rim pass.
 *
 * <p>Layout (eight vec4, 128 bytes):</p>
 * <ol start="0">
 *   <li>{@code uSun     = (sunU, sunV, sunVisible, intensity)} — sun screen position (computed per
 *       frame by projecting {@link DUFrameData#sunDir}), an in-front flag, and the overall ray gain.</li>
 *   <li>{@code uParams  = (density, decay, weight, sampleCount)} — the classic radial-blur knobs.</li>
 *   <li>{@code uSunColor= (r, g, b, godrayEnabled)}.</li>
 *   <li>{@code uRim     = (r, g, b, rimStrength)}.</li>
 *   <li>{@code uRim2    = (rimPower, rimEnabled, pad, pad)}.</li>
 *   <li>{@code uSunDir  = (x, y, z, pad)} — world-space sun direction (for the sun-driven rim; this
 *       is view-independent, so the rim persists regardless of where the camera looks).</li>
 *   <li>{@code uSilver  = (r, g, b, silverStrength)} — silver-lining (cloud-edge) glow colour & gain.</li>
 *   <li>{@code uSilver2 = (silverPower, scatterPower, silverEnabled, pad)} — edge sharpness, forward-
 *       scatter sharpness (how tightly the glow tracks looking toward the sun), and enable flag.</li>
 * </ol>
 * <p>The godray blur/composite shaders declare only the first members they use (a std140 prefix), so
 * appending {@code uSilver}/{@code uSilver2} at the end only affects {@code du_rim.fsh}.</p>
 */
public final class DUGodrayData {
    public static final int STD140_SIZE = 128; // eight vec4

    // God rays.
    public static boolean godrayEnabled = true;
    public static float density = 1.0F;
    public static float decay = 0.96F;
    public static float weight = 0.5F;
    public static float intensity = 0.6F;
    public static int sampleCount = 48;
    public static final Vector3f sunColor = new Vector3f(1.0F, 0.95F, 0.85F);

    // Rim light (off by default — opt in via the debug panel).
    public static boolean rimEnabled = false;
    public static float rimStrength = 0.0F;
    public static float rimPower = 3.0F;
    public static final Vector3f rimColor = new Vector3f(0.5F, 0.7F, 1.0F);

    // Silver-lining - Distinct from the front-lit rim: it lights cloud
    // silhouette edges when looking toward the sun, regardless of edge-normal sun-facing. Off by default.
    public static boolean silverEnabled = false;
    public static float silverStrength = 1.5F;
    public static float silverPower = 3.0F;       // grazing-edge sharpness
    public static float silverScatterPower = 6.0F; // how tightly the glow tracks looking toward the sun
    public static final Vector3f silverColor = new Vector3f(1.0F, 0.98F, 0.92F);

    // Per-frame, set by DUPostChain: world-space sun direction (for the sun-driven rim light).
    private static float sunDirX = 0.0F;
    private static float sunDirY = 1.0F;
    private static float sunDirZ = 0.0F;

    // Per-frame, set by DUPostChain from the projected sun direction.
    private static float sunU = 0.5F;
    private static float sunV = 0.5F;
    // Continuous 0..1 visibility gate: 1 while the sun is on-screen, fading to 0 as it moves off-screen,
    // and 0 once it grazes/goes behind the camera (where the projected UV would otherwise blow up).
    private static float sunGate = 0.0F;

    private static GpuBuffer buffer;
    private static ByteBuffer cpu;

    private DUGodrayData() {}

    public static void setSunScreen(float u, float v, float gate) {
        sunU = u;
        sunV = v;
        sunGate = gate;
    }

    public static void setSunWorldDir(float x, float y, float z) {
        sunDirX = x;
        sunDirY = y;
        sunDirZ = z;
    }

    public static float lastSunU() { return sunU; }
    public static float lastSunV() { return sunV; }
    public static float lastSunGate() { return sunGate; }
    public static boolean lastSunVisible() { return sunGate > 0.0F; }

    private static void ensure() {
        if (buffer != null) {
            return;
        }
        cpu = MemoryUtil.memAlloc(STD140_SIZE);
        buffer = RenderSystem.getDevice().createBuffer(
                () -> "DragonUniverse DUGodray UBO",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                STD140_SIZE
        );
    }

    public static void upload() {
        ensure();
        cpu.clear();
        cpu.putFloat(sunU).putFloat(sunV).putFloat(sunGate).putFloat(intensity);
        cpu.putFloat(density).putFloat(decay).putFloat(weight).putFloat((float) sampleCount);
        cpu.putFloat(sunColor.x).putFloat(sunColor.y).putFloat(sunColor.z).putFloat(godrayEnabled ? 1.0F : 0.0F);
        cpu.putFloat(rimColor.x).putFloat(rimColor.y).putFloat(rimColor.z).putFloat(rimStrength);
        cpu.putFloat(rimPower).putFloat(rimEnabled ? 1.0F : 0.0F).putFloat(0F).putFloat(0F);
        cpu.putFloat(sunDirX).putFloat(sunDirY).putFloat(sunDirZ).putFloat(0F);
        cpu.putFloat(silverColor.x).putFloat(silverColor.y).putFloat(silverColor.z).putFloat(silverStrength);
        cpu.putFloat(silverPower).putFloat(silverScatterPower).putFloat(silverEnabled ? 1.0F : 0.0F).putFloat(0F);
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
