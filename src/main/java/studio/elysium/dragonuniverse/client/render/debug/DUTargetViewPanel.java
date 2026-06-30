package studio.elysium.dragonuniverse.client.render.debug;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTextureView;
import foundry.imgui.api.ImGuiMC;
import foundry.imgui.api.ImGuiTextureProvider;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.type.ImBoolean;
import net.minecraft.client.Minecraft;
import studio.elysium.dragonuniverse.client.render.post.DUHiZBuffer;
import studio.elysium.dragonuniverse.client.render.post.DUPostChain;
import studio.elysium.dragonuniverse.client.render.water.DUWaterSim;

/**
 * <p> Blits the live render targets into the ImGui overlay via ImGuiMC's
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
    private static final int[] hiZLevel = { 0 };
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
            drawTarget("Final (tone mapped)", ImGuiMC.getTexture(present), w, h);
        }

        // The float HDR scene buffer (RGBA16F) before tone mapping. ImGui samples it clamped to
        // [0,1], so very bright VFX read as flat white here — that's expected; the tonemap pass is
        // what recovers their detail in the Final view above.
        GpuTextureView hdr = DUPostChain.hdrView();
        if (hdr != null) {
            drawTarget("HDR buffer (pre-tonemap, clamped)", ImGuiMC.getTexture(hdr), w, h);
        }

        GpuTextureView bloom = DUPostChain.bloomView();
        if (bloom != null) {
            drawTarget("Bloom (blurred, half-res, clamped)", ImGuiMC.getTexture(bloom), w, h);
        }

        // Geometry-normals G-buffer (RGB10_A2, N*0.5+0.5; a = coverage). Terrain (Sodium MRT) is
        // world-space at a~1.0: view-independent, so flat ground is one colour regardless of camera.
        // Entities (Phase 2 vanilla MRT) are view-space at a~0.667: their hue DOES shift as the camera
        // turns (that's how you tell them apart from terrain). Clouds are world-space (view-independent)
        // but tagged a~0.333 so SSAO excludes them and rim routes them to silver-lining. Sky/water
        // stay transparent (coverage 0). Otherwise stable under camera motion.
        GpuTextureView geoNorm = DUPostChain.geometryNormalsView();
        if (geoNorm != null) {
            drawTarget("Normals (geometry MRT, Sodium terrain)", ImGuiMC.getTexture(geoNorm), w, h);
        }

        // Water SSR G-buffer (RGBA16F): rg = octahedral view-space surface normal, b = fresnel, a = mask
        // (water surface depth). Only water pixels are non-zero; everything else is black. Present only
        // while SSR is enabled (the gbuf is allocated lazily by the translucent water pass).
        GpuTextureView waterGbuf = DUPostChain.waterGbufView();
        if (waterGbuf != null) {
            drawTarget("Water SSR G-buffer (rg=oct N, b=fresnel, a=mask)", ImGuiMC.getTexture(waterGbuf), w, h);
        }

        // Raw SSAO occlusion (grayscale): crevices/contacts dark, open flats white. Terrain only in
        // Phase 1 (entities/sky read white = unoccluded). Should be stable under camera motion.
        GpuTextureView ssao = DUPostChain.ssaoView();
        if (ssao != null) {
            drawTarget("SSAO (raw occlusion, full-res)", ImGuiMC.getTexture(ssao), w, h);
        }

        // Bilateral-blurred AO — the buffer actually multiplied into the scene. Should read like the raw
        // AO with the per-pixel IGN grain smoothed out while crease/silhouette edges stay sharp.
        GpuTextureView ssaoBlur = DUPostChain.ssaoBlurView();
        if (ssaoBlur != null) {
            drawTarget("SSAO (blurred, composited)", ImGuiMC.getTexture(ssaoBlur), w, h);
        }

        // Hi-Z min-depth pyramid (linear depth, grayscale: near dark, far white). Pick a level — higher
        // levels are blockier (the min-downsampled neighbourhood). Only present when Hi-Z is enabled.
        GpuTextureView hiz = DUPostChain.hiZVizView();
        if (hiz != null) {
            int maxLevel = Math.max(0, DUHiZBuffer.mipCount() - 1);
            if (ImGui.sliderInt("Hi-Z level", hiZLevel, 0, maxLevel)) {
                DUHiZBuffer.setVizLevel(hiZLevel[0]);
            }
            drawTarget("Hi-Z (min-depth, level " + DUHiZBuffer.vizLevel() + ")", ImGuiMC.getTexture(hiz), w, h);
        }

        // Ripple-sim height field (RGBA16F, r=height). Height oscillates around 0, so crests read
        // bright and troughs/flat read black: a disturbance shows as expanding bright rings that fade —
        // that's propagation + decay. Square field, so previewed square. World-anchored (stays put as you move).
        GpuTextureView ripple = DUWaterSim.fieldView();
        if (ripple != null) {
            drawTarget("Ripple sim field (height; crests bright)", ImGuiMC.getTexture(ripple), w, w);
        }

        // Sun's-eye shadow depth map (Stage A): grayscale, orthographic so depth is LINEAR (near-the-sun
        // dark, far white). The terrain silhouette from the sun's angle should be plainly visible and
        // should swing as the day cycle moves the sun. Square map, so previewed square. Only present when
        // the shadow pass is enabled.
        GpuTextureView shadowViz = DUPostChain.shadowVizView();
        if (shadowViz != null) {
            drawTarget("Shadow map (sun's-eye depth, Stage A)", ImGuiMC.getTexture(shadowViz), w, w);
        }

        // Stage D colored shadows: the translucent-occluder depth (grayscale) + the tint map (rgb=tint).
        GpuTextureView coloredViz = DUPostChain.coloredShadowVizView();
        if (coloredViz != null) {
            drawTarget("Colored shadow depth (translucent, Stage D)", ImGuiMC.getTexture(coloredViz), w, w);
        }
        GpuTextureView coloredTint = DUPostChain.coloredTintView();
        if (coloredTint != null) {
            drawTarget("Colored shadow tint (rgb, Stage D)", ImGuiMC.getTexture(coloredTint), w, w);
        }

        // Diagnostic: raw occlusion mask (sky = white, geometry = black) before the radial blur.
        GpuTextureView godrayMask = DUPostChain.godrayMaskView();
        if (godrayMask != null) {
            drawTarget("God-ray occlusion mask (half-res)", ImGuiMC.getTexture(godrayMask), w, h);
        }

        // God-ray intensity (half-res, grayscale) after the radial blur, before sun-colour tint.
        GpuTextureView godray = DUPostChain.godrayView();
        if (godray != null) {
            drawTarget("God rays (blurred intensity, half-res)", ImGuiMC.getTexture(godray), w, h);
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
