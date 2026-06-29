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
import studio.elysium.dragonuniverse.client.render.post.DUPostChain;
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
        // Drain deferred GPU disposals (resized/retired look-stack targets) before any new work, so
        // ImGuiMC's deferred draw never references a view we freed too early. Runs every frame.
        DUPostChain.tickTrash();
        // NOTE: the geometry-normals per-frame clear is armed at AfterLevel (DUPostChain.onRenderLevel),
        // NOT here. Clouds render via the deferred FrameGraph, which executes after this stage; arming
        // the clear here would let a later cloud attach re-clear the buffer and wipe terrain+entity
        // normals before the look stack reads them. AfterLevel is after all normal writers.
        // When the HDR look stack is active, draw the VFX into its float overlay (so additive glow
        // accumulates past 1.0 and gets tone mapped) instead of straight into the LDR main target.
        // This stage (not AfterLevel) is where the camera modelview is still active for the VFX
        // vertex transform.
        if (DUPostChain.drawsVfxIntoHdr()) {
            DUPostChain.captureVfx(event.getPoseStack(), partialTick);
        } else {
            render(event.getPoseStack(), partialTick);
        }
    }

    /**
     * Draw all world VFX (trails, emissive demo, bolts, beams) through the GLOW render type into the
     * currently-bound target. Called either directly at AfterTranslucentParticles (LDR, look stack
     * off) or by {@link DUPostChain#captureVfx} with the HDR overlay bound (look stack on).
     */
    public static void render(PoseStack pose, float partialTick) {
        DUFrameData.upload(partialTick);

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
