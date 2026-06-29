package studio.elysium.dragonuniverse.client.render.sky;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * std140 UBOs for the skybox passes.
 *
 * <p>{@code DUSkyView} (per frame) carries the inverse view-projection matrix so each fullscreen
 * layer reconstructs a world-space view ray per pixel (robust across the whole screen — no seam),
 * plus sun direction and time. The layer params live in a second buffer with one 256-aligned slot
 * per layer (so multiple layers in one frame don't stomp each other's binding).</p>
 */
public final class DUSkyData {
    private DUSkyData() {}

    public static final int VIEW_SIZE = 96;       // mat4 (64) + vec4 sunDir (16) + vec4 misc (16)
    public static final int LAYER_SLOT = 256;     // std140 UBO offset alignment
    public static final int MAX_LAYERS = 8;

    private static GpuBuffer viewBuffer;
    private static GpuBuffer layerBuffer;
    private static ByteBuffer viewCpu;
    private static ByteBuffer layerCpu;

    private static void ensure() {
        if (viewBuffer != null) {
            return;
        }
        viewCpu = MemoryUtil.memAlloc(VIEW_SIZE);
        layerCpu = MemoryUtil.memAlloc(LAYER_SLOT * MAX_LAYERS);
        viewBuffer = RenderSystem.getDevice().createBuffer(
                () -> "DragonUniverse DUSkyView UBO",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, VIEW_SIZE);
        layerBuffer = RenderSystem.getDevice().createBuffer(
                () -> "DragonUniverse DUSkyLayer UBO",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, LAYER_SLOT * MAX_LAYERS);
    }

    /**
     * @param invViewProj inverse of (projection * rotation-only modelview); maps NDC to a world
     *                    direction for per-pixel ray reconstruction.
     */
    public static void uploadView(Matrix4f invViewProj, Vector3f sunDir, float time) {
        ensure();

        viewCpu.clear();
        // std140 mat4 = 4 columns of vec4, column-major — matches joml's get() ordering and GLSL.
        invViewProj.get(viewCpu);
        viewCpu.position(64);
        putVec3(viewCpu, sunDir.x, sunDir.y, sunDir.z);
        viewCpu.putFloat(time).putFloat(0f).putFloat(0f).putFloat(0f);
        viewCpu.flip();
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(viewBuffer.slice(0, VIEW_SIZE), viewCpu);
    }

    /** Writes every layer's params into its own aligned slot in one buffer. */
    public static void uploadLayers(List<DUSkyLayer> layers) {
        ensure();
        layerCpu.clear();
        int count = Math.min(layers.size(), MAX_LAYERS);
        for (int i = 0; i < count; i++) {
            DUSkyLayer l = layers.get(i);
            layerCpu.position(i * LAYER_SLOT);
            layerCpu.putFloat(l.r).putFloat(l.g).putFloat(l.b).putFloat(0f);
            layerCpu.putFloat(Mth.clamp(l.opacity, 0f, 1f)).putFloat(l.rotation).putFloat(l.scale).putFloat(l.seed);
        }
        layerCpu.position(0);
        layerCpu.limit(count * LAYER_SLOT);
        RenderSystem.getDevice().createCommandEncoder()
                .writeToBuffer(layerBuffer.slice(0, count * LAYER_SLOT), layerCpu);
        layerCpu.clear();
    }

    public static GpuBufferSlice viewSlice() {
        ensure();
        return viewBuffer.slice(0, VIEW_SIZE);
    }

    public static GpuBufferSlice layerSlice(int index) {
        ensure();
        return layerBuffer.slice(index * LAYER_SLOT, LAYER_SLOT);
    }

    private static void putVec3(ByteBuffer buf, float x, float y, float z) {
        buf.putFloat(x).putFloat(y).putFloat(z).putFloat(0f);
    }

    public static void close() {
        if (viewBuffer != null) { viewBuffer.close(); viewBuffer = null; }
        if (layerBuffer != null) { layerBuffer.close(); layerBuffer = null; }
        if (viewCpu != null) { MemoryUtil.memFree(viewCpu); viewCpu = null; }
        if (layerCpu != null) { MemoryUtil.memFree(layerCpu); layerCpu = null; }
    }
}
