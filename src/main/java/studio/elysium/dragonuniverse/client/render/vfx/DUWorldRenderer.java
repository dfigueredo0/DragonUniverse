package studio.elysium.dragonuniverse.client.render.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;
import studio.elysium.dragonuniverse.DragonUniverse;
import studio.elysium.dragonuniverse.client.render.core.DUFrameData;
import studio.elysium.dragonuniverse.client.render.rendertype.DURenderTypes;
import studio.elysium.dragonuniverse.client.render.vfx.beam.DUBeams;
import studio.elysium.dragonuniverse.client.render.vfx.bolt.DUBolts;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = DragonUniverse.MODID, value = Dist.CLIENT)
public final class DUWorldRenderer {
    private static final List<ActiveTrail> TRAILS = new ArrayList<>();

    public record ActiveTrail(List<Vector3f> points, float width, float r, float g, float b, float headAlpha, float tailAlpha) {}

    public static void addTrail(ActiveTrail t) {
        TRAILS.add(t);
    }

    public static void clearTrails() {
        TRAILS.clear();
    }

    public static List<ActiveTrail> getTrails() {
        return TRAILS;
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent.AfterTranslucentParticles event) {
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);

        DUFrameData.upload(partialTick);

        PoseStack pose = event.getPoseStack();

        if (!TRAILS.isEmpty()) {
            MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            var consumer = buffers.getBuffer(DURenderTypes.GLOW);

            for (ActiveTrail trail : TRAILS) {
                TrailVFXBuilder.ribbon(pose, consumer, trail.points(), trail.width(),
                        trail.r(), trail.g(), trail.b(), trail.headAlpha(), trail.tailAlpha());
            }

            buffers.endBatch(DURenderTypes.GLOW);
        }

        DUEmissiveDemo.render(pose, partialTick);
        DUBolts.render(pose, partialTick);
        DUBeams.render(pose, partialTick);
    }
}
