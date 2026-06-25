package studio.elysium.dragonuniverse.client.render.vfx.beam;

import net.minecraft.util.Mth;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class SineBeam implements BeamPath {
    private final float amplitude;
    private final float frequency;
    private final float animSpeed;

    public SineBeam(float amplitude, float frequency, float animSpeed) {
        this.amplitude = amplitude;
        this.frequency = frequency;
        this.animSpeed = animSpeed;
    }

    @Override
    public List<Vector3f> points(Vector3f from, Vector3f to, float time, int segments) {
        Vector3f u = new Vector3f();
        Vector3f v = new Vector3f();
        BeamPath.perpBasis(from, to, u, v);

        List<Vector3f> pts = new ArrayList<>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            float offset = Mth.sin(t * frequency * (float) (Math.PI * 2.0) + time * animSpeed) * amplitude;
            Vector3f p = new Vector3f(from).lerp(to, t);
            p.add(u.x * offset, u.y * offset, u.z * offset);
            pts.add(p);
        }
        return pts;
    }
}
