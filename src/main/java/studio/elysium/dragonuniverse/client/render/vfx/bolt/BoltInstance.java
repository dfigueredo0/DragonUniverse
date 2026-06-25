package studio.elysium.dragonuniverse.client.render.vfx.bolt;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The generated node tree flattened into polylines (one per branch,
 * root→tip), each tagged with its fork depth for width/intensity taper. Tracks spawn time +
 * lifetime for fade/expiry.
 */
public final class BoltInstance {
    public record Branch(List<Vector3f> points, int depth) {}

    private final List<Branch> branches = new ArrayList<>();
    private final BoltConfig cfg;
    private final long spawnTime;

    public BoltInstance(BoltNode root, BoltConfig cfg, long spawnTime) {
        this.cfg = cfg;
        this.spawnTime = spawnTime;
        flattenSpine(root);
    }

    private void flattenSpine(BoltNode start) {
        List<Vector3f> line = new ArrayList<>();
        line.add(new Vector3f(start.pos));
        BoltNode cur = start;
        while (!cur.children.isEmpty()) {
            for (int k = 1; k < cur.children.size(); k++) {
                flattenFork(cur.pos, cur.children.get(k));
            }
            BoltNode spine = cur.children.get(0);
            line.add(new Vector3f(spine.pos));
            cur = spine;
        }
        branches.add(new Branch(line, start.depth));
    }

    private void flattenFork(Vector3f from, BoltNode forkStart) {
        List<Vector3f> line = new ArrayList<>();
        line.add(new Vector3f(from));
        line.add(new Vector3f(forkStart.pos));
        BoltNode cur = forkStart;
        while (!cur.children.isEmpty()) {
            for (int k = 1; k < cur.children.size(); k++) {
                flattenFork(cur.pos, cur.children.get(k));
            }
            BoltNode spine = cur.children.get(0);
            line.add(new Vector3f(spine.pos));
            cur = spine;
        }
        branches.add(new Branch(line, forkStart.depth));
    }

    public List<Branch> branches() {
        return branches;
    }

    public BoltConfig config() {
        return cfg;
    }

    public float age(long now, float partialTick) {
        return (now - spawnTime) + partialTick;
    }

    public boolean expired(long now) {
        return (now - spawnTime) >= cfg.lifetime;
    }
}
