package studio.elysium.dragonuniverse.client.render.vfx.beam;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class StraightBeam implements BeamPath {
    public static final StraightBeam INSTANCE = new StraightBeam();

    @Override
    public List<Vector3f> points(Vector3f from, Vector3f to, float time, int segments) {
        List<Vector3f> pts = new ArrayList<>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            pts.add(new Vector3f(from).lerp(to, t));
        }
        return pts;
    }
}
