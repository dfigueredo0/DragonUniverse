package studio.elysium.dragonuniverse.client.render.vfx.beam;

import net.minecraft.util.Mth;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * A time-animated wobble: pseudo-noise (summed out-of-phase sines) displaces the line
 * in both perpendicular axes, so the beam writhes like an unstable arc. Endpoints stay anchored.
 */
public final class WobbleBeam implements BeamPath {
    private final float amplitude;
    private final float frequency;

    public WobbleBeam(float amplitude, float frequency) {
        this.amplitude = amplitude;
        this.frequency = frequency;
    }

    @Override
    public List<Vector3f> points(Vector3f from, Vector3f to, float time, int segments) {
        Vector3f u = new Vector3f();
        Vector3f v = new Vector3f();
        BeamPath.perpBasis(from, to, u, v);

        List<Vector3f> pts = new ArrayList<>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            // Anchor the endpoints (envelope 0 at t=0/1, 1 in the middle).
            float envelope = Mth.sin(t * (float) Math.PI);
            float phase = t * frequency * (float) (Math.PI * 2.0);
            float nu = (Mth.sin(phase + time * 0.21f) + 0.5f * Mth.sin(phase * 2.3f + time * 0.13f)) * amplitude * envelope;
            float nv = (Mth.cos(phase * 1.7f + time * 0.17f) + 0.5f * Mth.sin(phase * 0.7f + time * 0.23f)) * amplitude * envelope;
            Vector3f p = new Vector3f(from).lerp(to, t);
            p.add(u.x * nu + v.x * nv, u.y * nu + v.y * nv, u.z * nu + v.z * nv);
            pts.add(p);
        }
        return pts;
    }
}
