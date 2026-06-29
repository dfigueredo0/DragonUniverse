package studio.elysium.dragonuniverse.client.render.core;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * std140 UBO for color-grading pass. Sibling to {@link DULookData}; consumed by
 * the {@code du_color_grade} pass, which runs LAST (after every other look-stack pass) on the composited
 * LDR frame. A creative grade, tonemap-aware in that it operates on the already-tone-mapped [0,1] color.
 *
 * <p>Neutral defaults are an exact identity (contrast/saturation/gain/gamma = 1, lift = 0, temp/tint = 0),
 * so enabling with untouched sliders changes nothing; the pass is skipped entirely when disabled.</p>
 *
 * <p>Layout (seven vec4, 112 bytes):</p>
 * <ol start="0">
 *   <li>{@code uP0      = (contrast, saturation, temperature, tint)}.</li>
 *   <li>{@code uLift    = (r, g, b, pad)} — additive shadow offset (ASC-CDL "lift").</li>
 *   <li>{@code uGamma   = (r, g, b, pad)} — per-channel midtone gamma.</li>
 *   <li>{@code uGain    = (r, g, b, pad)} — multiplicative highlight scale.</li>
 *   <li>{@code uSky     = (strength, skyEnabled, gradeEnabled, pad)} — sky-tint amount + flag, and the
 *       master static-grade flag (so the two toggle independently within one pass).</li>
 *   <li>{@code uSkyWarm = (r, g, b, pad)} — tint when the view ray faces toward the sun.</li>
 *   <li>{@code uSkyCool = (r, g, b, pad)} — tint when the view ray faces away from the sun.</li>
 * </ol>
 *
 * <p>The sky-tint reads the world-space sun direction from the {@code DUGodray} UBO ({@code uSunDir}) and
 * reconstructs the per-pixel world view ray from {@code DUView} — no data duplicated here. Neutral at a
 * perpendicular view ray, so it only warms/cools as you look toward/away from the sun. FUTURE HOOK: the
 * strength/warm/cool could later come from per-dimension atmosphere params (thick atmosphere = strong
 * warmth, airless = ~none) instead of these flat globals — not built yet.</p>
 */
public final class DUGradeData {
    public static final int STD140_SIZE = 112; // seven vec4

    public static boolean enabled = false;

    public static float contrast = 1.0F;     // around mid-grey 0.5
    public static float saturation = 1.0F;
    public static float temperature = 0.0F;  // -1 cool .. +1 warm
    public static float tint = 0.0F;         // -1 green .. +1 magenta
    public static final Vector3f lift = new Vector3f(0.0F, 0.0F, 0.0F);
    public static final Vector3f gamma = new Vector3f(1.0F, 1.0F, 1.0F);
    public static final Vector3f gain = new Vector3f(1.0F, 1.0F, 1.0F);

    public static boolean skyEnabled = false;
    public static float skyStrength = 0.3F;             // manual mode strength
    public static final Vector3f skyWarm = new Vector3f(1.0F, 0.85F, 0.6F);   // manual warm
    public static final Vector3f skyCool = new Vector3f(0.7F, 0.8F, 1.0F);    // manual cool

    // Per-dimension atmosphere hook: when on, the sky-tint strength/warm/cool come from the current
    // dimension's atmosphere (DUAtmosphereTint sets the auto* values below) instead of the manual ones —
    // thick atmosphere = strong, airless = ~none, scatter colour drives the warm/cool. skyDimScale is a
    // live master multiplier on the auto strength. The auto strength is stored RAW (pre-scale).
    public static boolean skyFromDimension = false;
    public static float skyDimScale = 1.0F;
    public static float autoSkyStrength = 0.3F;
    public static final Vector3f autoSkyWarm = new Vector3f(1.0F, 0.85F, 0.6F);
    public static final Vector3f autoSkyCool = new Vector3f(0.7F, 0.8F, 1.0F);

    private static GpuBuffer buffer;
    private static ByteBuffer cpu;

    private DUGradeData() {}

    private static void ensure() {
        if (buffer != null) {
            return;
        }
        cpu = MemoryUtil.memAlloc(STD140_SIZE);
        buffer = RenderSystem.getDevice().createBuffer(
                () -> "DragonUniverse DUGrade UBO",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                STD140_SIZE
        );
    }

    public static void upload() {
        ensure();
        cpu.clear();
        cpu.putFloat(contrast).putFloat(saturation).putFloat(temperature).putFloat(tint);
        cpu.putFloat(lift.x).putFloat(lift.y).putFloat(lift.z).putFloat(0F);
        cpu.putFloat(gamma.x).putFloat(gamma.y).putFloat(gamma.z).putFloat(0F);
        cpu.putFloat(gain.x).putFloat(gain.y).putFloat(gain.z).putFloat(0F);
        // Effective sky-tint: per-dimension auto values (scaled) when driven from the dimension, else manual.
        float sStrength = skyFromDimension ? autoSkyStrength * skyDimScale : skyStrength;
        Vector3f sWarm = skyFromDimension ? autoSkyWarm : skyWarm;
        Vector3f sCool = skyFromDimension ? autoSkyCool : skyCool;
        cpu.putFloat(sStrength).putFloat(skyEnabled ? 1.0F : 0.0F).putFloat(enabled ? 1.0F : 0.0F).putFloat(0F);
        cpu.putFloat(sWarm.x).putFloat(sWarm.y).putFloat(sWarm.z).putFloat(0F);
        cpu.putFloat(sCool.x).putFloat(sCool.y).putFloat(sCool.z).putFloat(0F);
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
