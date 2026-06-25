package studio.elysium.dragonuniverse.client.render.vfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.RandomSource;
import studio.elysium.dragonuniverse.client.particle.DUParticleData;
import studio.elysium.dragonuniverse.client.particle.DUParticleData.Easing;
import studio.elysium.dragonuniverse.client.particle.DUVFXParticle;
import studio.elysium.dragonuniverse.client.render.rendertype.DUParticlePipelines;
import org.joml.Vector3f;

/**
 * <pre>{@code
 * DUVFXBuilder.create()
 *     .additive()
 *     .colorFade(1f, 0.9f, 0.4f,  1f, 0.3f, 0f)
 *     .scale(0.15f, 0f).alpha(1f, 0f)
 *     .lifetime(16).gravity(0.02f).friction(0.9f)
 *     .randomMotion(0.12f).randomOffset(0.05f)
 *     .spawn(level, x, y, z, 12);
 * }</pre>
 *
 * <p>Spawns directly through {@link ParticleEngine#add} — no networked {@code ParticleOptions} —
 * so it stays fully client-side and can carry arbitrary curves.</p>
 */
public final class DUVFXBuilder {
    private final Vector3f colorStart = new Vector3f(1f, 1f, 1f);
    private final Vector3f colorEnd = new Vector3f(1f, 1f, 1f);
    private float scaleStart = 0.2f;
    private float scaleEnd = 0.0f;
    private float alphaStart = 1.0f;
    private float alphaEnd = 0.0f;
    private int lifetime = 20;
    private float gravity = 0.0f;
    private float friction = 0.96f;
    private Easing colorEasing = Easing.LINEAR;
    private Easing scaleEasing = Easing.LINEAR;
    private Easing alphaEasing = Easing.LINEAR;
    private SingleQuadParticle.Layer layer = DUParticlePipelines.TRANSLUCENT;
    private boolean fullbright = false;
    private boolean animate = false;

    private final Vector3f baseMotion = new Vector3f();
    private float motionSpread = 0.0f;
    private float offsetSpread = 0.0f;
    private DUParticleData.Vortex vortex = null;

    private final RandomSource random = RandomSource.create();

    private DUVFXBuilder() {}

    public static DUVFXBuilder create() {
        return new DUVFXBuilder();
    }

    public DUVFXBuilder color(float r, float g, float b) {
        colorStart.set(r, g, b);
        colorEnd.set(r, g, b);
        return this;
    }

    public DUVFXBuilder colorFade(float r1, float g1, float b1, float r2, float g2, float b2) {
        colorStart.set(r1, g1, b1);
        colorEnd.set(r2, g2, b2);
        return this;
    }

    public DUVFXBuilder scale(float start, float end) {
        scaleStart = start;
        scaleEnd = end;
        return this;
    }

    public DUVFXBuilder alpha(float start, float end) {
        alphaStart = start;
        alphaEnd = end;
        return this;
    }

    public DUVFXBuilder lifetime(int ticks) {
        lifetime = ticks;
        return this;
    }

    public DUVFXBuilder gravity(float g) {
        gravity = g;
        return this;
    }

    public DUVFXBuilder friction(float f) {
        friction = f;
        return this;
    }

    public DUVFXBuilder easing(Easing color, Easing scale, Easing alpha) {
        colorEasing = color;
        scaleEasing = scale;
        alphaEasing = alpha;
        return this;
    }

    public DUVFXBuilder additive() {
        layer = DUParticlePipelines.ADDITIVE;
        fullbright = true;
        return this;
    }

    public DUVFXBuilder translucent() {
        layer = DUParticlePipelines.TRANSLUCENT;
        return this;
    }

    public DUVFXBuilder fullbright(boolean value) {
        fullbright = value;
        return this;
    }

    public DUVFXBuilder animate(boolean value) {
        animate = value;
        return this;
    }

    public DUVFXBuilder motion(float x, float y, float z) {
        baseMotion.set(x, y, z);
        return this;
    }

    public DUVFXBuilder randomMotion(float spread) {
        motionSpread = spread;
        return this;
    }

    public DUVFXBuilder randomOffset(float spread) {
        offsetSpread = spread;
        return this;
    }

    /**
     * Orbit particles around a center: spiral inward for {@code collectTicks}, then burst outward.
     *
     * @param radius        spawn-shell radius / starting orbit radius.
     * @param angularSpeed  orbit speed in radians per tick.
     * @param burstSpeed    outward expansion speed (blocks/tick) after the collect phase.
     * @param collectTicks  ticks spent spiraling inward before the implosion burst.
     */
    public DUVFXBuilder vortex(double centerX, double centerY, double centerZ,
                               float radius, float angularSpeed, float burstSpeed, int collectTicks) {
        this.vortex = new DUParticleData.Vortex(
                new Vector3f((float) centerX, (float) centerY, (float) centerZ),
                radius, angularSpeed, burstSpeed, collectTicks);
        return this;
    }

    /** An immutable snapshot of the current config (so reused builders don't mutate live particles). */
    public DUParticleData build() {
        return new DUParticleData(
                new Vector3f(colorStart), new Vector3f(colorEnd),
                scaleStart, scaleEnd, alphaStart, alphaEnd,
                lifetime, gravity, friction,
                colorEasing, scaleEasing, alphaEasing,
                layer, fullbright, animate, vortex);
    }

    public void spawn(ClientLevel level, double x, double y, double z, int count) {
        SpriteSet sprites = DUVFX.spriteSet();
        if (level == null || sprites == null) {
            return; // provider not registered yet, or no level
        }
        DUParticleData data = build();
        ParticleEngine engine = Minecraft.getInstance().particleEngine;
        for (int i = 0; i < count; i++) {
            double ox = x + spread(offsetSpread);
            double oy = y + spread(offsetSpread);
            double oz = z + spread(offsetSpread);
            double mx = baseMotion.x + spread(motionSpread);
            double my = baseMotion.y + spread(motionSpread);
            double mz = baseMotion.z + spread(motionSpread);
            engine.add(new DUVFXParticle(level, ox, oy, oz, mx, my, mz, data, sprites));
        }
    }

    private double spread(float amount) {
        return amount == 0.0f ? 0.0 : (random.nextFloat() * 2.0f - 1.0f) * amount;
    }
}
