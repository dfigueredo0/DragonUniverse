package studio.elysium.dragonuniverse.client.render.post;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Vector4f;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import studio.elysium.dragonuniverse.DragonUniverse;
import studio.elysium.dragonuniverse.client.render.compat.DUIrisCompat;
import studio.elysium.dragonuniverse.client.render.core.DUFrameData;
import studio.elysium.dragonuniverse.client.render.core.DUGodrayData;
import studio.elysium.dragonuniverse.client.render.core.DUGradeData;
import studio.elysium.dragonuniverse.client.render.core.DULookData;
import studio.elysium.dragonuniverse.client.render.core.DUSsaoData;
import studio.elysium.dragonuniverse.client.render.core.DUShadowData;
import studio.elysium.dragonuniverse.client.render.core.DUSsrData;
import studio.elysium.dragonuniverse.client.render.core.DUViewData;
import studio.elysium.dragonuniverse.client.render.core.DUWaterData;
import studio.elysium.dragonuniverse.client.render.core.DUWaterFogData;
import studio.elysium.dragonuniverse.client.render.sky.DUAtmosphereTint;
import studio.elysium.dragonuniverse.client.render.vfx.DUWorldRenderer;
import studio.elysium.dragonuniverse.client.render.water.DUWater;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * The HDR foundation of the look stack. Hand-rolled (vanilla's JSON {@code PostChain} can't
 * feed a per-frame dynamic UBO), each pass mirrors {@code PostPass}'s draw block: open a
 * {@link RenderPass}, bind samplers/UBO, draw a vertexless fullscreen triangle.
 *
 * <p><b>MC 26.1 has no float color {@code TextureFormat}, and redirecting vanilla's entire
 * world render into a fabricated float buffer is out of scope. Instead render only the
 * additive world VFX into a float ({@code RGBA16F}) overlay (so they accumulate past 1.0
 * instead of clipping), tone map that overlay, and composite it additively over the
 * otherwise-pristine main target. The base scene is left exactly as vanilla drew it.</p>
 *
 * <p>Per frame:</p>
 * <ol>
 *   <li><b>capture</b> (at {@code AfterTranslucentParticles}, via {@link #captureVfx}): clear the
 *       float {@link #hdr} buffer, then draw the world VFX into it through
 *       {@link RenderSystem#outputColorTextureOverride} (depth falls back to the main target's, so
 *       VFX stay occluded by terrain). This stage has the camera modelview active — required for the
 *       VFX vertex transform.</li>
 *   <li><b>composite</b> (at {@code AfterLevel}, in {@link #onRenderLevel}): tone map {@link #hdr}
 *       (exposure + ACES/Reinhard) and add it onto the main color. A fullscreen pass — no modelview
 *       needed, which is why it's safe at {@code AfterLevel} (where the camera matrix is gone).</li>
 * </ol>
 */
@EventBusSubscriber(modid = DragonUniverse.MODID, value = Dist.CLIENT)
public final class DUPostChain {
    private DUPostChain() {}

    // God-ray sun visibility fade (radial NDC distance from screen centre). Full strength out to the
    // screen corner (~1.41), fully faded by FADE_OUT so a grazing/off-screen sun never blows out.
    private static final float GODRAY_FADE_IN = 1.5F;
    private static final float GODRAY_FADE_OUT = 2.5F;

    private static boolean enabled = false;
    private static boolean warnedOnce = false;
    /** Set when {@link #captureVfx} drew the overlay this frame; gates the AfterLevel composite. */
    private static boolean hdrPopulated = false;

    /** The float HDR overlay the additive VFX accumulate into before tone mapping. */
    private static final DUHdrTarget hdr = new DUHdrTarget("DU HDR overlay");

    // Bloom: half-res float ping-pong buffers. Float (not RGBA8) so the bright-extracted
    // values >1.0 survive the blur instead of clipping, which is the whole point of HDR bloom.
    private static final DUHdrTarget bloomA = new DUHdrTarget("DU bloom A");
    private static final DUHdrTarget bloomB = new DUHdrTarget("DU bloom B");

    // World-space normals now come from the Sodium geometry MRT (DUNormalBuffer, written during the
    // terrain pass), not a depth-reconstruction pass — see the MRT plan. The depth-recon path is gone.

    // SSAO: full-res RGBA8 ambient-occlusion factor (grayscale; composite reads .r). Full-res to start
    // (no depth-aware upsample — fewest moving parts); can drop to half-res + bilateral upsample later.
    private static final PostTarget ssao =
            new PostTarget("DU ssao", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING);
    // The bilateral-blurred AO (full-res). `ssao` stays the raw buffer (for the inspector);
    // the blur reads it and the multiply composite reads this.
    private static final PostTarget ssaoBlur =
            new PostTarget("DU ssao blur", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING);

    // Hi-Z debug viz: a full-res unpack of a chosen pyramid level for the Target Inspector (the pyramid
    // itself is packed RGBA8, unreadable raw). The pyramid lives in DUHiZBuffer (its own mip texture).
    private static final PostTarget hiZViz =
            new PostTarget("DU hiz viz", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING);

    // Shadow-map debug viz (Stage A): a small grayscale unpack of the sun's-eye depth map for the
    // Target Inspector (the raw depth texture is near-white/unreadable). The map itself lives in
    // DUShadowPass (its own depth-only FBO, built during the terrain pass).
    private static final PostTarget shadowViz =
            new PostTarget("DU shadow viz", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING);
    // Stage D: a grayscale unpack of the translucent (colored) shadow depth for the inspector. The tint
    // color map itself is RGBA8 and shown directly (DUShadowPass.coloredTintView()).
    private static final PostTarget coloredShadowViz =
            new PostTarget("DU colored shadow viz", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING);

    // God rays: half-res RGBA8 occlusion mask + radial-blur result. Half-res because the radial
    // blur is the expensive part and rays are low-frequency, so it upsamples cleanly.
    private static final PostTarget godrayMask =
            new PostTarget("DU godray mask", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING);
    private static final PostTarget godrayBlur =
            new PostTarget("DU godray blur", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING);

    // Color grade: a pre-grade copy of the composited frame (the grade pass can't read & write the
    // main color in one pass, so we copy main -> here, then grade this -> main).
    private static final PostTarget gradeSrc =
            new PostTarget("DU grade src", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING);

    // Underwater scene fog: a pre-fog copy of the scene (the pass can't read & write the main
    // color in one pass). Runs independently of the look stack — only while the camera eye is in water.
    private static final PostTarget underwaterSrc =
            new PostTarget("DU underwater src", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING);
    private static boolean warnedUnderwater = false;

    // SSR: a pre-reflection copy of the scene (the pass can't read & write the main color in one
    // pass). The SSR raymarch samples this for the reflected color and writes the result back into main.
    private static final PostTarget ssrSrc =
            new PostTarget("DU ssr src", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING);
    // Temporal history — last frame's SSR output (rgb) + water mask (a). The SSR pass reads
    // it; right after, the just-written main color is copied here for next frame. Single buffer (read in
    // the pass, written by the copy after) — no ping-pong needed.
    private static final PostTarget ssrHistory =
            new PostTarget("DU ssr history", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING);
    // False until the SSR pass has populated the history at least once since enable/resize; while false the
    // temporal feedback is forced to 0 so the first frame never blends an uninitialized history buffer
    // (cheaper + safer than clearing it, which would need a render-attachment usage the buffer doesn't have).
    private static boolean ssrHistoryValid = false;
    private static boolean warnedSsr = false;
    private static boolean warnedShadow = false;
    // Previous-frame camera pose, for the temporal accumulation's stillness gate (no motion-vector buffer
    // exists in the stack, so accumulation backs off by how much the camera moved/rotated this frame).
    private static final Matrix3f ssrPrevRot = new Matrix3f();
    private static double ssrPrevX, ssrPrevY, ssrPrevZ;
    private static boolean ssrHasPrev = false;

    // A controlled copy of the final frame for the inspector. We never hand ImGui the live main
    // render target view — Minecraft closes it on resize, which crashes ImGui's deferred draw
    // ("Texture view ... has been closed"). Copies into a texture we own dodge that entirely.
    private static final PostTarget presentCopy =
            new PostTarget("DU present copy", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING);

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Pump the deferred GPU-resource disposal queue once per frame. Retired targets (from resizes or
     * disabling the look stack) are closed a few frames later so ImGuiMC's deferred draw never touches
     * a freed view. Safe to call every frame regardless of look-stack state.
     */
    public static void tickTrash() {
        DUGpuTrash.tick();
    }

    /**
     * True when the look stack will capture VFX into the HDR overlay this frame (so the direct LDR
     * VFX draw should stand down). Look stack on, and no Iris pack owning the frame.
     */
    public static boolean drawsVfxIntoHdr() {
        return enabled && !DUIrisCompat.shaderPackActive();
    }

    /** The float HDR overlay view, for the Target Inspector. Null until the chain runs once. */
    public static GpuTextureView hdrView() {
        return hdr.view();
    }

    /** A copy of the final composited frame, for the Target Inspector. Null until run once. */
    public static GpuTextureView presentView() {
        return presentCopy.view;
    }

    /** The blurred HDR bloom (half-res), for the Target Inspector. Null until the chain runs once. */
    public static GpuTextureView bloomView() {
        return bloomA.view();
    }

    /** The Sodium geometry-normals G-buffer (Phase 1 MRT), for the Target Inspector. Null until run. */
    public static GpuTextureView geometryNormalsView() {
        return DUNormalBuffer.view();
    }

    /** The water SSR G-buffer (rg=oct normal, b=fresnel, a=mask), for the Target Inspector. Null until run. */
    public static GpuTextureView waterGbufView() {
        return DUWaterGbuffer.view();
    }

    /** The raw SSAO occlusion buffer (grayscale, full-res), for the Target Inspector. Null until run. */
    public static GpuTextureView ssaoView() {
        return ssao.view;
    }

    /** The bilateral-blurred SSAO buffer (what gets composited), for the Target Inspector. Null until run. */
    public static GpuTextureView ssaoBlurView() {
        return ssaoBlur.view;
    }

    /** The Hi-Z pyramid debug viz (unpacked grayscale of the selected level), for the Target Inspector. */
    public static GpuTextureView hiZVizView() {
        return hiZViz.view;
    }

    /** The sun's-eye shadow-map debug viz (Stage A; grayscale depth), for the Target Inspector. Null until run. */
    public static GpuTextureView shadowVizView() {
        return shadowViz.view;
    }

    /** The translucent (colored) shadow depth viz (Stage D; grayscale), for the inspector. Null until run. */
    public static GpuTextureView coloredShadowVizView() {
        return coloredShadowViz.view;
    }

    /** The translucent-occluder tint map (Stage D; rgb=tint, a=opacity), for the inspector. Null until run. */
    public static GpuTextureView coloredTintView() {
        return DUShadowPass.coloredTintView();
    }

    /** The blurred god-ray intensity (half-res, Task 4), for the Target Inspector. Null until run once. */
    public static GpuTextureView godrayView() {
        return godrayBlur.view;
    }

    /** The raw god-ray occlusion mask (half-res, pre-blur), for the Target Inspector. Diagnostic. */
    public static GpuTextureView godrayMaskView() {
        return godrayMask.view;
    }

    /** Re-populated when an LDR scene snapshot is needed again. Null for now. */
    public static GpuTextureView sceneCopyView() {
        return null;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        DUFrameData.postProcessingEnabled = value;
        if (!value) {
            hdrPopulated = false;
            // Release GPU resources deterministically when the look stack is off; they lazily
            // re-create on the next enabled frame.
            hdr.close();
            bloomA.close();
            bloomB.close();
            DUNormalBuffer.close();
            ssao.close();
            ssaoBlur.close();
            hiZViz.close();
            DUHiZBuffer.close();
            godrayMask.close();
            godrayBlur.close();
            shadowViz.close();
            coloredShadowViz.close();
            DUShadowPass.close();
            DUShadowData.close();
            gradeSrc.close();
            underwaterSrc.close();
            ssrSrc.close();
            ssrHistory.close();
            ssrHistoryValid = false;
            ssrHasPrev = false;
            DUWaterGbuffer.close();
            DUSsrData.close();
            DUWaterFogData.close();
            presentCopy.close();
            DULookData.close();
            DUGradeData.close();
            DUViewData.close();
            DUGodrayData.close();
        }
    }

    /**
     * Render the world VFX into the float HDR overlay (cleared first), with immediate draws
     * redirected via {@link RenderSystem#outputColorTextureOverride}. Called from
     * {@link DUWorldRenderer} at {@code AfterTranslucentParticles} when {@link #drawsVfxIntoHdr()}.
     * Owns all the GL-state juggling so the VFX code stays target-agnostic.
     */
    public static void captureVfx(PoseStack pose, float partialTick) {
        hdrPopulated = false;
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        if (main.getColorTexture() == null || main.getDepthTextureView() == null) {
            return;
        }
        if (main.width <= 0 || main.height <= 0) {     // minimized / mid-resize degenerate size
            return;
        }
        hdr.ensure(main.width, main.height);
        if (hdr.view() == null || hdr.texture() == null) {
            return;
        }
        try {
            // Clear last frame's overlay so additive VFX don't ghost across frames.
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(hdr.texture(), 0);

            // Redirect immediate RenderType draws' color into the overlay; depth falls back to the
            // main target's depth (so VFX are occluded by terrain). Always clear the override.
            RenderSystem.outputColorTextureOverride = hdr.view();
            try {
                DUWorldRenderer.render(pose, partialTick);
            } finally {
                RenderSystem.outputColorTextureOverride = null;
            }
            hdrPopulated = true;
        } catch (Exception e) {
            failOnce("VFX HDR capture failed; disabling look stack.", e);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent.AfterLevel event) {
        // Arm the geometry-normals clear for the NEXT frame's first writer. Done here (AfterLevel, after
        // the whole level FrameGraph incl. the deferred clouds pass) rather than mid-frame, so no later
        // writer can re-clear the buffer and wipe earlier normals. Resets a guard only (the actual clear
        // happens on next frame's first DUNormalBuffer.attach); safe to run unconditionally.
        DUNormalBuffer.beginFrame();
        // Arm the SSR water-gbuf clear for the next frame's first writer (same rationale as the normal buffer).
        DUWaterGbuffer.beginFrame();
        // Reset the shadow pass's once-per-frame fire guard (look-stack independent: shadows can run with
        // the look stack off, they just can't be inspected without it).
        DUShadowPass.beginFrame();
        // Underwater scene fog runs independently of the look-stack toggle (water can be used standalone):
        // tint the submerged scene before the look-stack passes (so glow/VFX still read through the water).
        renderUnderwaterFog(event);
        if (!enabled || !hdrPopulated) {
            return;
        }
        // When an Iris shaderpack is active it owns the gbuffer/post; stand down and let it drive.
        if (DUIrisCompat.shaderPackActive()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        GpuTexture mainColor = main.getColorTexture();
        GpuTextureView mainColorView = main.getColorTextureView();
        GpuTextureView mainDepthView = main.getDepthTextureView();
        if (mainColor == null || mainColorView == null || mainDepthView == null || hdr.view() == null) {
            return;
        }

        int w = main.width;
        int h = main.height;
        if (w <= 0 || h <= 0) {     // minimized / mid-resize degenerate size
            return;
        }
        int bw = Math.max(1, w / 2);
        int bh = Math.max(1, h / 2);
        presentCopy.ensure(w, h);
        bloomA.ensure(bw, bh);
        bloomB.ensure(bw, bh);
        godrayMask.ensure(bw, bh);
        godrayBlur.ensure(bw, bh);
        ssao.ensure(w, h);
        ssaoBlur.ensure(w, h);
        boolean gradeOn = DUGradeData.enabled || DUGradeData.skyEnabled;
        if (gradeOn) {
            if (DUGradeData.skyFromDimension) {
                DUAtmosphereTint.refresh();   // per-dimension atmosphere -> auto sky-tint (cached on change)
            }
            gradeSrc.ensure(w, h);
            DUGradeData.upload();
        }
        DULookData.upload();
        DUSsaoData.upload();

        // Capture this frame's camera matrices from the level render state (plain value
        // objects, unaffected by the modelview GL state being torn down by now at AfterLevel). The
        // base projection here omits view-bob/portal skew applied later in renderLevel — negligible
        // for normal reconstruction, and it stays consistent with viewRotationMatrix (its sibling).
        CameraRenderState cam = event.getLevelRenderState().cameraRenderState;
        DUViewData.set(cam.projectionMatrix, cam.viewRotationMatrix);
        DUViewData.upload();

        // Project the mod's sun direction to screen space for the god-ray radial blur, with a
        // robust visibility gate. Naively gating on clip.w > 0 lets the sun's screen UV blow up as it
        // grazes the camera's side plane (w -> 0 => UV -> huge), which feeds the radial blur a garbage
        // march direction and makes it blow out. Instead: require the sun clearly in front, then fade by
        // its radial distance from screen centre so it's full-strength on-screen, smoothly off past the
        // edge, and fully off once grazing/behind (never feeding an exploded UV).
        Vector4f sun = new Vector4f(DUFrameData.sunDir.x, DUFrameData.sunDir.y, DUFrameData.sunDir.z, 0.0F);
        cam.viewRotationMatrix.transform(sun);     // -> view space (w stays 0); camera looks down -Z
        float viewZ = sun.z;
        cam.projectionMatrix.transform(sun);       // -> clip space
        float sunU = 0.5F, sunV = 0.5F, sunGate = 0.0F;
        if (viewZ < -1.0e-3F && sun.w > 1.0e-4F) { // clearly in front of the camera
            float ndcX = sun.x / sun.w;
            float ndcY = sun.y / sun.w;
            float r = (float) Math.sqrt(ndcX * ndcX + ndcY * ndcY);  // 0 = centre, ~1.41 = screen corner
            // Full strength across the screen (corners at ~1.41), fading out by GODRAY_FADE_OUT.
            sunGate = clamp01((GODRAY_FADE_OUT - r) / (GODRAY_FADE_OUT - GODRAY_FADE_IN));
            if (sunGate > 0.0F) {
                sunU = ndcX * 0.5F + 0.5F;
                sunV = ndcY * 0.5F + 0.5F;
            }
        }
        DUGodrayData.setSunScreen(sunU, sunV, sunGate);
        // World-space sun direction for the sun-driven rim light: view-independent, so the rim persists
        // regardless of where the camera looks (unlike the screen-space shafts).
        DUGodrayData.setSunWorldDir(DUFrameData.sunDir.x, DUFrameData.sunDir.y, DUFrameData.sunDir.z);
        DUGodrayData.upload();

        // SSR temporal: derive this frame's effective history feedback from camera stillness. A still
        // camera keeps strong feedback (averaging out the wave shimmer); rotation/translation drops it
        // toward 0 so moving views don't ghost. No motion-vector buffer exists in the stack — same-UV
        // reuse is valid while still, and feedback is ~0 once moving. (Rotation weighted heavier: a small
        // turn shifts the on-screen reflection a lot.)
        float effFeedback = 0.0F;
        if (DUSsrData.temporalEnabled) {
            Vec3 camPos = mc.gameRenderer.getMainCamera().position();
            Matrix3f curRot = new Matrix3f().set(cam.viewRotationMatrix);
            if (ssrHasPrev && ssrHistoryValid) {                     // need a prev pose AND a populated history
                Matrix3f rel = new Matrix3f(curRot).mul(new Matrix3f(ssrPrevRot).transpose());
                float stillness = getStillness(rel, camPos);
                effFeedback = DUSsrData.maxFeedback * stillness;
            }
            ssrPrevRot.set(curRot);
            ssrPrevX = camPos.x;
            ssrPrevY = camPos.y;
            ssrPrevZ = camPos.z;
            ssrHasPrev = true;
        }
        DUSsrData.runtimeFeedback = effFeedback;
        DUSsrData.upload();

        try {
            CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
            GpuSampler clamp = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

            // NEAREST depth sampler for the depth-driven passes (god rays, SSAO, and the rim's
            // view-direction reconstruction) so depth isn't interpolated across silhouette edges.
            // World-space normals come from the Sodium geometry MRT (DUNormalBuffer), not reconstruction.
            GpuSampler depthClamp = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);

            // Shadow-map debug viz (Stage A): unpack the sun's-eye depth map to grayscale for the
            // inspector. The map is built during the terrain pass (DUShadowPass); this only displays it.
            // Independent of the look-stack passes; produces nothing when shadows are off.
            if (DUShadowPass.isEnabled() && DUShadowPass.depthView() != null) {
                shadowViz.ensure(1024, 1024);   // 1024 so small casters aren't lost to viz downsample aliasing
                runPass(enc, "DU shadow viz", shadowViz.view, DUPostPipelines.SHADOW_VIZ,
                        pass -> pass.bindTexture("DepthSampler", DUShadowPass.depthView(), depthClamp));
                // Stage D: same unpack for the translucent (colored) shadow depth, when built.
                if (DUShadowPass.hasColoredMap() && DUShadowPass.coloredDepthView() != null) {
                    coloredShadowViz.ensure(1024, 1024);
                    runPass(enc, "DU colored shadow viz", coloredShadowViz.view, DUPostPipelines.SHADOW_VIZ,
                            pass -> pass.bindTexture("DepthSampler", DUShadowPass.coloredDepthView(), depthClamp));
                }
            }

            // -1) Hi-Z min-depth pyramid (optional): linearize the scene depth into level 0, then min-
            //     downsample up the chain, so SSAO can read a footprint-matched mip at distance (contact
            //     shadows persist instead of fading). Built before SSAO; off by default. A debug viz of a
            //     chosen level is rendered for the inspector (the pyramid is packed RGBA8, unreadable raw).
            if (DUSsaoData.hiZEnabled) {
                DUHiZBuffer.ensure(w, h);
                if (DUHiZBuffer.view() != null) {
                    runPass(enc, "DU hiz init", DUHiZBuffer.level(0), DUPostPipelines.HIZ_INIT, pass -> {
                        pass.setUniform("DUView", DUViewData.slice());
                        pass.bindTexture("DepthSampler", mainDepthView, depthClamp);
                    });
                    for (int k = 1; k < DUHiZBuffer.mipCount(); k++) {
                        final int dst = k;
                        runPass(enc, "DU hiz down", DUHiZBuffer.level(dst), DUPostPipelines.HIZ_DOWN,
                                pass -> pass.bindTexture("InSampler", DUHiZBuffer.level(dst - 1), depthClamp));
                    }
                    // Debug viz of the selected level (upscaled to full-res grayscale for the inspector).
                    hiZViz.ensure(w, h);
                    GpuTextureView vizLevel = DUHiZBuffer.level(DUHiZBuffer.vizLevel());
                    if (vizLevel != null) {
                        runPass(enc, "DU hiz viz", hiZViz.view, DUPostPipelines.HIZ_VIZ,
                                pass -> pass.bindTexture("InSampler", vizLevel, depthClamp));
                    }
                }
            }

            //    SSAO: view-space hemisphere occlusion from depth + geometry normals, bilateral-blurred,
            //    then MULTIPLIED into the scene. Placed BEFORE the additive passes (bloom/tonemap/godray/
            //    rim) so the light those add isn't darkened by the occlusion. Needs the geometry normals,
            //    so it self-gates on their buffer (degrades cleanly to no AO when Sodium/inject is absent).
            if (DUSsaoData.ssaoEnabled && DUNormalBuffer.view() != null) {
                // Raw occlusion -> ssao (also the "raw" Target Inspector view).
                // HiZSampler is declared by the pipeline so it must always be bound: use the pyramid when
                // Hi-Z is on and built, else a harmless fallback (the depth view) the shader won't read
                // (uParams3.x = 0). Mipmap sampler so the shader's textureLod can reach coarse levels.
                GpuSampler hizSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST, true);
                GpuTextureView hizView = (DUSsaoData.hiZEnabled && DUHiZBuffer.view() != null)
                        ? DUHiZBuffer.view() : mainDepthView;
                runPass(enc, "DU ssao", ssao.view, DUPostPipelines.SSAO, pass -> {
                    pass.setUniform("DUView", DUViewData.slice());
                    pass.setUniform("DUSsao", DUSsaoData.slice());
                    pass.bindTexture("DepthSampler", mainDepthView, depthClamp);
                    pass.bindTexture("NormalSampler", DUNormalBuffer.view(), depthClamp);
                    pass.bindTexture("HiZSampler", hizView, hizSampler);
                });
                // depth-aware blur ssao -> ssaoBlur (cleans the IGN noise, edge-preserving).
                runPass(enc, "DU ssao blur", ssaoBlur.view, DUPostPipelines.SSAO_BLUR, pass -> {
                    pass.setUniform("DUView", DUViewData.slice());
                    pass.bindTexture("InSampler", ssao.view, clamp);
                    pass.bindTexture("DepthSampler", mainDepthView, depthClamp);
                });
                // multiply the blurred AO into the main color (uncovered pixels carry AO=1 -> no-op).
                runPass(enc, "DU ssao composite", mainColorView, DUPostPipelines.SSAO_COMPOSITE,
                        pass -> pass.bindTexture("InSampler", ssaoBlur.view, clamp));
            }

            //    Stage-B hard shadows: sample the sun's-eye map (built during the terrain pass) per
            //    fragment and MULTIPLY-darken shadowed, sun-facing terrain. Placed with SSAO (before the
            //    additive passes) so added light isn't darkened. Uses the EXACT light matrix the map was
            //    drawn with (from DUShadowPass) so producer/receiver can't drift. Fail-soft in its own
            //    scope — a shadow failure disables shadows only, never the rest of the look stack.
            if (DUShadowData.enabled && DUShadowPass.hasMap() && DUNormalBuffer.view() != null) {
                try {
                    float shadowTexelWorld = 2.0F * DUShadowPass.coverageBlocks
                            / Math.max(1, DUShadowPass.resolution);
                    // Stage D gate: sample the tint map only when it's actually been built this run, so the
                    // receiver never reads a garbage/unbuilt colored map. When off, bind the opaque depth as a
                    // harmless placeholder for the two colored samplers (uColored.w == 0 -> never read).
                    boolean coloredOn = DUShadowData.coloredEnabled && DUShadowPass.hasColoredMap();
                    DUShadowData.coloredRuntime = coloredOn;
                    GpuTextureView coloredDepth = coloredOn ? DUShadowPass.coloredDepthView() : DUShadowPass.depthView();
                    GpuTextureView coloredTint = coloredOn ? DUShadowPass.coloredTintView() : DUShadowPass.depthView();
                    DUShadowData.upload(DUShadowPass.lastLightViewProj(), DUShadowPass.lastSunDir(),
                            shadowTexelWorld);
                    runPass(enc, "DU shadow apply", mainColorView, DUPostPipelines.SHADOW_APPLY, pass -> {
                        pass.setUniform("DUShadow", DUShadowData.slice());
                        pass.bindTexture("DepthSampler", mainDepthView, depthClamp);
                        pass.bindTexture("NormalSampler", DUNormalBuffer.view(), depthClamp);
                        pass.bindTexture("ShadowSampler", DUShadowPass.depthView(), depthClamp);
                        pass.bindTexture("ColoredDepthSampler", coloredDepth, depthClamp);
                        pass.bindTexture("ColoredTintSampler", coloredTint, clamp);
                    });
                } catch (Exception e) {
                    DUShadowData.enabled = false;
                    if (!warnedShadow) {
                        warnedShadow = true;
                        DragonUniverse.LOGGER.warn("[Dragon Universe] Shadow apply pass failed; disabling "
                                + "shadows only (the rest of the look stack is unaffected).", e);
                    }
                }
            }

            //      SSR: reflect on-screen geometry off the water surface. Runs AFTER SSAO
            //      (so reflections sample an AO-correct scene) and BEFORE the additive passes + grade (so
            //      reflections participate in tonemap/god rays/grade). Water-only via the water G-buffer
            //      mask; reconstructs the surface from the gbuf, marches against the opaque-depth copy (so it
            //      never self-hits the water plane). Outputs the RAW reflected color for review — no
            //      fresnel blend/fallback yet. Disabled when the eye is submerged.
            if (DUSsrData.enabled && DUWaterGbuffer.wasWrittenThisFrame() && DUWaterGbuffer.view() != null
                    && DUWater.opaqueDepthView() != null && !DUWater.isEyeInWater()) {
                // Fail-soft in its OWN scope: any SSR failure disables SSR only and leaves the rest of the
                // look stack running (the spec's hard requirement — a broken reflection must never take down
                // god rays / SSAO / grade). Without this, a thrown SSR op trips the outer catch and kills it all.
                try {
                    ssrSrc.ensure(w, h);
                    ssrHistory.ensure(w, h);
                    enc.copyTextureToTexture(mainColor, ssrSrc.texture, 0, 0, 0, 0, 0, w, h);
                    runPass(enc, "DU ssr", mainColorView, DUPostPipelines.SSR, pass -> {
                        pass.setUniform("DUView", DUViewData.slice());
                        pass.setUniform("DUSsr", DUSsrData.slice());
                        pass.setUniform("DUGodray", DUGodrayData.slice());   // sky-fallback sun colour + world dir
                        pass.bindTexture("SceneColorSampler", ssrSrc.view, clamp);
                        pass.bindTexture("SceneDepthSampler", DUWater.opaqueDepthView(), depthClamp);
                        pass.bindTexture("WaterGbufSampler", DUWaterGbuffer.view(), depthClamp);
                        pass.bindTexture("HistorySampler", ssrHistory.view, clamp);
                    });
                    // Stash this frame's SSR result (now in main; rgb + water mask in alpha) as next frame's
                    // history. Single buffer: it was read in the pass above, so overwriting it now is safe.
                    enc.copyTextureToTexture(mainColor, ssrHistory.texture, 0, 0, 0, 0, 0, w, h);
                    ssrHistoryValid = true;     // history now holds real data -> temporal may engage next frame
                } catch (Exception e) {
                    DUSsrData.enabled = false;
                    ssrHistoryValid = false;
                    if (!warnedSsr) {
                        warnedSsr = true;
                        DragonUniverse.LOGGER.warn("[Dragon Universe] SSR pass failed; disabling SSR only "
                                + "(the rest of the look stack is unaffected).", e);
                    }
                }
            }

            // bloom bright-extract (soft-knee threshold) from the HDR overlay -> bloomA (half-res).
            runPass(enc, "DU bloom prefilter", bloomA.view(), DUPostPipelines.BLOOM_PREFILTER, pass -> {
                pass.setUniform("DULook", DULookData.slice());
                pass.bindTexture("InSampler", hdr.view(), clamp);
            });

            // separable Gaussian blur, ping-ponging A<->B. More iterations = wider bloom (radius).
            int iterations = Math.max(1, DULookData.bloomRadius);
            for (int i = 0; i < iterations; i++) {
                runPass(enc, "DU bloom blurH", bloomB.view(), DUPostPipelines.BLUR_H,
                        pass -> pass.bindTexture("InSampler", bloomA.view(), clamp));
                runPass(enc, "DU bloom blurV", bloomA.view(), DUPostPipelines.BLUR_V,
                        pass -> pass.bindTexture("InSampler", bloomB.view(), clamp));
            }

            //    tonemap (overlay + bloom) and add onto the main target (additive blend on the
            //    pipeline). Where overlay and bloom are empty, tonemap(0)==0 -> base scene untouched.
            runPass(enc, "DU tonemap", mainColorView, DUPostPipelines.TONEMAP, pass -> {
                pass.setUniform("DULook", DULookData.slice());
                pass.bindTexture("InSampler", hdr.view(), clamp);
                pass.bindTexture("BloomSampler", bloomA.view(), clamp);
            });

            //    god rays: sky-occlusion mask -> radial blur toward the sun -> additive composite onto
            //    the scene. Skipped entirely when off; the blur shader also self-gates when the sun is
            //    behind the camera.
            if (DUGodrayData.godrayEnabled) {
                runPass(enc, "DU godray mask", godrayMask.view, DUPostPipelines.GODRAY_MASK,
                        pass -> pass.bindTexture("DepthSampler", mainDepthView, depthClamp));
                runPass(enc, "DU godray blur", godrayBlur.view, DUPostPipelines.GODRAY_BLUR, pass -> {
                    pass.setUniform("DUGodray", DUGodrayData.slice());
                    pass.bindTexture("InSampler", godrayMask.view, clamp);
                });
                runPass(enc, "DU godray composite", mainColorView, DUPostPipelines.GODRAY_COMPOSITE, pass -> {
                    pass.setUniform("DUGodray", DUGodrayData.slice());
                    pass.bindTexture("InSampler", godrayBlur.view, clamp);
                });
            }

            //    optional rim light (Fresnel edge glow from the geometry normals) + silver-lining (cloud
            //    edge back-light) — both produced by the rim pass, additive. Skipped when the geometry
            //    normal buffer isn't available (Sodium absent / inject failed), so it degrades cleanly to
            //    off rather than sampling a null view.
            boolean rimOn = DUGodrayData.rimEnabled && DUGodrayData.rimStrength > 0.0F;
            boolean silverOn = DUGodrayData.silverEnabled && DUGodrayData.silverStrength > 0.0F;
            if ((rimOn || silverOn) && DUNormalBuffer.view() != null) {
                runPass(enc, "DU rim", mainColorView, DUPostPipelines.RIM, pass -> {
                    pass.setUniform("DUView", DUViewData.slice());
                    pass.setUniform("DUGodray", DUGodrayData.slice());
                    pass.bindTexture("NormalSampler", DUNormalBuffer.view(), depthClamp);
                    pass.bindTexture("DepthSampler", mainDepthView, depthClamp);
                });
            }

            //    color grade: final creative grade over the whole composited frame. Copy main ->
            //    gradeSrc (a pass can't read & write the same color attachment), then grade back into main.
            //    Runs LAST so it grades the fully-composited look (incl. all additive passes).
            if (gradeOn) {
                enc.copyTextureToTexture(mainColor, gradeSrc.texture, 0, 0, 0, 0, 0, w, h);
                runPass(enc, "DU color grade", mainColorView, DUPostPipelines.COLOR_GRADE, pass -> {
                    pass.setUniform("DUGrade", DUGradeData.slice());
                    pass.setUniform("DUView", DUViewData.slice());     // view ray for the sky-tint
                    pass.setUniform("DUGodray", DUGodrayData.slice()); // world sun dir for the sky-tint
                    pass.bindTexture("InSampler", gradeSrc.view, clamp);
                });
            }

            // debug: snapshot the final (graded) frame for the inspector (owned RGBA8 texture).
            enc.copyTextureToTexture(mainColor, presentCopy.texture, 0, 0, 0, 0, 0, w, h);
        } catch (Exception e) {
            failOnce("HDR composite failed; disabling look stack. The AFTER_LEVEL hook may need to move.", e);
        } finally {
            hdrPopulated = false;
        }
    }

    private static float getStillness(Matrix3f rel, Vec3 camPos) {
        float cosT = Math.clamp((rel.m00 + rel.m11 + rel.m22 - 1.0F) * 0.5F, -1.0F, 1.0F);
        float rotMot = 1.0F - cosT;                          // 0 still .. up to 2 (180°)
        double dx = camPos.x - ssrPrevX, dy = camPos.y - ssrPrevY, dz = camPos.z - ssrPrevZ;
        float posMot = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float motion = rotMot * 8.0F + posMot;
        return (float) Math.exp(-motion * DUSsrData.motionSensitivity);
    }

    /**
     * Underwater scene fog. When the camera eye is submerged, exponentially tint the whole
     * scene toward the underwater colour by view-space distance, so submerged terrain/entities fade into
     * the water with depth. This is the <i>volume</i> tint — the water surface boundary is shaded by the
     * Sodium terrain inject. Independent of the look-stack toggle; fail-soft (disables itself on error).
     */
    private static void renderUnderwaterFog(RenderLevelStageEvent.AfterLevel event) {
        if (!DUWaterData.enabled || !DUWaterData.underwaterFog || !DUWater.isEyeInWater()) {
            return;
        }
        if (DUIrisCompat.shaderPackActive()) {
            return;   // an Iris pack owns the frame; stand down
        }
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        GpuTexture mainColor = main.getColorTexture();
        GpuTextureView mainColorView = main.getColorTextureView();
        GpuTextureView mainDepthView = main.getDepthTextureView();
        if (mainColor == null || mainColorView == null || mainDepthView == null) {
            return;
        }
        int w = main.width;
        int h = main.height;
        if (w <= 0 || h <= 0) {
            return;
        }
        try {
            underwaterSrc.ensure(w, h);
            CameraRenderState cam = event.getLevelRenderState().cameraRenderState;
            DUViewData.set(cam.projectionMatrix, cam.viewRotationMatrix);
            DUViewData.upload();
            DUWaterFogData.upload();

            CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
            GpuSampler clamp = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            GpuSampler depthClamp = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);

            // Copy main -> scratch (a pass can't read & write the same color attachment), then fog into main.
            enc.copyTextureToTexture(mainColor, underwaterSrc.texture, 0, 0, 0, 0, 0, w, h);
            runPass(enc, "DU underwater", mainColorView, DUPostPipelines.UNDERWATER, pass -> {
                pass.setUniform("DUView", DUViewData.slice());
                pass.setUniform("DUWaterFog", DUWaterFogData.slice());
                pass.bindTexture("InSampler", underwaterSrc.view, clamp);
                pass.bindTexture("DepthSampler", mainDepthView, depthClamp);
            });
        } catch (Exception e) {
            if (!warnedUnderwater) {
                warnedUnderwater = true;
                DragonUniverse.LOGGER.warn("[Dragon Universe] Underwater fog pass failed; disabling it. The "
                        + "water surface and the rest of the stack are unaffected.", e);
            }
            DUWaterData.underwaterFog = false;
        }
    }

    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : (Math.min(v, 1.0F));
    }

    private static void failOnce(String message, Exception e) {
        if (!warnedOnce) {
            warnedOnce = true;
            DragonUniverse.LOGGER.warn("[Dragon Universe] {}", message, e);
        }
        setEnabled(false);
    }

    private interface PassSetup {
        void setup(RenderPass pass);
    }

    private static void runPass(CommandEncoder enc, String label, GpuTextureView output,
                                RenderPipeline pipeline, PassSetup setup) {
        try (RenderPass pass = enc.createRenderPass(
                () -> label, output, OptionalInt.empty(), null, OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            setup.setup(pass);
            pass.draw(0, 3);
        }
    }

    /** A lazily (re)allocated offscreen texture + view, recreated when the size changes. */
    private static final class PostTarget {
        private final String label;
        private final int usage;
        private GpuTexture texture;
        private GpuTextureView view;
        private int w = -1;
        private int h = -1;

        PostTarget(String label, int usage) {
            this.label = label;
            this.usage = usage;
        }

        void ensure(int width, int height) {
            if (texture != null && w == width && h == height) {
                return;
            }
            close();
            var device = RenderSystem.getDevice();
            texture = device.createTexture(() -> label, usage, TextureFormat.RGBA8, width, height, 1, 1);
            view = device.createTextureView(texture);
            w = width;
            h = height;
        }

        void close() {
            // Deferred close (see DUGpuTrash): a resize can reallocate this target while ImGuiMC still
            // holds the old view for its deferred endFrame draw. Park it instead of closing now.
            DUGpuTrash.retire(texture, view);
            texture = null;
            view = null;
            w = -1;
            h = -1;
        }
    }
}
