package studio.elysium.dragonuniverse.client.particle;

import net.minecraft.client.particle.SingleQuadParticle;
import org.joml.Vector3f;

/**
 * Phase 4 — an immutable snapshot of a particle's data-driven config (Lodestone-style).
 *
 * <p>Each visual property animates from a start to an end value over the particle's lifetime,
 * shaped by an {@link Easing} curve. Produced by {@code DUVFXBuilder} and consumed by
 * {@link DUVFXParticle}.</p>
 */
public final class DUParticleData {
    @FunctionalInterface
    public interface Easing {
        float ease(float t);

        Easing LINEAR = t -> t;
        Easing IN_QUAD = t -> t * t;
        Easing OUT_QUAD = t -> 1.0F - (1.0F - t) * (1.0F - t);
        Easing IN_OUT_QUAD = t -> t < 0.5F ? 2.0F * t * t : 1.0F - (-2.0F * t + 2.0F) * (-2.0F * t + 2.0F) / 2.0F;
        Easing OUT_CUBIC = t -> 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
    }

    /**
     * Phase 5B — optional vortex motion (kinematic orbital model).
     *
     * <p>Each particle orbits {@code center} in its own (randomly oriented) plane at
     * {@code angularSpeed} rad/tick. For the first {@code collectTicks} the orbit radius spirals
     * inward from its spawn distance toward the center (the "collecting" phase); afterward it
     * expands outward at {@code burstSpeed} blocks/tick (the implosion burst). {@code radius} is
     * the spawn-shell radius used by presets.</p>
     */
    public record Vortex(Vector3f center, float radius, float angularSpeed, float burstSpeed, int collectTicks) {}

    public final Vector3f colorStart;
    public final Vector3f colorEnd;
    public final float scaleStart;
    public final float scaleEnd;
    public final float alphaStart;
    public final float alphaEnd;
    public final int lifetime;
    public final float gravity;
    public final float friction;
    public final Easing colorEasing;
    public final Easing scaleEasing;
    public final Easing alphaEasing;
    public final SingleQuadParticle.Layer layer;
    public final boolean fullbright;
    public final boolean animate;
    /** Optional swirl-toward-center behavior; null = no vortex. */
    public final Vortex vortex;

    public DUParticleData(Vector3f colorStart, Vector3f colorEnd,
                          float scaleStart, float scaleEnd,
                          float alphaStart, float alphaEnd,
                          int lifetime, float gravity, float friction,
                          Easing colorEasing, Easing scaleEasing, Easing alphaEasing,
                          SingleQuadParticle.Layer layer, boolean fullbright, boolean animate,
                          Vortex vortex) {
        this.colorStart = colorStart;
        this.colorEnd = colorEnd;
        this.scaleStart = scaleStart;
        this.scaleEnd = scaleEnd;
        this.alphaStart = alphaStart;
        this.alphaEnd = alphaEnd;
        this.lifetime = lifetime;
        this.gravity = gravity;
        this.friction = friction;
        this.colorEasing = colorEasing;
        this.scaleEasing = scaleEasing;
        this.alphaEasing = alphaEasing;
        this.layer = layer;
        this.fullbright = fullbright;
        this.animate = animate;
        this.vortex = vortex;
    }
}
