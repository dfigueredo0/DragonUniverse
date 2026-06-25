package studio.elysium.dragonuniverse.client.render.vfx.beam;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import studio.elysium.dragonuniverse.client.render.rendertype.DURenderTypes;
import studio.elysium.dragonuniverse.client.render.vfx.TrailVFXBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Manager for active modular beams (mirrors {@code DUBolts}).
 *
 * <p>Each beam re-evaluates its {@link BeamPath} every frame (so spiral/sine/wobble animate) and
 * draws through the GLOW ribbon, fading over its lifetime. Drawn from {@code DUWorldRenderer}'s
 * {@code AfterTranslucentParticles} hook.</p>
 */
public final class DUBeams {
    private DUBeams() {}

    private static final List<BeamInstance> ACTIVE = new ArrayList<>();

    public static void spawn(Vector3f from, Vector3f to, BeamPath path, BeamConfig cfg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        ACTIVE.add(new BeamInstance(from, to, path, cfg, mc.level.getGameTime()));
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

        float time = now + partialTick;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        var consumer = buffers.getBuffer(DURenderTypes.GLOW);

        for (BeamInstance beam : ACTIVE) {
            BeamConfig c = beam.config();
            float fade = 1.0f - Mth.clamp(beam.age(now, partialTick) / c.lifetime, 0.0f, 1.0f);
            List<Vector3f> pts = beam.path().points(beam.from(), beam.to(), time, c.segments);
            TrailVFXBuilder.ribbon(pose, consumer, pts, c.baseWidth,
                    c.r, c.g, c.b, c.headAlpha * fade, c.tailAlpha * fade);
        }

        buffers.endBatch(DURenderTypes.GLOW);
    }
}
