package studio.elysium.dragonuniverse.client.render.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import studio.elysium.dragonuniverse.client.render.core.DUFrameData;

import java.util.List;

public final class TrailVFXBuilder {
    private TrailVFXBuilder() {}

    /**
     * @param pose       PoseStack from the RenderLevelStageEvent (view transform).
     * @param consumer   VertexConsumer obtained from a MultiBufferSource for DURenderTypes.GLOW.
     * @param points     ordered world-space points (head last). Need >= 2.
     * @param width      ribbon half-width in blocks at the head.
     * @param r,g,b      base color (0..1).
     * @param headAlpha  alpha at the newest point.
     * @param tailAlpha  alpha at the oldest point (taper).
     */
    public static void ribbon(PoseStack pose, VertexConsumer consumer, List<Vector3f> points,
                              float width, float r, float g, float b,
                              float headAlpha, float tailAlpha) {
        if (points.size() < 2) return;

        // Apply the CPU-side bloom pulse so the "Bloom intensity" slider is visibly live
        // before the Phase-3 post path exists. Additive blend, so >1 just brightens.
        float pulse = DUFrameData.currentPulse;
        r *= pulse;
        g *= pulse;
        b *= pulse;

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f camPos = new Vector3f(
                (float) camera.position().x(),
                (float) camera.position().y(),
                (float) camera.position().z()
        );

        camera.forwardVector();
        Vector3f lookVec = new  Vector3f(camera.forwardVector());

        Matrix4f mat = pose.last().pose();
        int n = points.size();

        Vector3f prevSide = new Vector3f();
        for (int i = 0; i < n - 1; ++i) {
            Vector3f a = new Vector3f(points.get(i)).sub(camPos);
            Vector3f bp = new Vector3f(points.get(i + 1)).sub(camPos);
            Vector3f tangent = new Vector3f(bp).sub(a);

            if (tangent.lengthSquared() < 1.0e-8F) continue;
            tangent.normalize();

            Vector3f side = new Vector3f(tangent).cross(lookVec);
            if (side.lengthSquared() < 1.0e-8F) side.set(prevSide); else side.normalize();
            prevSide.set(side);

            float t0 = i / (float) (n - 1);
            float t1 = (i + 1) / (float) (n - 1);
            float w0 = width * lerp(0.2f, 1.0f, t0);
            float w1 = width * lerp(0.2f, 1.0f, t1);
            float a0 = lerp(tailAlpha, headAlpha, t0);
            float a1 = lerp(tailAlpha, headAlpha, t1);

            Vector3f s0 = new Vector3f(side).mul(w0);
            Vector3f s1 = new Vector3f(side).mul(w1);

            vertex(consumer, mat, a.x - s0.x, a.y - s0.y, a.z - s0.z, 0f, t0, r, g, b, a0);
            vertex(consumer, mat, a.x + s0.x, a.y + s0.y, a.z + s0.z, 1f, t0, r, g, b, a0);
            vertex(consumer, mat, bp.x + s1.x, bp.y + s1.y, bp.z + s1.z, 1f, t1, r, g, b, a1);
            vertex(consumer, mat, bp.x - s1.x, bp.y - s1.y, bp.z - s1.z, 0f, t1, r, g, b, a1);
        }
    }

    public static void beam(PoseStack pose, VertexConsumer consumer,
                            Vector3f from, Vector3f to, float width,
                            float r, float g, float b, float alpha) {
        ribbon(pose, consumer, List.of(from, to), width, r, g, b, alpha, alpha);
    }

    /**
     * Builds a true 3D tube (a ring cross-section swept along the point list) into the GLOW type.
     * Unlike {@link #ribbon} this is genuinely volumetric — visible from any angle, including
     * looking straight down the axis — so it's the right choice for beams.
     *
     * @param radius tube radius in blocks (constant along the length).
     * @param sides  ring resolution (>= 3); 6–8 reads as round with additive glow.
     */
    public static void tube(PoseStack pose, VertexConsumer consumer, List<Vector3f> points,
                            float radius, int sides, float r, float g, float b,
                            float headAlpha, float tailAlpha) {
        int n = points.size();
        if (n < 2 || sides < 3) return;

        // NB brightness: the GLOW pipeline renders cull-off, so a tube draws both its near and far
        // walls at every pixel (~2x a single ribbon layer), and as a big solid shape it reads much
        // hotter than a thin bolt at equal alpha. Drive beam brightness down via the caller's alpha
        // (the "Beam intensity" debug slider) rather than a hidden factor here.
        float pulse = DUFrameData.currentPulse;
        r *= pulse;
        g *= pulse;
        b *= pulse;

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f camPos = new Vector3f(
                (float) camera.position().x(),
                (float) camera.position().y(),
                (float) camera.position().z());
        Matrix4f mat = pose.last().pose();

        // Camera-relative points + a per-point frame (two axes perpendicular to the local tangent).
        Vector3f[] cp = new Vector3f[n];
        Vector3f[] us = new Vector3f[n];
        Vector3f[] vs = new Vector3f[n];
        for (int i = 0; i < n; i++) {
            cp[i] = new Vector3f(points.get(i)).sub(camPos);
        }
        for (int i = 0; i < n; i++) {
            Vector3f tangent = new Vector3f();
            if (i == 0) tangent.set(cp[1]).sub(cp[0]);
            else if (i == n - 1) tangent.set(cp[n - 1]).sub(cp[n - 2]);
            else tangent.set(cp[i + 1]).sub(cp[i - 1]);
            if (tangent.lengthSquared() < 1.0e-8F) tangent.set(0f, 1f, 0f);
            tangent.normalize();

            Vector3f ref = Math.abs(tangent.y) < 0.99f ? new Vector3f(0f, 1f, 0f) : new Vector3f(1f, 0f, 0f);
            Vector3f u = new Vector3f(tangent).cross(ref);
            if (u.lengthSquared() < 1.0e-8F) u.set(1f, 0f, 0f); else u.normalize();
            Vector3f v = new Vector3f(tangent).cross(u).normalize();
            us[i] = u;
            vs[i] = v;
        }

        float twoPi = (float) (Math.PI * 2.0);
        for (int i = 0; i < n - 1; i++) {
            float t0 = i / (float) (n - 1);
            float t1 = (i + 1) / (float) (n - 1);
            float a0 = lerp(tailAlpha, headAlpha, t0);
            float a1 = lerp(tailAlpha, headAlpha, t1);

            for (int k = 0; k < sides; k++) {
                float ang0 = (k / (float) sides) * twoPi;
                float ang1 = ((k + 1) / (float) sides) * twoPi;
                float uu0 = k / (float) sides;
                float uu1 = (k + 1) / (float) sides;

                Vector3f a = ringPoint(cp[i], us[i], vs[i], ang0, radius);
                Vector3f bb = ringPoint(cp[i + 1], us[i + 1], vs[i + 1], ang0, radius);
                Vector3f cc = ringPoint(cp[i + 1], us[i + 1], vs[i + 1], ang1, radius);
                Vector3f d = ringPoint(cp[i], us[i], vs[i], ang1, radius);

                // GLOW has cull off, so winding doesn't matter for visibility.
                vertex(consumer, mat, a.x, a.y, a.z, uu0, t0, r, g, b, a0);
                vertex(consumer, mat, bb.x, bb.y, bb.z, uu0, t1, r, g, b, a1);
                vertex(consumer, mat, cc.x, cc.y, cc.z, uu1, t1, r, g, b, a1);
                vertex(consumer, mat, d.x, d.y, d.z, uu1, t0, r, g, b, a0);
            }
        }

        // End caps (glowing discs) so you can't see through the open tube ends.
        cap(consumer, mat, cp[0], us[0], vs[0], radius, sides, r, g, b, tailAlpha);
        cap(consumer, mat, cp[n - 1], us[n - 1], vs[n - 1], radius, sides, r, g, b, headAlpha);
    }

    /** A radial disc fan filling a tube end. Each wedge is emitted as a degenerate quad (QUADS mode). */
    private static void cap(VertexConsumer c, Matrix4f m, Vector3f center, Vector3f u, Vector3f v,
                            float radius, int sides, float r, float g, float b, float alpha) {
        float twoPi = (float) (Math.PI * 2.0);
        for (int k = 0; k < sides; k++) {
            float ang0 = (k / (float) sides) * twoPi;
            float ang1 = ((k + 1) / (float) sides) * twoPi;
            Vector3f p0 = ringPoint(center, u, v, ang0, radius);
            Vector3f p1 = ringPoint(center, u, v, ang1, radius);
            float u0 = (float) Math.cos(ang0) * 0.5f + 0.5f;
            float v0 = (float) Math.sin(ang0) * 0.5f + 0.5f;
            float u1 = (float) Math.cos(ang1) * 0.5f + 0.5f;
            float v1 = (float) Math.sin(ang1) * 0.5f + 0.5f;
            // center vertex duplicated -> the quad collapses to the triangle (center, p1, p0).
            vertex(c, m, center.x, center.y, center.z, 0.5f, 0.5f, r, g, b, alpha);
            vertex(c, m, center.x, center.y, center.z, 0.5f, 0.5f, r, g, b, alpha);
            vertex(c, m, p1.x, p1.y, p1.z, u1, v1, r, g, b, alpha);
            vertex(c, m, p0.x, p0.y, p0.z, u0, v0, r, g, b, alpha);
        }
    }

    private static Vector3f ringPoint(Vector3f center, Vector3f u, Vector3f v, float angle, float radius) {
        float cs = (float) Math.cos(angle) * radius;
        float sn = (float) Math.sin(angle) * radius;
        return new Vector3f(
                center.x + u.x * cs + v.x * sn,
                center.y + u.y * cs + v.y * sn,
                center.z + u.z * cs + v.z * sn);
    }

    private static void vertex(VertexConsumer c, Matrix4f m,
                               float x, float y, float z, float u, float v,
                               float r, float g, float b, float a) {
        // addVertex with a Matrix4f transforms the position by the pose.
        c.addVertex(m, x, y, z).setUv(u, v).setColor(r, g, b, a);
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}
