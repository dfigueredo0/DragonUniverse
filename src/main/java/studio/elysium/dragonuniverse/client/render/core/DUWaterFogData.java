package studio.elysium.dragonuniverse.client.render.core;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * Tiny std140 UBO for the underwater scene-fog pass ({@code du_underwater}): one {@code vec4}
 * = {@code (color.rgb, density)}. Sibling to {@link DUSsaoData}; the pass also reads {@link DUViewData}
 * for the inverse projection (to linearise depth into a view-space distance).
 */
public final class DUWaterFogData {
    public static final int STD140_SIZE = 16; // one vec4

    private static GpuBuffer buffer;
    private static ByteBuffer cpu;

    private DUWaterFogData() {}

    private static void ensure() {
        if (buffer != null) {
            return;
        }
        cpu = MemoryUtil.memAlloc(STD140_SIZE);
        buffer = RenderSystem.getDevice().createBuffer(
                () -> "DragonUniverse DUWaterFog UBO",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                STD140_SIZE
        );
    }

    public static void upload() {
        ensure();
        cpu.clear();
        cpu.putFloat(DUWaterData.underwaterFogColor.x)
                .putFloat(DUWaterData.underwaterFogColor.y)
                .putFloat(DUWaterData.underwaterFogColor.z)
                .putFloat(DUWaterData.underwaterFogDensity);
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
