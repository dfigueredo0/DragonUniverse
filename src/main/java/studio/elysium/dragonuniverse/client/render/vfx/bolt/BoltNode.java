package studio.elysium.dragonuniverse.client.render.vfx.bolt;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>The main path is a chain where each node's <b>first</b> child is the spine continuation;
 * additional children are forks (each the start of a child branch). {@code depth} is the fork
 * generation (0 = main bolt), used to taper width/intensity per branch.</p>
 */
public final class BoltNode {
    public final Vector3f pos;
    public final int depth;
    public final List<BoltNode> children = new ArrayList<>();

    public BoltNode(Vector3f pos, int depth) {
        this.pos = pos;
        this.depth = depth;
    }
}
