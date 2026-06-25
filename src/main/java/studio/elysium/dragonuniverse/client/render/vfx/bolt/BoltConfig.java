package studio.elysium.dragonuniverse.client.render.vfx.bolt;

/**
 * An immutable snapshot of a bolt's generation + look parameters (like
 * {@code DUParticleData}). Build via {@link #builder()}.
 */
public final class BoltConfig {
    public final int segments;             // midpoint-displacement subdivision passes (2^n segments)
    public final float displacement;       // initial perpendicular offset magnitude
    public final float displacementDecay;  // displacement multiplier per subdivision pass (~0.5)
    public final float forkProbability;    // chance an interior node spawns a fork
    public final float forkAngleSpread;    // max angle (radians) a fork deviates from the tangent
    public final int maxForkDepth;         // recursion cap for forks
    public final float childLengthFactor;  // fork length / displacement relative to remaining
    public final float childWidthFalloff;  // width multiplier per fork generation
    public final float childIntensityFalloff; // alpha multiplier per fork generation
    public final float baseWidth;          // ribbon half-width of the main bolt
    public final float r, g, b;
    public final float headAlpha;
    public final int lifetime;             // ticks before the bolt expires
    public final int maxNodes;             // hard cap to avoid pathological recursion

    private BoltConfig(Builder bd) {
        this.segments = bd.segments;
        this.displacement = bd.displacement;
        this.displacementDecay = bd.displacementDecay;
        this.forkProbability = bd.forkProbability;
        this.forkAngleSpread = bd.forkAngleSpread;
        this.maxForkDepth = bd.maxForkDepth;
        this.childLengthFactor = bd.childLengthFactor;
        this.childWidthFalloff = bd.childWidthFalloff;
        this.childIntensityFalloff = bd.childIntensityFalloff;
        this.baseWidth = bd.baseWidth;
        this.r = bd.r;
        this.g = bd.g;
        this.b = bd.b;
        this.headAlpha = bd.headAlpha;
        this.lifetime = bd.lifetime;
        this.maxNodes = bd.maxNodes;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** A jagged, heavily-branching vertical strike (image-1 look). */
    public static BoltConfig groundStrike() {
        return builder()
                .segments(6).displacement(1.4f).forkProbability(0.22f).maxForkDepth(3)
                .baseWidth(0.10f).color(0.6f, 0.8f, 1.0f).lifetime(8)
                .build();
    }

    /** A tighter point-to-point arc with fewer, shorter forks. */
    public static BoltConfig arc() {
        return builder()
                .segments(5).displacement(0.5f).forkProbability(0.10f).maxForkDepth(2)
                .baseWidth(0.07f).color(0.8f, 0.9f, 1.0f).lifetime(6)
                .build();
    }

    public static final class Builder {
        private int segments = 6;
        private float displacement = 1.2f;
        private float displacementDecay = 0.55f;
        private float forkProbability = 0.18f;
        private float forkAngleSpread = 0.7f;
        private int maxForkDepth = 3;
        private float childLengthFactor = 0.55f;
        private float childWidthFalloff = 0.65f;
        private float childIntensityFalloff = 0.6f;
        private float baseWidth = 0.09f;
        private float r = 0.6f, g = 0.8f, b = 1.0f;
        private float headAlpha = 1.0f;
        private int lifetime = 8;
        private int maxNodes = 1500;

        public Builder segments(int v) { this.segments = v; return this; }
        public Builder displacement(float v) { this.displacement = v; return this; }
        public Builder displacementDecay(float v) { this.displacementDecay = v; return this; }
        public Builder forkProbability(float v) { this.forkProbability = v; return this; }
        public Builder forkAngleSpread(float v) { this.forkAngleSpread = v; return this; }
        public Builder maxForkDepth(int v) { this.maxForkDepth = v; return this; }
        public Builder childLengthFactor(float v) { this.childLengthFactor = v; return this; }
        public Builder childWidthFalloff(float v) { this.childWidthFalloff = v; return this; }
        public Builder childIntensityFalloff(float v) { this.childIntensityFalloff = v; return this; }
        public Builder baseWidth(float v) { this.baseWidth = v; return this; }
        public Builder color(float r, float g, float b) { this.r = r; this.g = g; this.b = b; return this; }
        public Builder headAlpha(float v) { this.headAlpha = v; return this; }
        public Builder lifetime(int v) { this.lifetime = v; return this; }
        public Builder maxNodes(int v) { this.maxNodes = v; return this; }

        public BoltConfig build() {
            return new BoltConfig(this);
        }
    }
}
