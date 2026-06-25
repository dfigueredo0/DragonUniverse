package studio.elysium.dragonuniverse.client.render.vfx.beam;

import org.joml.Vector3f;

public final class BeamInstance {
    private final Vector3f from;
    private final Vector3f to;
    private final BeamPath path;
    private final BeamConfig cfg;
    private final long spawnTime;

    public BeamInstance(Vector3f from, Vector3f to, BeamPath path, BeamConfig cfg, long spawnTime) {
        this.from = new Vector3f(from);
        this.to = new Vector3f(to);
        this.path = path;
        this.cfg = cfg;
        this.spawnTime = spawnTime;
    }

    public Vector3f from() { return from; }
    public Vector3f to() { return to; }
    public BeamPath path() { return path; }
    public BeamConfig config() { return cfg; }

    public float age(long now, float partialTick) {
        return (now - spawnTime) + partialTick;
    }

    public boolean expired(long now) {
        return (now - spawnTime) >= cfg.lifetime;
    }
}
