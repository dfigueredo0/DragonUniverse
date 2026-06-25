package studio.elysium.dragonuniverse.client.render.vfx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.RandomSource;
import studio.elysium.dragonuniverse.client.particle.DUParticleData;
import studio.elysium.dragonuniverse.client.particle.DUParticleData.Easing;
import studio.elysium.dragonuniverse.client.particle.DUVFXParticle;

/**
 * <p>The sprite set is captured by {@link DUVFXParticle.Provider} when the {@code du_vfx} particle
 * type's provider is registered (client-side). Presets build on {@link DUVFXBuilder}; gameplay
 * code can call these directly, e.g. {@code DUVFX.sparks(level, x, y, z, 12)}.</p>
 */
public final class DUVFX {
    private DUVFX() {}

    private static final RandomSource RNG = RandomSource.create();

    private static SpriteSet sprites;

    public static void setSprites(SpriteSet spriteSet) {
        sprites = spriteSet;
    }

    public static SpriteSet spriteSet() {
        return sprites;
    }

    /** Default config used if the type is ever spawned through the vanilla registry path. */
    public static DUParticleData defaultSpark() {
        return DUVFXBuilder.create()
                .additive()
                .colorFade(1f, 0.9f, 0.4f, 1f, 0.3f, 0f)
                .scale(0.15f, 0f).alpha(1f, 0f)
                .lifetime(16).gravity(0.02f).friction(0.9f)
                .easing(Easing.LINEAR, Easing.OUT_QUAD, Easing.IN_QUAD)
                .build();
    }

    /** Hot, fast, gravity-pulled additive sparks. */
    public static void sparks(ClientLevel level, double x, double y, double z, int count) {
        DUVFXBuilder.create()
                .additive()
                .colorFade(1f, 0.9f, 0.4f, 1f, 0.3f, 0f)
                .scale(0.15f, 0f).alpha(1f, 0f)
                .lifetime(16).gravity(0.04f).friction(0.88f)
                .randomMotion(0.14f).randomOffset(0.05f)
                .easing(Easing.LINEAR, Easing.OUT_QUAD, Easing.IN_QUAD)
                .spawn(level, x, y, z, count);
    }

    /** Heavy translucent purple splat that falls and spreads. */
    public static void ichorSplash(ClientLevel level, double x, double y, double z, int count) {
        DUVFXBuilder.create()
                .translucent()
                .colorFade(0.5f, 0.1f, 0.6f, 0.2f, 0.0f, 0.3f)
                .scale(0.25f, 0.05f).alpha(0.9f, 0f)
                .lifetime(28).gravity(0.06f).friction(0.92f)
                .randomMotion(0.1f).randomOffset(0.1f)
                .easing(Easing.LINEAR, Easing.OUT_QUAD, Easing.OUT_QUAD)
                .spawn(level, x, y, z, count);
    }

    /** Slow, faint, long-lived ambient drift. */
    public static void spaceDust(ClientLevel level, double x, double y, double z, int count) {
        DUVFXBuilder.create()
                .additive()
                .colorFade(0.4f, 0.6f, 1f, 0.2f, 0.3f, 0.8f)
                .scale(0.08f, 0.02f).alpha(0.6f, 0f)
                .lifetime(80).gravity(-0.002f).friction(0.99f)
                .randomMotion(0.01f).randomOffset(0.6f)
                .easing(Easing.LINEAR, Easing.IN_OUT_QUAD, Easing.IN_OUT_QUAD)
                .spawn(level, x, y, z, count);
    }

    /** A single bright additive twinkle that swells then fades — for pocket-stars / ship cores. */
    public static void pocketStarTwinkle(ClientLevel level, double x, double y, double z, int count) {
        DUVFXBuilder.create()
                .additive()
                .colorFade(0.8f, 1f, 1f, 0.4f, 0.7f, 1f)
                .scale(0f, 0.3f).alpha(1f, 0f)
                .lifetime(24).gravity(0f).friction(1f)
                .randomOffset(0.15f)
                .easing(Easing.LINEAR, Easing.OUT_CUBIC, Easing.IN_QUAD)
                .spawn(level, x, y, z, count);
    }

    /**
     * Phase 5B: particles spawned on a sphere shell orbit the center, spiral inward (collecting
     * like an accretion swarm), then implode and burst back outward.
     */
    public static void implodeVortex(ClientLevel level, double cx, double cy, double cz,
                                     int count, float radius) {
        implodeVortex(level, cx, cy, cz, count, radius, 0.25f, 0.25f);
    }

    /** As above, with tunable orbit speed + outward burst speed. */
    public static void implodeVortex(ClientLevel level, double cx, double cy, double cz,
                                     int count, float radius, float angularSpeed, float burstSpeed) {
        if (level == null) {
            return;
        }
        int lifetime = 50;
        int collectTicks = Math.round(lifetime * 0.6f); // ~60% spiraling in, then burst out

        DUVFXBuilder base = DUVFXBuilder.create()
                .additive()
                .colorFade(0.5f, 0.8f, 1f, 0.8f, 0.95f, 1f)
                .scale(0.12f, 0.05f)
                .alpha(0.95f, 0f)
                .lifetime(lifetime).gravity(0f).friction(1f)
                // alpha stays bright through the collect phase, then fades during the burst
                .easing(Easing.LINEAR, Easing.LINEAR, Easing.IN_QUAD)
                .vortex(cx, cy, cz, radius, angularSpeed, burstSpeed, collectTicks);

        for (int i = 0; i < count; i++) {
            // random point on the sphere shell of `radius` around the center
            float u = RNG.nextFloat() * 2.0f - 1.0f;          // cos(theta) in [-1,1]
            float phi = RNG.nextFloat() * (float) (Math.PI * 2.0);
            float s = (float) Math.sqrt(Math.max(0.0f, 1.0f - u * u));
            double px = cx + s * Math.cos(phi) * radius;
            double py = cy + u * radius;
            double pz = cz + s * Math.sin(phi) * radius;
            base.spawn(level, px, py, pz, 1);
        }
    }
}
