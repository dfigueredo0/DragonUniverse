package studio.elysium.dragonuniverse.client.render.core;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * Per-frame std140 UBO for the look stack. Sibling to {@link DUFrameData}; kept
 * separate so the cinematic-look params (exposure, tonemap operator, bloom, god-ray,
 * and grading controls) evolve independently of the scene-fog UBO.
 *
 * <p>Layout (one vec4, 16 bytes): {@code uTonemap = (exposure, operator, bloomIntensity,
 * bloomThreshold)} where {@code operator} is 0 = Reinhard, 1 = ACES.</p>
 */
public final class DULookData {
    public static final int STD140_SIZE = 16;

    /** Tonemap operators (must match the branch in du_tonemap.fsh). */
    public static final int OP_REINHARD = 0;
    public static final int OP_ACES = 1;

    public static float exposure = 1.0F;
    public static int operator = OP_ACES;

    public static float bloomThreshold = 1.0F;
    public static float bloomIntensity = 0.6F;
    public static int bloomRadius = 3;

    private static GpuBuffer buffer;
    private static ByteBuffer cpu;

    private DULookData() {}

    private static void ensure() {
        if (buffer != null) {
            return;
        }
        cpu = MemoryUtil.memAlloc(STD140_SIZE);
        buffer = RenderSystem.getDevice().createBuffer(
                () -> "DragonUniverse DULook UBO",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                STD140_SIZE
        );
    }

    public static void upload() {
        ensure();
        cpu.clear();
        cpu.putFloat(exposure);
        cpu.putFloat((float) operator);
        cpu.putFloat(bloomIntensity);
        cpu.putFloat(bloomThreshold);
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
