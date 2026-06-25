package studio.elysium.dragonuniverse.client.render.vfx.beam;

import org.joml.Vector3f;

import java.util.List;

@FunctionalInterface
public interface BeamPath {
    List<Vector3f> points(Vector3f from, Vector3f to, float time, int segments);

    static void perpBasis(Vector3f from, Vector3f to, Vector3f outU, Vector3f outV) {
        Vector3f axis = new Vector3f(to).sub(from);
        if (axis.lengthSquared() < 1.0e-8f) {
            axis.set(0.0f, 1.0f, 0.0f);
        }
        axis.normalize();
        Vector3f ref = Math.abs(axis.y) < 0.99f
                ? new Vector3f(0.0f, 1.0f, 0.0f)
                : new Vector3f(1.0f, 0.0f, 0.0f);
        outU.set(axis).cross(ref).normalize();
        outV.set(axis).cross(outU).normalize();
    }
}
