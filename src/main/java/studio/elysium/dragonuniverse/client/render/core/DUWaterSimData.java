package studio.elysium.dragonuniverse.client.render.core;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * std140 UBO for the ripple simulation ({@code du_water_sim}).
 *
 * Layout:
 * <ul>
 *   <li>{@code p0  = (reprojX, reprojZ, damping, propagation)} — per-frame reprojection UV shift
 *       ({@code cameraDelta / extent}) keeping the field world-anchored, plus the wave tuning.</li>
 *   <li>{@code meta = (texel, heightDecay, injCount, pad)} — {@code 1/N}, the still-water settle decay,
 *       and how many of the injection slots are active this frame.</li>
 *   <li>{@code inj[MAX] = (uvX, uvY, radiusUV, strength)} — the disturbances to add this frame: the debug
 *       source plus Stage 4's real sources (entity wakes, player charge-ripples).</li>
 * </ul>
 *
 * <p>The dynamic terms are passed to {@link #upload}; the wave tuning (damping/propagation/decay) is read
 * from {@link DUWaterData}.</p>
 */
public final class DUWaterSimData {
    /** Max simultaneous disturbances per frame (debug + wakes + charge). Excess sources are dropped. */
    public static final int MAX_INJECTIONS = 32;
    public static final int STD140_SIZE = 32 + MAX_INJECTIONS * 16; // p0 + meta + inj[MAX]

    private static GpuBuffer buffer;
    private static ByteBuffer cpu;

    private DUWaterSimData() {}

    private static void ensure() {
        if (buffer != null) {
            return;
        }
        cpu = MemoryUtil.memAlloc(STD140_SIZE);
        buffer = RenderSystem.getDevice().createBuffer(
                () -> "DragonUniverse DUWaterSim UBO",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                STD140_SIZE
        );
    }

    /**
     * @param inj   flat {@code [uvX, uvY, radiusUV, strength, ...]} array; {@code count} active entries
     * @param count number of active injections (clamped to {@link #MAX_INJECTIONS})
     */
    public static void upload(float reprojX, float reprojZ, float texel, float[] inj, int count) {
        ensure();
        int n = Math.min(count, MAX_INJECTIONS);
        cpu.clear();
        cpu.putFloat(reprojX).putFloat(reprojZ).putFloat(DUWaterData.rippleDamping).putFloat(DUWaterData.ripplePropagation);
        cpu.putFloat(texel).putFloat(DUWaterData.rippleHeightDecay).putFloat((float) n).putFloat(0F);
        for (int i = 0; i < MAX_INJECTIONS; i++) {
            if (i < n) {
                cpu.putFloat(inj[i * 4]).putFloat(inj[i * 4 + 1]).putFloat(inj[i * 4 + 2]).putFloat(inj[i * 4 + 3]);
            } else {
                cpu.putFloat(0F).putFloat(0F).putFloat(0F).putFloat(0F);
            }
        }
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
