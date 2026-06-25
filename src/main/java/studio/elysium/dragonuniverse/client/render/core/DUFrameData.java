package studio.elysium.dragonuniverse.client.render.core;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public final class DUFrameData {
    // Four std140 vec4s: [time/partialTick] [sunDir/fog] [skyTint/bloom] [near/far/pad/pad].
    public static final int STD140_SIZE = 64;
    private static final float TICKS_PER_DAY = 24000.0f;
    private static final float NEAR_PLANE = 0.05f;

    public static float fogDensity = 0.0F;
    public static float bloomIntensity = 0.0F;
    public static final Vector3f skyTint = new Vector3f(1.0F, 1.0F, 1.0F);
    public static final Vector3f sunDir =  new Vector3f(0.0F, 1.0F, 0.0F);
    public static boolean lockSunDir = false;

    /**
     * CPU-side brightness pulse derived from {@link #bloomIntensity} and game time.
     * The trail VFX path multiplies vertex rgb by this so the "Bloom intensity" slider
     * has a visible effect before the Phase-3 post-processing stack exists.
     */
    public static float currentPulse = 1.0F;

    /**
     * Gate for the std140 UBO GPU upload. The UBO is only consumed by the Phase-3 post
     * pipelines; until those exist it binds to nothing, so we skip the per-frame write.
     * Phase 3 flips this on.
     */
    public static boolean postProcessingEnabled = false;

    private static GpuBuffer buffer;
    private static ByteBuffer cpu;

    private DUFrameData() {}

    private static void ensure() {
        if (buffer != null)
            return;
        cpu =  MemoryUtil.memAlloc(STD140_SIZE);

        buffer = RenderSystem.getDevice().createBuffer(
                () -> "DragonUniverse DUFrame UBO",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                STD140_SIZE
        );
    }

    public static float sunAngleRadians(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        long gameTime = mc.level == null ? 0L : mc.level.getGameTime();
        float t = (gameTime % (long) TICKS_PER_DAY) + partialTick;
        float frac = t / TICKS_PER_DAY;            // 0..1 across the day
        return frac * (float) (Math.PI * 2.0);     // radians
    }

    public static void upload(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        float time = (mc.level == null ? 0 : mc.level.getGameTime()) + partialTick;

        if (!lockSunDir && mc.level != null) {
            // TODO: Replace with Space-sim sun vector
            float angle = sunAngleRadians(partialTick);
            sunDir.set((float) Math.cos(angle), (float) Math.sin(angle), 0.0F).normalize();
        }

        // CPU-side pulse driven by time + bloomIntensity (used by the trail VFX path today).
        currentPulse = 1.0F + bloomIntensity * 0.25F * (float) Math.sin(time * 0.15F);

        // The std140 UBO is only consumed by the Phase-3 post pipelines; until those exist it
        // binds to nothing, so skip the GPU upload to avoid paying the cost every frame.
        if (!postProcessingEnabled) {
            return;
        }

        // Depth-fog needs the real projection planes to linearize the depth buffer. Replicate
        // Camera.depthFar exactly so our reconstruction matches what wrote the depth buffer.
        float near = NEAR_PLANE;
        float far = 1.0F;
        if (mc.level != null) {
            float renderDistance = mc.options.getEffectiveRenderDistance() * 16.0F;
            far = Math.max(renderDistance * 4.0F, mc.options.cloudRange().get() * 16.0F);
        }

        ensure();
        cpu.clear();
        cpu.putFloat(time);
        cpu.putFloat(partialTick);
        cpu.putFloat(0F).putFloat(0F);
        cpu.putFloat(sunDir.x).putFloat(sunDir.y).putFloat(sunDir.z);
        cpu.putFloat(fogDensity);
        cpu.putFloat(skyTint.x).putFloat(skyTint.y).putFloat(skyTint.z);
        cpu.putFloat(bloomIntensity);
        cpu.putFloat(near).putFloat(far).putFloat(0F).putFloat(0F);
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
