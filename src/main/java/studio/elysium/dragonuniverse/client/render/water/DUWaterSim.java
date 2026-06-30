package studio.elysium.dragonuniverse.client.render.water;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import studio.elysium.dragonuniverse.DragonUniverse;
import studio.elysium.dragonuniverse.client.render.core.DUWaterData;
import studio.elysium.dragonuniverse.client.render.core.DUWaterSimData;
import studio.elysium.dragonuniverse.client.render.post.DUPostPipelines;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Ripple simulation — a persistent height field advanced each frame by a damped wave equation
 * and ping-ponged between two float targets (see {@code du_water_sim.fsh}). The field is a fixed
 * {@link #FIELD_SIZE}² grid covering {@link #EXTENT} world-blocks, <b>centred on the camera</b>: the
 * water material maps a fragment to field UV with {@code d_WorldPos.xz / EXTENT + 0.5} (no absolute-world
 * coords, since Sodium's {@code d_WorldPos} is already camera-relative).
 *
 * <p><b>World-anchoring</b> comes from temporal reprojection, not a fixed grid: each frame the sim shifts
 * its read UV by {@code cameraDelta / EXTENT}, so a ripple holds its world position as the camera moves;
 * texels exposed at the trailing edge reset to flat. Both frames share the same world-per-texel scale, so
 * the reprojected Laplacian is a single pass.</p>
 *
 * <p>Dispatched at {@code AfterSky} — before Sodium's translucent water pass samples the field (correct
 * ordering) and without running a render pass inside Sodium's {@code begin}. A single fragment ping-pong
 * (one color target) sidesteps 26.1's MRT render-pass limitation entirely. Underwater the stage doesn't
 * fire, so the field simply holds. Independent of the look-stack toggle; fail-soft.</p>
 */
@EventBusSubscriber(modid = DragonUniverse.MODID, value = Dist.CLIENT)
public final class DUWaterSim {
    private DUWaterSim() {}

    /** Field resolution (texels per side). */
    public static final int FIELD_SIZE = 256;
    /** World extent the field covers (blocks per side) → {@code EXTENT/FIELD_SIZE} blocks per texel. */
    public static final float EXTENT = 64.0F;

    private static final FloatField bufA = new FloatField("DU ripple A");
    private static final FloatField bufB = new FloatField("DU ripple B");
    private static boolean aIsLatest = false;   // which buffer holds the most recent field
    private static boolean buffersReady = false;

    private static boolean havePrev = false;
    private static double prevCamX, prevCamZ;

    // World-anchored debug source (the isolated-test disturbance).
    private static boolean srcValid = false;
    private static double srcWorldX, srcWorldZ;
    private static float oneShotStrength = 0.0F;
    private static double oneShotX, oneShotZ;

    // Reused per-frame injection scratch (uvX, uvY, radiusUV, strength) × MAX.
    private static final float[] INJ = new float[DUWaterSimData.MAX_INJECTIONS * 4];

    /** True once a sim step has produced a current field this frame (gates the water-material sample). */
    private static boolean available = false;
    private static boolean warnedOnce = false;

    /** Queue a one-shot disturbance at the player's current world position (debug isolated test). */
    public static void dropTestRippleAtPlayer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        oneShotX = mc.player.getX();
        oneShotZ = mc.player.getZ();
        oneShotStrength = DUWaterData.rippleInjectStrength;
    }

    /**
     * Queue a one-shot ripple at a world point — the event-driven entry point for real sources (e.g. the
     * charge mechanic, impacts) to call directly. Consumed on the next sim step; a later call this frame
     * overwrites an unconsumed one.
     */
    public static void injectRipple(double worldX, double worldZ, float strength) {
        oneShotX = worldX;
        oneShotZ = worldZ;
        oneShotStrength = strength;
    }

    /** Map a world point to a field injection slot (camera-relative UV), dropping out-of-field sources. */
    private static int addInjection(float[] inj, int n, double worldX, double worldZ,
                                    float strength, float radiusBlocks, double camX, double camZ) {
        if (n >= DUWaterSimData.MAX_INJECTIONS || strength <= 0.0F) {
            return n;
        }
        float u = (float) ((worldX - camX) / EXTENT) + 0.5F;
        float v = (float) ((worldZ - camZ) / EXTENT) + 0.5F;
        if (u < -0.05F || u > 1.05F || v < -0.05F || v > 1.05F) {
            return n;   // outside the field — no effect, so don't spend a slot
        }
        inj[n * 4] = u;
        inj[n * 4 + 1] = v;
        inj[n * 4 + 2] = radiusBlocks / EXTENT;
        inj[n * 4 + 3] = strength;
        return n + 1;
    }

    /** Forget the captured continuous-source anchor (re-captured at the player on next enable). */
    public static void resetTestSource() {
        srcValid = false;
    }

    /** The current (latest) field view — for the water-material sample and the Target Inspector. */
    public static GpuTextureView fieldView() {
        if (!buffersReady) {
            return null;
        }
        return (aIsLatest ? bufA : bufB).view;
    }

    /** Raw GL id of the current field, for binding onto Sodium's program. 0 when unavailable. */
    public static int fieldGlId() {
        if (!buffersReady || !available) {
            return 0;
        }
        return (aIsLatest ? bufA : bufB).glId;
    }

    public static boolean isAvailable() {
        return available && buffersReady;
    }

    public static void close() {
        bufA.close();
        bufB.close();
        buffersReady = false;
        available = false;
        havePrev = false;
        srcValid = false;
        DUWaterSimData.close();
    }

    @SubscribeEvent
    public static void onAfterSky(RenderLevelStageEvent.AfterSky event) {
        available = false;
        if (!DUWaterData.enabled || !DUWaterData.rippleEnabled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        try {
            ensureBuffers();
            if (!buffersReady) {
                return;
            }
            Vec3 cam = mc.gameRenderer.getMainCamera().position();
            double camX = cam.x;
            double camZ = cam.z;

            float reprojX = 0.0F;
            float reprojZ = 0.0F;
            if (havePrev) {
                reprojX = (float) ((camX - prevCamX) / EXTENT);
                reprojZ = (float) ((camZ - prevCamZ) / EXTENT);
            }

            // Build this frame's disturbance list (world-anchored): debug source + real sources.
            int n = 0;
            float[] inj = INJ;

            // Debug one-shot.
            if (oneShotStrength != 0.0F) {
                n = addInjection(inj, n, oneShotX, oneShotZ, oneShotStrength, DUWaterData.rippleInjectRadius, camX, camZ);
                oneShotStrength = 0.0F;
            }
            // Debug continuous source (a fixed world point).
            if (DUWaterData.rippleTestSource) {
                if (!srcValid && mc.player != null) {
                    srcWorldX = mc.player.getX();
                    srcWorldZ = mc.player.getZ();
                    srcValid = true;
                }
                if (srcValid) {
                    n = addInjection(inj, n, srcWorldX, srcWorldZ, DUWaterData.rippleInjectStrength,
                            DUWaterData.rippleInjectRadius, camX, camZ);
                }
            } else {
                srcValid = false;
            }
            // Entity wakes: moving entities in water disturb the surface at their feet, scaled by
            // horizontal speed (prev-tick → current position). Per-frame injection draws the propagating wake.
            if (DUWaterData.wakesEnabled && mc.level instanceof ClientLevel cl) {
                double half = EXTENT * 0.5;
                for (Entity e : cl.entitiesForRendering()) {
                    if (n >= DUWaterSimData.MAX_INJECTIONS) {
                        break;
                    }
                    if (!e.isInWater()) {
                        continue;
                    }
                    double ex = e.getX();
                    double ez = e.getZ();
                    if (Math.abs(ex - camX) > half || Math.abs(ez - camZ) > half) {
                        continue;
                    }
                    double dx = ex - e.xo;
                    double dz = ez - e.zo;
                    double speed = Math.sqrt(dx * dx + dz * dz);
                    if (speed < DUWaterData.wakeSpeedThreshold) {
                        continue;
                    }
                    float str = (float) Math.min(speed * DUWaterData.wakeStrength, 2.0);
                    n = addInjection(inj, n, ex, ez, str, DUWaterData.wakeRadius, camX, camZ);
                }
            }
            // player charge-ripples: radiate from under the player while "charging" (driven by item
            // use, or forced via the debug toggle — a stand-in until the real charge mechanic calls in).
            if (DUWaterData.chargeEnabled && mc.player != null) {
                boolean charging = DUWaterData.debugCharge || mc.player.isUsingItem();
                if (charging) {
                    n = addInjection(inj, n, mc.player.getX(), mc.player.getZ(),
                            DUWaterData.chargeStrength, DUWaterData.chargeRadius, camX, camZ);
                }
            }

            FloatField read = aIsLatest ? bufA : bufB;
            FloatField write = aIsLatest ? bufB : bufA;

            DUWaterSimData.upload(reprojX, reprojZ, 1.0F / FIELD_SIZE, inj, n);

            CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
            GpuSampler linClamp = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            try (RenderPass pass = enc.createRenderPass(() -> "DU water sim", write.view,
                    OptionalInt.empty(), null, OptionalDouble.empty())) {
                pass.setPipeline(DUPostPipelines.WATER_SIM);
                pass.setUniform("DUWaterSim", DUWaterSimData.slice());
                pass.bindTexture("PrevField", read.view, linClamp);
                pass.draw(0, 3);
            }

            aIsLatest = !aIsLatest;
            prevCamX = camX;
            prevCamZ = camZ;
            havePrev = true;
            available = true;
        } catch (Throwable t) {
            if (!warnedOnce) {
                warnedOnce = true;
                DragonUniverse.LOGGER.warn("[Dragon Universe] Ripple sim step failed; disabling ripples. Water "
                        + "surface and the rest of the stack are unaffected.", t);
            }
            DUWaterData.rippleEnabled = false;
        }
    }

    private static void ensureBuffers() {
        if (buffersReady) {
            return;
        }
        bufA.ensure(FIELD_SIZE, FIELD_SIZE);
        bufB.ensure(FIELD_SIZE, FIELD_SIZE);
        if (bufA.texture == null || bufB.texture == null) {
            return;
        }
        // Start both fields flat.
        CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
        enc.clearColorTexture(bufA.texture, 0);
        enc.clearColorTexture(bufB.texture, 0);
        aIsLatest = false;
        havePrev = false;
        buffersReady = true;
    }

    /**
     * A small {@code RGBA16F} field target (r=height, g=velocity), allocated via the same raw-GL storage
     * reformat as {@code DUHdrTarget} (26.1 has no float color {@link TextureFormat}). LINEAR so both the
     * sim reprojection read and the water-material slope sample interpolate smoothly.
     */
    private static final class FloatField {
        private static final int GL_TEXTURE_2D = 3553;
        private static final int GL_RGBA16F = 0x881A;
        private static final int GL_RGBA = 6408;
        private static final int GL_FLOAT = 5126;
        private static final int GL_LINEAR = 9729;
        private static final int GL_CLAMP_TO_EDGE = 33071;
        private static final int GL_TEXTURE_MIN_FILTER = 10241;
        private static final int GL_TEXTURE_MAG_FILTER = 10240;
        private static final int GL_TEXTURE_WRAP_S = 10242;
        private static final int GL_TEXTURE_WRAP_T = 10243;
        private static final int USAGE = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING
                | GpuTexture.USAGE_RENDER_ATTACHMENT;

        private final String label;
        private GpuTexture texture;
        private GpuTextureView view;
        private int glId = 0;
        private int w = -1;
        private int h = -1;

        FloatField(String label) {
            this.label = label;
        }

        void ensure(int width, int height) {
            if (texture != null && w == width && h == height) {
                return;
            }
            close();
            var device = RenderSystem.getDevice();
            texture = device.createTexture(() -> label, USAGE, TextureFormat.RGBA8, width, height, 1, 1);
            glId = ((GlTexture) texture).glId();
            GlStateManager._bindTexture(glId);
            GlStateManager._texImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, width, height, 0, GL_RGBA, GL_FLOAT, (ByteBuffer) null);
            GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            GlStateManager._bindTexture(0);
            view = device.createTextureView(texture);
            w = width;
            h = height;
        }

        void close() {
            if (view != null) {
                view.close();
                view = null;
            }
            if (texture != null) {
                texture.close();
                texture = null;
            }
            glId = 0;
            w = -1;
            h = -1;
        }
    }
}
