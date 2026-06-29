package studio.elysium.dragonuniverse.client.render.core;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * std140 UBO carrying the inverse camera matrices needed to rebuild view/world-space geometry from
 * the depth buffer. Sibling to {@link DULookData} and {@link DUFrameData}; kept separate because
 * only the depth-aware passes (normals, and god rays / rim light) consume it.
 *
 * <p>Layout (three {@code mat4}, column-major, 192 bytes): {@code uInvProj}, {@code uInvView}, then
 * {@code uProj}. {@code uInvProj} maps clip space back to view space; {@code uInvView}'s upper 3×3
 * rotates a view-space normal into world space; {@code uProj} (the forward projection) projects a
 * view-space position back to clip/screen — needed by SSAO to look up a sample's depth. Translation is
 * irrelevant for direction vectors, so the camera-relative modelview is exactly right here.</p>
 *
 * <p>Passes that only need the first two matrices (e.g. the rim light) declare a 2-{@code mat4} block;
 * binding the full 192-byte buffer to a smaller block is valid std140 — the extra tail is ignored.</p>
 */
public final class DUViewData {
    public static final int STD140_SIZE = 192; // three mat4 (64 bytes each)

    private static final Matrix4f invProj = new Matrix4f();
    private static final Matrix4f invView = new Matrix4f();
    private static final Matrix4f proj = new Matrix4f();

    private static GpuBuffer buffer;
    private static ByteBuffer cpu;

    private DUViewData() {}

    public static void set(Matrix4fc projection, Matrix4fc modelView) {
        invProj.set(projection).invert();
        invView.set(modelView).invert();
        proj.set(projection);
    }

    private static void ensure() {
        if (buffer != null) {
            return;
        }
        cpu = MemoryUtil.memAlloc(STD140_SIZE);
        buffer = RenderSystem.getDevice().createBuffer(
                () -> "DragonUniverse DUView UBO",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                STD140_SIZE
        );
    }

    /** Upload the current inverse matrices. Call once per frame before the normals pass. */
    public static void upload() {
        ensure();
        cpu.clear();                 // position=0, limit=capacity=192
        invProj.get(0, cpu);         // absolute write, column-major; does not move position
        invView.get(64, cpu);
        proj.get(128, cpu);
        // clear() already left position=0, limit=192 → the whole 192 bytes are pending.
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
