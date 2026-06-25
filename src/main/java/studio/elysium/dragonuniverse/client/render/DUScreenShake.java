package studio.elysium.dragonuniverse.client.render;

import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import studio.elysium.dragonuniverse.DragonUniverse;

/**
 * <p>A decaying shake applied as a per-frame rotation offset via
 * {@link ViewportEvent.ComputeCameraAngles}. Trigger with {@link #shake(float, int)}; the effect
 * fades out over its duration with a squared falloff. Uses summed sines at distinct frequencies
 * per axis so it reads as jitter rather than a clean wobble.</p>
 */
@EventBusSubscriber(modid = DragonUniverse.MODID, value = Dist.CLIENT)
public final class DUScreenShake {
    private DUScreenShake() {}

    private static float intensity = 0.0f;
    private static int duration = 0;
    private static int maxDuration = 0;
    private static int ticks = 0;

    /**
     * @param amount    peak rotation magnitude in degrees (e.g. 1.5).
     * @param durationTicks how long the shake lasts before fully decaying.
     */
    public static void shake(float amount, int durationTicks) {
        intensity = amount;
        duration = Math.max(1, durationTicks);
        maxDuration = duration;
    }

    public static boolean isShaking() {
        return duration > 0;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (duration > 0) {
            duration--;
        }
        ticks++;
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (duration <= 0 || intensity <= 0.0f) {
            return;
        }
        float pt = (float) event.getPartialTick();
        float phase = ticks + pt;

        // Squared falloff toward the end of the shake.
        float decay = Mth.clamp((duration - 1 + (1.0f - pt)) / maxDuration, 0.0f, 1.0f);
        float amt = intensity * decay * decay;

        float roll = amt * Mth.sin(phase * 1.7f);
        float yaw = amt * 0.6f * Mth.sin(phase * 2.3f + 1.0f);
        float pitch = amt * 0.6f * Mth.sin(phase * 1.9f + 2.0f);

        event.setRoll(event.getRoll() + roll);
        event.setYaw(event.getYaw() + yaw);
        event.setPitch(event.getPitch() + pitch);
    }
}
