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

    private static void vertex(VertexConsumer c, Matrix4f m,
                               float x, float y, float z, float u, float v,
                               float r, float g, float b, float a) {
        // addVertex with a Matrix4f transforms the position by the pose.
        c.addVertex(m, x, y, z).setUv(u, v).setColor(r, g, b, a);
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}
