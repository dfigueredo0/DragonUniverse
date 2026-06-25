package studio.elysium.dragonuniverse.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import studio.elysium.dragonuniverse.client.render.vfx.DUVFX;

/**
 * Phase 4 — a configurable, data-driven billboard particle.
 *
 * <p>All visuals (color, scale, alpha) animate from start to end over the lifetime, shaped by the
 * {@link DUParticleData} easing curves. Render layer (additive vs translucent) and physics
 * (gravity/friction) come from the same data. Spawn it through {@code DUVFXBuilder}.</p>
 */
public final class DUVFXParticle extends SingleQuadParticle {
    private static final int FULL_BRIGHT = 0x00F000F0;

    private final DUParticleData data;
    private final SpriteSet sprites;

    // Per-particle orbital state (only used when data.vortex != null). The orbit is a circle of
    // the current radius centered on the vortex center, in the plane spanned by (orbU, orbV).
    private final Vector3f orbU = new Vector3f();
    private final Vector3f orbV = new Vector3f();
    private float orbRadius0;
    private float orbAngle;

    public DUVFXParticle(ClientLevel level, double x, double y, double z,
                         double xd, double yd, double zd,
                         DUParticleData data, SpriteSet sprites) {
        // Use the no-speed ctor so the base doesn't apply its own random speed scatter, then set
        // our motion explicitly (the builder already handles randomization).
        super(level, x, y, z, sprites.first());
        this.data = data;
        this.sprites = sprites;

        this.setParticleSpeed(xd, yd, zd);
        this.lifetime = Math.max(1, data.lifetime);
        this.gravity = data.gravity;
        this.friction = data.friction;

        if (data.vortex != null) {
            initOrbit(data.vortex);
        }
        apply(0.0F); // initialize frame-0 visuals
    }

    /** Sets up this particle's orbital plane so it starts at its spawn position. */
    private void initOrbit(DUParticleData.Vortex v) {
        // Kinematic vortex: drive position directly, so kill velocity-based motion.
        this.setParticleSpeed(0.0, 0.0, 0.0);

        Vector3f radial = new Vector3f(
                (float) (this.x - v.center().x),
                (float) (this.y - v.center().y),
                (float) (this.z - v.center().z));
        orbRadius0 = radial.length();
        if (orbRadius0 < 1.0e-3f) {
            orbRadius0 = Math.max(0.1f, v.radius());
            radial.set(0.0f, 1.0f, 0.0f);
        }
        orbU.set(radial).normalize();                 // angle 0 points back to the spawn position
        orbV.set(randomPerp(orbU)).normalize();        // a random perpendicular -> random orbit plane
        orbAngle = 0.0f;
    }

    @Override
    public void tick() {
        super.tick(); // ages + removes at lifetime (velocity is 0 for vortex particles, so no drift)
        if (this.removed) {
            return;
        }
        if (data.vortex != null) {
            updateOrbit(data.vortex);
        }
        float t = Mth.clamp(this.age / (float) this.lifetime, 0.0F, 1.0F);
        apply(t);
        if (data.animate) {
            setSpriteFromAge(sprites);
        }
    }

    /** Advances the orbit angle, spirals the radius in (collect) then out (burst), and sets position. */
    private void updateOrbit(DUParticleData.Vortex v) {
        orbAngle += v.angularSpeed();

        float radius;
        float radiusMin = orbRadius0 * 0.05f;
        if (this.age <= v.collectTicks()) {
            // ease-in collapse toward the center (accelerating spiral)
            float u = v.collectTicks() <= 0 ? 1.0f : this.age / (float) v.collectTicks();
            radius = Mth.lerp(u * u, orbRadius0, radiusMin);
        } else {
            // implosion burst: expand outward
            radius = radiusMin + v.burstSpeed() * (this.age - v.collectTicks());
        }

        float cos = Mth.cos(orbAngle);
        float sin = Mth.sin(orbAngle);
        double px = v.center().x + (orbU.x * cos + orbV.x * sin) * radius;
        double py = v.center().y + (orbU.y * cos + orbV.y * sin) * radius;
        double pz = v.center().z + (orbU.z * cos + orbV.z * sin) * radius;
        this.setPos(px, py, pz);
    }

    private Vector3f randomPerp(Vector3f dir) {
        Vector3f ref = new Vector3f(
                this.random.nextFloat() * 2.0f - 1.0f,
                this.random.nextFloat() * 2.0f - 1.0f,
                this.random.nextFloat() * 2.0f - 1.0f);
        Vector3f perp = new Vector3f(dir).cross(ref);
        if (perp.lengthSquared() < 1.0e-6f) {
            perp = new Vector3f(dir).cross(new Vector3f(0.0f, 1.0f, 0.0f));
            if (perp.lengthSquared() < 1.0e-6f) {
                return new Vector3f(1.0f, 0.0f, 0.0f);
            }
        }
        return perp.normalize();
    }

    private void apply(float t) {
        float ct = data.colorEasing.ease(t);
        setColor(
                Mth.lerp(ct, data.colorStart.x, data.colorEnd.x),
                Mth.lerp(ct, data.colorStart.y, data.colorEnd.y),
                Mth.lerp(ct, data.colorStart.z, data.colorEnd.z));
        setAlpha(Mth.lerp(data.alphaEasing.ease(t), data.alphaStart, data.alphaEnd));
        this.quadSize = Mth.lerp(data.scaleEasing.ease(t), data.scaleStart, data.scaleEnd);
    }

    @Override
    protected Layer getLayer() {
        return data.layer;
    }

    @Override
    protected int getLightCoords(float a) {
        return data.fullbright ? FULL_BRIGHT : super.getLightCoords(a);
    }

    /**
     * Registered so the type yields a {@link SpriteSet} (captured into {@link DUVFX}). If the type
     * is ever spawned through the vanilla registry path, it falls back to a default spark.
     */
    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
            DUVFX.setSprites(sprites);
        }

        @Override
        public @Nullable Particle createParticle(SimpleParticleType options, ClientLevel level,
                                                 double x, double y, double z,
                                                 double xd, double yd, double zd, RandomSource random) {
            return new DUVFXParticle(level, x, y, z, xd, yd, zd, DUVFX.defaultSpark(), sprites);
        }
    }
}
