package studio.elysium.dragonuniverse.client.render.core;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * std140 UBO for SSAO (screen-space ambient occlusion). Sibling to {@link DULookData}/{@link DUViewData}
 * /{@link DUGodrayData}; consumed by the {@code du_ssao} pass, which also reads {@link DUViewData} for
 * the camera matrices (view-space reconstruction + sample reprojection).
 *
 * <p>Layout (three vec4, 48 bytes):</p>
 * <ol start="0">
 *   <li>{@code (radius, bias, intensity, power)} — hemisphere radius in view-space units (blocks),
 *       depth-compare bias, occlusion strength, and a contrast exponent on the final AO.</li>
 *   <li>{@code (sampleCount, enabled, pad, pad)}.</li>
 *   <li>{@code (hiZEnabled, hiZLodBias, pad, pad)} — Hi-Z distance-persistence flag + LOD tuning bias.</li>
 * </ol>
 */
public final class DUSsaoData {
    public static final int STD140_SIZE = 48; // three vec4

    public static boolean ssaoEnabled = true;
    public static float radius = 1.0F;        // view-space units (blocks)
    public static float bias = 0.025F;
    public static float intensity = 1.0F;
    public static float power = 2.0F;
    public static int sampleCount = 16;

    // Hi-Z distance persistence (off by default — opt in via the debug panel). When on, DUPostChain builds
    // the min-depth pyramid and SSAO samples a footprint-matched mip so distant contact shadows persist
    // instead of fading. hiZLodBias nudges the chosen mip (+ coarser/more persistent, - finer).
    public static boolean hiZEnabled = false;
    public static float hiZLodBias = 0.0F;

    private static GpuBuffer buffer;
    private static ByteBuffer cpu;

    private DUSsaoData() {}

    private static void ensure() {
        if (buffer != null) {
            return;
        }
        cpu = MemoryUtil.memAlloc(STD140_SIZE);
        buffer = RenderSystem.getDevice().createBuffer(
                () -> "DragonUniverse DUSsao UBO",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                STD140_SIZE
        );
    }

    public static void upload() {
        ensure();
        cpu.clear();
        cpu.putFloat(radius).putFloat(bias).putFloat(intensity).putFloat(power);
        cpu.putFloat((float) sampleCount).putFloat(ssaoEnabled ? 1.0F : 0.0F).putFloat(0F).putFloat(0F);
        cpu.putFloat(hiZEnabled ? 1.0F : 0.0F).putFloat(hiZLodBias).putFloat(0F).putFloat(0F);
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
