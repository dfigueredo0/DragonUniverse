package studio.elysium.dragonuniverse.client.render.debug;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTextureView;
import foundry.imgui.api.ImGuiMC;
import foundry.imgui.api.ImGuiTextureProvider;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.type.ImBoolean;
import net.minecraft.client.Minecraft;
import studio.elysium.dragonuniverse.client.render.post.DUPostChain;

/**
 * <p>Blits the live render targets into the ImGui overlay via ImGuiMC's
 * {@link ImGuiMC#image(ImGuiTextureProvider, float, float)} support: the main color + depth
 * targets, plus our own post intermediates (the fogged scene snapshot and the blurred bloom
 * buffer) exposed from {@link DUPostChain}.</p>
 *
 * <p>Only referenced from {@link DUDebugWindows}, which is itself only invoked when ImGuiMC is
 * loaded — so this class (which touches {@code foundry.imgui.*}) never loads in production.</p>
 */
public final class DUTargetViewPanel {
    private DUTargetViewPanel() {}

    private static final int[] previewWidth = { 256 };
    // Framebuffers are bottom-left origin; ImGui is top-left. Flip by default so previews are
    // upright. Toggle if your ImGuiMC build already flips internally.
    private static final ImBoolean flipY = new ImBoolean(true);

    public static void render() {
        if (!ImGui.collapsingHeader("Target Inspector (Phase 3c)")) {
            return;
        }
        if (!DUPostChain.isEnabled()) {
            ImGui.text("Enable the post chain (Post FX) to inspect targets.");
            return;
        }

        ImGui.checkbox("Flip Y", flipY);
        ImGui.sliderInt("Preview width", previewWidth, 128, 640);

        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        float aspect = main.height / (float) Math.max(1, main.width);
        float w = previewWidth[0];
        float h = w * aspect;

        // Only ever show textures we own. Handing ImGui the live main render target view crashes
        // on window resize, because Minecraft closes that view before ImGui's deferred draw.
        GpuTextureView present = DUPostChain.presentView();
        if (present != null) {
            drawTarget("Final (composited)", ImGuiMC.getTexture(present), w, h);
        }

        GpuTextureView depth = DUPostChain.depthVizView();
        if (depth != null) {
            drawTarget("Depth (linearized)", ImGuiMC.getTexture(depth), w, h);
        }

        GpuTextureView bloom = DUPostChain.bloomView();
        if (bloom != null) {
            drawTarget("Bloom (blurred, half-res)", ImGuiMC.getTexture(bloom), w, h);
        }
        GpuTextureView scene = DUPostChain.sceneCopyView();
        if (scene != null) {
            drawTarget("Scene copy (fogged)", ImGuiMC.getTexture(scene), w, h);
        }
    }

    private static void drawTarget(String label, ImGuiTextureProvider provider, float w, float h) {
        ImGui.text(label);
        if (flipY.get()) {
            ImGuiMC.image(provider, new ImVec2(w, h), new ImVec2(0, 1), new ImVec2(1, 0));
        } else {
            ImGuiMC.image(provider, w, h);
        }
    }
}
