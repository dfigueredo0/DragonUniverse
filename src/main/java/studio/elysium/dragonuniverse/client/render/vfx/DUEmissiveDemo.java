package studio.elysium.dragonuniverse.client.render.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import studio.elysium.dragonuniverse.client.render.rendertype.DUEmissiveRenderTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>Each {@link DemoCube} is drawn in up to three layers to show the difference between a lit
 * base model and the emissive overlays:</p>
 * <ol>
 *   <li><b>base</b> — a dim cube through vanilla {@link RenderTypes#entitySolid} (world-lit);</li>
 *   <li><b>emissive</b> — the same cube, slightly larger, through {@link DUEmissiveRenderTypes#EMISSIVE}
 *       (full-bright, translucent, samples the emissive map);</li>
 *   <li><b>glow</b> — a larger additive halo through {@link DUEmissiveRenderTypes#GLOW_LAYER}.</li>
 * </ol>
 *
 * <p>This is deliberately not tied to a registered block/entity — it stays 100% client-side and
 * is driven from the ImGui debug panel.</p>
 */
public final class DUEmissiveDemo {
    private DUEmissiveDemo() {}

    /** Full-bright packed light (block 15, sky 15). EMISSIVE ignores the lightmap, but the
     *  ENTITY vertex format still carries a UV2 element that must be supplied. */
    private static final int FULL_BRIGHT = 0x00F000F0;

    // Unit cube: 6 faces, each {nx,ny,nz, then 4 corners as (x,y,z) offsets in [-1,1]}.
    // Corner order maps to uv (0,0),(0,1),(1,1),(1,0). Cull is off, so winding only matters
    // for PER_FACE_LIGHTING shading on the EMISSIVE path (via the normals).
    private static final float[][] FACES = {
            { 0, -1, 0,  -1, -1, -1,  -1, -1,  1,   1, -1,  1,   1, -1, -1 }, // down
            { 0,  1, 0,  -1,  1,  1,  -1,  1, -1,   1,  1, -1,   1,  1,  1 }, // up
            { 0, 0, -1,   1,  1, -1,   1, -1, -1,  -1, -1, -1,  -1,  1, -1 }, // north
            { 0, 0,  1,  -1,  1,  1,  -1, -1,  1,   1, -1,  1,   1,  1,  1 }, // south
            { -1, 0, 0,  -1,  1, -1,  -1, -1, -1,  -1, -1,  1,  -1,  1,  1 }, // west
            {  1, 0, 0,   1,  1,  1,   1, -1,  1,   1, -1, -1,   1,  1, -1 }, // east
    };

    public static final class DemoCube {
        public final Vector3f center;
        public float size;
        public final float r;
        public final float g;
        public final float b;

        public DemoCube(Vector3f center, float size, float r, float g, float b) {
            this.center = center;
            this.size = size;
            this.r = r;
            this.g = g;
            this.b = b;
        }
    }

    private static final List<DemoCube> CUBES = new ArrayList<>();

    // --- ImGui-driven tunables ---
    public static boolean showBase = true;
    public static boolean showEmissive = true;
    public static boolean showGlow = true;
    public static float emissiveStrength = 1.5F;
    public static float pulseSpeed = 0.15F;

    public static void addCube(DemoCube cube) {
        CUBES.add(cube);
    }

    public static void clearCubes() {
        CUBES.clear();
    }

    public static List<DemoCube> getCubes() {
        return CUBES;
    }

    /**
     * Drawn from {@link DUWorldRenderer}'s render-level hook (after the per-frame state upload
     */
    public static void render(PoseStack pose, float partialTick) {
        if (CUBES.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        float time = (mc.level == null ? 0 : mc.level.getGameTime()) + partialTick;
        float pulse = 1.0F + emissiveStrength * 0.5F * (float) Math.sin(time * pulseSpeed);

        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        for (DemoCube cube : CUBES) {
            float cx = cube.center.x - (float) cam.x();
            float cy = cube.center.y - (float) cam.y();
            float cz = cube.center.z - (float) cam.z();
            float half = cube.size * 0.5F;

            if (showBase) {
                VertexConsumer c = buffers.getBuffer(RenderTypes.entitySolid(DUEmissiveRenderTypes.DEMO_TEX));
                entityCube(pose, c, cx, cy, cz, half, 0.22F, 0.22F, 0.25F, 1.0F, FULL_BRIGHT);
            }
            if (showEmissive) {
                VertexConsumer c = buffers.getBuffer(DUEmissiveRenderTypes.EMISSIVE);
                entityCube(pose, c, cx, cy, cz, half * 1.02F,
                        cube.r * pulse, cube.g * pulse, cube.b * pulse, 0.9F, FULL_BRIGHT);
            }
            if (showGlow) {
                VertexConsumer c = buffers.getBuffer(DUEmissiveRenderTypes.GLOW_LAYER);
                simpleCube(pose, c, cx, cy, cz, half * 1.18F,
                        cube.r * pulse, cube.g * pulse, cube.b * pulse, 0.5F);
            }
        }

        buffers.endBatch();
    }

    private static void entityCube(PoseStack pose, VertexConsumer c,
                                   float cx, float cy, float cz, float half,
                                   float r, float g, float b, float a, int light) {
        PoseStack.Pose last = pose.last();
        for (float[] f : FACES) {
            float nx = f[0], ny = f[1], nz = f[2];
            for (int k = 0; k < 4; k++) {
                float x = cx + f[3 + k * 3] * half;
                float y = cy + f[4 + k * 3] * half;
                float z = cz + f[5 + k * 3] * half;
                float u = (k == 2 || k == 3) ? 1F : 0F;
                float v = (k == 1 || k == 2) ? 1F : 0F;
                c.addVertex(last, x, y, z)
                        .setColor(r, g, b, a)
                        .setUv(u, v)
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(last, nx, ny, nz);
            }
        }
    }

    private static void simpleCube(PoseStack pose, VertexConsumer c,
                                   float cx, float cy, float cz, float half,
                                   float r, float g, float b, float a) {
        Matrix4f m = pose.last().pose();
        for (float[] f : FACES) {
            for (int k = 0; k < 4; k++) {
                float x = cx + f[3 + k * 3] * half;
                float y = cy + f[4 + k * 3] * half;
                float z = cz + f[5 + k * 3] * half;
                float u = (k == 2 || k == 3) ? 1F : 0F;
                float v = (k == 1 || k == 2) ? 1F : 0F;
                c.addVertex(m, x, y, z).setUv(u, v).setColor(r, g, b, a);
            }
        }
    }
}
