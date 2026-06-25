package studio.elysium.dragonuniverse.client.render.vfx.beam;

import net.minecraft.util.Mth;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class SpiralBeam implements BeamPath {
    private final float radius;
    private final float turns;
    private final float animSpeed;

    public SpiralBeam(float radius, float turns, float animSpeed) {
        this.radius = radius;
        this.turns = turns;
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
            float angle = t * turns * (float) (Math.PI * 2.0) + time * animSpeed;
            float c = Mth.cos(angle) * radius;
            float s = Mth.sin(angle) * radius;
            Vector3f p = new Vector3f(from).lerp(to, t);
            p.add(u.x * c + v.x * s, u.y * c + v.y * s, u.z * c + v.z * s);
            pts.add(p);
        }
        return pts;
    }
}
