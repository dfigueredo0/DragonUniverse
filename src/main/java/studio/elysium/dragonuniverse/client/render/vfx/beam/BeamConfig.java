package studio.elysium.dragonuniverse.client.render.vfx.beam;

public final class BeamConfig {
    public final int segments;
    public final float baseWidth;
    public final float r, g, b;
    public final float headAlpha;
    public final float tailAlpha;
    public final int lifetime; // ticks

    private BeamConfig(Builder bd) {
        this.segments = bd.segments;
        this.baseWidth = bd.baseWidth;
        this.r = bd.r;
        this.g = bd.g;
        this.b = bd.b;
        this.headAlpha = bd.headAlpha;
        this.tailAlpha = bd.tailAlpha;
        this.lifetime = bd.lifetime;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int segments = 48;
        private float baseWidth = 0.12f;
        private float r = 0.5f, g = 0.8f, b = 1.0f;
        private float headAlpha = 1.0f;
        private float tailAlpha = 1.0f;
        private int lifetime = 40;

        public Builder segments(int v) { this.segments = v; return this; }
        public Builder baseWidth(float v) { this.baseWidth = v; return this; }
        public Builder color(float r, float g, float b) { this.r = r; this.g = g; this.b = b; return this; }
        public Builder alpha(float head, float tail) { this.headAlpha = head; this.tailAlpha = tail; return this; }
        public Builder lifetime(int v) { this.lifetime = v; return this; }

        public BeamConfig build() {
            return new BeamConfig(this);
        }
    }
}
