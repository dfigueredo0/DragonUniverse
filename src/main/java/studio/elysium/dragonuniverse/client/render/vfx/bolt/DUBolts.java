package studio.elysium.dragonuniverse.client.render.vfx.bolt;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;
import studio.elysium.dragonuniverse.client.render.rendertype.DURenderTypes;
import studio.elysium.dragonuniverse.client.render.vfx.TrailVFXBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>Generates a {@link BoltInstance} per spawn and draws every branch through the existing GLOW
 * ribbon. Width/intensity taper by fork depth; the whole bolt fades + flickers over its lifetime.
 * Drawn from {@code DUWorldRenderer}'s {@code AfterTranslucentParticles} hook.</p>
 */
public final class DUBolts {
    private DUBolts() {}

    private static final List<BoltInstance> ACTIVE = new ArrayList<>();
    private static final RandomSource RNG = RandomSource.create();

    public static void spawn(Vector3f start, Vector3f end, BoltConfig cfg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        BoltNode root = BoltGenerator.generate(cfg, start, end, RNG);
        ACTIVE.add(new BoltInstance(root, cfg, mc.level.getGameTime()));
    }

    /** Preset: a vertical strike of height {@code height} ending at {@code groundPoint}. */
    public static void strikeGround(Vector3f groundPoint, float height, BoltConfig cfg) {
        spawn(new Vector3f(groundPoint).add(0.0f, height, 0.0f), new Vector3f(groundPoint), cfg);
    }

    /** Preset: a point-to-point arc. */
    public static void arc(Vector3f from, Vector3f to) {
        spawn(from, to, BoltConfig.arc());
    }

    public static void clear() {
        ACTIVE.clear();
    }

    public static int count() {
        return ACTIVE.size();
    }

    public static void render(PoseStack pose, float partialTick) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level == null ? 0L : mc.level.getGameTime();
        ACTIVE.removeIf(b -> b.expired(now));
        if (ACTIVE.isEmpty()) {
            return;
        }

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        var consumer = buffers.getBuffer(DURenderTypes.GLOW);

        for (BoltInstance bolt : ACTIVE) {
            BoltConfig c = bolt.config();
            float age = bolt.age(now, partialTick);
            float t = Mth.clamp(age / c.lifetime, 0.0f, 1.0f);
            float flicker = 0.7f + 0.3f * Mth.sin(age * 7.0f);
            float life = (1.0f - t) * flicker;

            for (BoltInstance.Branch branch : bolt.branches()) {
                float widthFactor = (float) Math.pow(c.childWidthFalloff, branch.depth());
                float intensity = (float) Math.pow(c.childIntensityFalloff, branch.depth());
                float width = c.baseWidth * widthFactor;
                float alpha = Mth.clamp(c.headAlpha * intensity * life, 0.0f, 1.0f);
                TrailVFXBuilder.ribbon(pose, consumer, branch.points(), width,
                        c.r, c.g, c.b, alpha, alpha);
            }
        }

        buffers.endBatch(DURenderTypes.GLOW);
    }
}
