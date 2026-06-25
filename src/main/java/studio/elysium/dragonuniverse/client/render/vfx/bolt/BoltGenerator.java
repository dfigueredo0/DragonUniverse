package studio.elysium.dragonuniverse.client.render.vfx.bolt;

import net.minecraft.util.RandomSource;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Fractal lightning generator: recursive midpoint displacement + L-system forks.
 *
 * <p>The main path subdivides start→end, offsetting each new midpoint perpendicular to its segment
 * by a random amount that halves each pass (classic fractal lightning). Interior nodes fork with
 * {@code forkProbability} into shorter, less-jagged child branches up to {@code maxForkDepth},
 * producing the branching tree. A node counter caps total nodes.</p>
 */
public final class BoltGenerator {
    private BoltGenerator() {}

    public static BoltNode generate(BoltConfig cfg, Vector3f start, Vector3f end, RandomSource rng) {
        return buildBranch(cfg, start, end, cfg.displacement, 0, rng, new int[]{0});
    }

    private static BoltNode buildBranch(BoltConfig cfg, Vector3f start, Vector3f end,
                                        float displacement, int depth, RandomSource rng, int[] counter) {
        // Deeper forks are simpler (fewer subdivisions) — keeps node count bounded and looks natural.
        int passes = Math.max(1, cfg.segments - depth * 2);
        List<Vector3f> pts = midpointDisplace(start, end, displacement, passes, cfg.displacementDecay, rng);

        // Build the spine chain: each node's first child is the next main point.
        List<BoltNode> chain = new ArrayList<>(pts.size());
        for (int i = 0; i < pts.size(); i++) {
            BoltNode n = new BoltNode(pts.get(i), depth);
            chain.add(n);
            if (i > 0) {
                chain.get(i - 1).children.add(n);
            }
            counter[0]++;
        }

        if (depth < cfg.maxForkDepth) {
            for (int i = 1; i < pts.size() - 1 && counter[0] < cfg.maxNodes; i++) {
                if (rng.nextFloat() >= cfg.forkProbability) {
                    continue;
                }
                Vector3f forkStart = pts.get(i);
                Vector3f tangent = new Vector3f(pts.get(i + 1)).sub(pts.get(i - 1));
                Vector3f forkDir = randomizedDirection(tangent, cfg.forkAngleSpread, rng);
                float remaining = new Vector3f(end).sub(forkStart).length();
                float forkLen = Math.max(0.5f, remaining * cfg.childLengthFactor);
                Vector3f forkEnd = new Vector3f(forkStart).add(forkDir.mul(forkLen));

                BoltNode childRoot = buildBranch(cfg, forkStart, forkEnd,
                        displacement * cfg.childLengthFactor, depth + 1, rng, counter);
                for (BoltNode c : childRoot.children) {
                    chain.get(i).children.add(c);
                }
            }
        }

        return chain.getFirst();
    }

    private static List<Vector3f> midpointDisplace(Vector3f start, Vector3f end, float displacement,
                                                   int passes, float decay, RandomSource rng) {
        List<Vector3f> pts = new ArrayList<>();
        pts.add(new Vector3f(start));
        pts.add(new Vector3f(end));

        float disp = displacement;
        for (int p = 0; p < passes; p++) {
            List<Vector3f> next = new ArrayList<>(pts.size() * 2);
            for (int i = 0; i < pts.size() - 1; i++) {
                Vector3f a = pts.get(i);
                Vector3f b = pts.get(i + 1);
                next.add(a);

                Vector3f mid = new Vector3f(a).add(b).mul(0.5f);
                Vector3f dir = new Vector3f(b).sub(a);
                Vector3f perp = randomPerp(dir, rng);
                float off = (rng.nextFloat() * 2.0f - 1.0f) * disp;
                mid.add(perp.mul(off));
                next.add(mid);
            }
            next.add(pts.getLast());
            pts = next;
            disp *= decay;
        }
        return pts;
    }

    private static Vector3f randomizedDirection(Vector3f tangent, float spread, RandomSource rng) {
        Vector3f dir = new Vector3f(tangent);
        if (dir.lengthSquared() < 1.0e-8f) {
            dir.set(0.0f, 1.0f, 0.0f);
        }
        dir.normalize();
        Vector3f perp = randomPerp(dir, rng);
        float angle = rng.nextFloat() * spread;
        return new Vector3f(dir).mul((float) Math.cos(angle))
                .add(perp.mul((float) Math.sin(angle)))
                .normalize();
    }

    /** A unit vector perpendicular to {@code dir}, randomly oriented around it. */
    private static Vector3f randomPerp(Vector3f dir, RandomSource rng) {
        Vector3f ref = new Vector3f(rng.nextFloat() * 2.0f - 1.0f,
                rng.nextFloat() * 2.0f - 1.0f, rng.nextFloat() * 2.0f - 1.0f);
        Vector3f perp = new Vector3f(dir).cross(ref);
        if (perp.lengthSquared() < 1.0e-6f) {
            perp = new Vector3f(dir).cross(new Vector3f(0.0f, 1.0f, 0.0f));
            if (perp.lengthSquared() < 1.0e-6f) {
                return new Vector3f(1.0f, 0.0f, 0.0f);
            }
        }
        return perp.normalize();
    }
}
