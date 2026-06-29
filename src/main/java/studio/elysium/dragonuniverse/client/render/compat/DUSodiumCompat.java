package studio.elysium.dragonuniverse.client.render.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

/**
 * Sodium soft-compat — kept (unlike the Iris layer) because Sodium is a chunk-performance engine we
 * want to stay compatible with for FPS. Sodium replaces terrain/chunk rendering but does not own our
 * standalone pipelines, the {@code RenderLevelStageEvent} hooks, or the particle engine, so the
 * world VFX (trails/bolts/beams/emissive/particles) work unchanged.
 *
 * <p>The one well-defined surface: our fullscreen look-stack passes must read/write the correct main
 * render target. Sodium can change how render targets are set up, so the look stack should route its
 * main-target access through {@link #mainRenderTarget()} rather than reaching for the framebuffer
 * directly. Today that is simply {@code Minecraft#getMainRenderTarget()} (still correct under
 * Sodium); the indirection gives one place to special-case if a future Sodium build diverges.</p>
 */
public final class DUSodiumCompat {
    private DUSodiumCompat() {}

    private static Boolean loaded;

    public static boolean loaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded("sodium");
        }
        return loaded;
    }

    /**
     * The main render target our fullscreen look-stack passes composite into. This is the single
     * Sodium-aware seam: under vanilla and current Sodium it is {@code getMainRenderTarget()}, the
     * same framebuffer the world rendered into.
     */
    public static RenderTarget mainRenderTarget() {
        return Minecraft.getInstance().getMainRenderTarget();
    }
}
