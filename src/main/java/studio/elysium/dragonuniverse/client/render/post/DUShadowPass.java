package studio.elysium.dragonuniverse.client.render.post;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import studio.elysium.dragonuniverse.DragonUniverse;
import studio.elysium.dragonuniverse.client.render.core.DUFrameData;
import studio.elysium.dragonuniverse.client.render.core.DUShadowData;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stage A of the DU shadow stack: a sun's-eye depth map of the scene. <b>No sampling, no softening,
 * no colour yet</b> — this only builds the map and exposes it for the debug viz so the light-space
 * projection can be verified in isolation before anything consumes it.
 *
 * <p><b>How the second geometry pass is scheduled (the architecturally risky bit).</b> Neo 26.1 +
 * Sodium with Iris dropped has no shadow pass and no MRT/second-pass render API. But Sodium's
 * {@code DefaultChunkRenderer.render(matrices, …)} takes its view-projection as a <i>parameter</i>,
 * so a sun's-eye pass is just that method re-invoked with a light-space matrix. We hook the TAIL of
 * the opaque terrain {@code render()} (via {@code DefaultChunkRendererMixin}), then re-invoke it
 * reflectively (the project never links Sodium types — same {@code @Coerce}+reflection discipline as
 * the normals/water mixins) with the <i>same</i> visible-section lists/camera/fog/sampler but a
 * light-space {@code ChunkRenderMatrices}. Sodium's {@code begin()} binds the main FBO itself, so we
 * can't pre-bind our target; instead, during the re-invoke, the terrain {@code begin}/{@code end}
 * hooks call {@link #onSodiumBegin()}/{@link #onSodiumEnd()} to swap to our own depth-only FBO for
 * the duration (exactly the begin-TAIL/end-HEAD window {@link DUNormalBuffer} already uses for MRT).</p>
 *
 * <p><b>Depth target the {@link DUHdrTarget} way:</b> MC 26.1's {@link TextureFormat} has no depth
 * format we can request, so we let the device make an {@code RGBA8} texture and redefine its level-0
 * storage as {@code GL_DEPTH_COMPONENT24} via raw GL, then hang it off our own GL framebuffer as the
 * depth attachment (colour writes discarded via {@code glDrawBuffer(GL_NONE)}). It's still samplable
 * as a normal {@code sampler2D} (returns depth in {@code .r}) for the viz — the same way the look
 * stack samples the main depth buffer.</p>
 *
 * <p><b>Reuses Sodium's terrain shader as-is</b> — only depth matters, so the injected normals output
 * (location 1) simply writes nowhere with no colour attachment. No shader changes needed for Stage A.</p>
 *
 * <p><b>Fail-soft:</b> any reflection/GL/compile failure trips {@link #failed}, logs once, and falls
 * back to the current no-DU-shadows behaviour. Off by default ({@link #enabled}).</p>
 *
 * <p>All access is on the render thread (the Sodium mixins during world render, the look stack at
 * {@code AfterLevel}) — single-threaded GL, no synchronization needed.</p>
 */
public final class DUShadowPass {
    private DUShadowPass() {}

    // ---- Tunables (debug panel; off by default until enabled) ------------------------------------
    public static boolean enabled = false;
    /** Shadow map resolution (square). Coverage vs. precision; power-of-two friendly. */
    public static int resolution = 4096;
    /** World-space half-extent (blocks) of the orthographic light frustum, centred on the camera. */
    public static float coverageBlocks = 192.0F;
    /**
     * Match the shadow distance to the player's render distance (Complementary's model: shadow distance is
     * a fixed radius capped at render distance, not literally "everything visible"). When on, {@link
     * #coverageBlocks} is overwritten each frame with {@code renderDistanceChunks·16}, clamped to
     * {@link #COVERAGE_MIN}..{@link #COVERAGE_MAX} so a huge render distance can't spread the map's texels
     * to mush. Off → the panel's manual coverage slider rules. The far edge is faded in the receiver so the
     * cutoff isn't a visible line. <b>Beyond ~24 chunks, texel density drops (uniform ortho, no warp); the
     * upgrade is a near-packing distortion warp — see the shadow handoff notes.</b>
     */
    public static boolean matchRenderDistance = true;
    /** Coverage clamp (blocks) when {@link #matchRenderDistance} is on. Below this, short distances waste res. */
    public static final float COVERAGE_MIN = 64.0F;
    /** Coverage clamp (blocks): 24 chunks. Above this a uniform ortho map gets too soft without a warp. */
    public static final float COVERAGE_MAX = 384.0F;
    /**
     * Vertical half-extent (blocks) of the shadow volume — how far above/below the camera occluders and
     * receivers are bracketed. Decoupled from {@link #coverageBlocks} so the light depth range fits the
     * thin ground+caster slab, not the full coverage cube. <b>Smaller = far more caster-vs-ground depth
     * contrast</b> (short casters resolve), at the cost of clipping geometry taller/lower than this from
     * the map. Raise if shadows clip on steep terrain / very tall casters; lower for flat terrain to
     * sharpen short-caster shadows. (Overhead-sun {@code far−near ≈ 2·(this+16)}.)
     */
    public static float depthHeight = 48.0F;
    /**
     * Light "eye" sign. {@code true} places the eye toward the sun looking back at the scene (the
     * physically-correct choice); flip in the panel if the verified map looks lit from the wrong side
     * (the one calibration the raw-map review gate exists to settle).
     */
    public static boolean eyeTowardSun = true;
    /**
     * Cast-side depth bias (GL polygon offset) applied while drawing the shadow map. The real lever for
     * foliage acne: alpha-tested leaves (the cutout pass) self-shadow badly, so push occluders back a
     * touch. {@code units} is the constant term, {@code factor} the slope term.
     */
    public static float leafBiasUnits = 0.0F;
    public static float leafBiasFactor = 0.0F;
    /**
     * Bug A fix: gather the shadow occluders into the map from an own <b>camera-centred {@code coverage}
     * box</b> (all directions), instead of reusing Sodium's <i>camera-frustum</i> visible set. The light
     * volume is a box centred on the camera, so "which sections cast" is view-independent — every built
     * section within {@code coverage} of the camera, regardless of where the player looks. Off → falls
     * back to the camera's visible set (the old behaviour: occluders outside the view don't cast). If the
     * gather reflection ever fails it silently falls back too (shadows keep working, just camera-limited).
     */
    public static boolean ownVisibleSet = true;

    // ---- Raw GL enums (mirror DUNormalBuffer's style; keep LWJGL constants out of call sites) -----
    private static final int GL_TEXTURE_2D = 3553;
    private static final int GL_DEPTH_COMPONENT24 = 0x81A6;
    private static final int GL_DEPTH_COMPONENT = 0x1902;
    private static final int GL_UNSIGNED_INT = 0x1405;
    private static final int GL_NEAREST = 9728;
    private static final int GL_CLAMP_TO_EDGE = 33071;
    private static final int GL_TEXTURE_MIN_FILTER = 10241;
    private static final int GL_TEXTURE_MAG_FILTER = 10240;
    private static final int GL_TEXTURE_WRAP_S = 10242;
    private static final int GL_TEXTURE_WRAP_T = 10243;

    private static final int GL_FRAMEBUFFER = 0x8D40;
    private static final int GL_FRAMEBUFFER_BINDING = 0x8CA6;
    private static final int GL_DEPTH_ATTACHMENT = 0x8D00;
    private static final int GL_FRAMEBUFFER_COMPLETE = 0x8CD5;
    private static final int GL_NONE = 0;
    private static final int GL_DEPTH_BUFFER_BIT = 0x100;
    private static final int GL_SCISSOR_TEST = 0x0C11;
    private static final int GL_BLEND = 0x0BE2;
    private static final int GL_CURRENT_PROGRAM = 0x8B8D;
    private static final int GL_COLOR = 0x1800;          // glClearBufferfv buffer selector
    private static final int GL_DEPTH = 0x1801;
    private static final float[] TINT_COLOR_CLEAR = { 0.0F, 0.0F, 0.0F, 0.0F };
    private static final float[] TINT_DEPTH_CLEAR = { 1.0F };

    private static final int USAGE_ALL = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC
            | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT;

    // ---- Target (own depth-only FBO + samplable depth texture) -----------------------------------
    private static GpuTexture texture;
    private static GpuTextureView view;
    private static int texId = 0;
    private static int fbo = 0;
    private static int allocRes = -1;

    // ---- Per-pass GL state save/restore -----------------------------------------------------------
    private static int savedFbo = 0;
    private static int savedVpW = 0;
    private static int savedVpH = 0;
    private static boolean savedScissor = false;   // GL_SCISSOR_TEST enable state to restore after the draw

    // ---- Reflection into Sodium (resolved once, lazily) -------------------------------------------
    private static Method renderMethod;        // DefaultChunkRenderer.render(...8 args...)
    private static Constructor<?> matricesCtor; // ChunkRenderMatrices(Matrix4fc, Matrix4fc)
    private static Method matricesProj;         // ChunkRenderMatrices.projection()
    private static Method matricesModelView;    // ChunkRenderMatrices.modelView()
    private static Method isTranslucentMethod;  // TerrainRenderPass.isTranslucent()

    // ---- Reflection for the own light-space gather (Bug A) ----------------------------------------
    // Builds a view-independent SortedRenderLists of every built section in the coverage box into our OWN
    // ChunkRenderList instances. We deliberately do NOT use Sodium's OcclusionCuller (it writes shared
    // RenderSection visibility state) nor its SectionCollector (its visit() resets/refills the region's
    // SHARED ChunkRenderList — region.getRenderList() — which Sodium's live camera lists still point at,
    // so it corrupts the cutout/translucent draw mid-frame: water/chunks vanish). Building our own lists
    // touches only read-only section/region accessors, so Sodium's camera render is untouched.
    private static Method swrInstanceNullable;  // SodiumWorldRenderer.instanceNullable()
    private static Field rsmField;              // SodiumWorldRenderer.renderSectionManager
    private static Field sectionMapField;       // RenderSectionManager.sectionByPosition (Long2ReferenceMap)
    private static Constructor<?> chunkListCtor; // ChunkRenderList(RenderRegion)  — our own instances
    private static Method chunkListReset;       // ChunkRenderList.reset(int frame, boolean sorted)
    private static Method chunkListAdd;         // ChunkRenderList.add(int sectionIndex, int flags)
    private static Constructor<?> sortedListsCtor; // SortedRenderLists(ObjectArrayList) — package-private
    private static Constructor<?> backingListCtor; // the ctor's actual ObjectArrayList param type (no-arg)
    private static Method backingListAdd;          // that ObjectArrayList's add(Object)
    private static Method secCenterX, secCenterY, secCenterZ, secGetRegion, secGetIndex, secGetFlags;
    private static Method regionGetCachedBatch;    // RenderRegion.getCachedBatch(TerrainRenderPass)
    private static Method batchClear;              // MultiDrawBatch.clear() — resets size + isFilled
    private static List<Object> shadowRegions;     // regions in this frame's list (for per-pass batch invalidation)
    private static boolean gatherReady = false;
    private static boolean gatherFailed = false;
    private static boolean gatherWarned = false;
    private static int shadowFrameCounter = 0;
    private static Object shadowRenderLists;    // cached own list for this frame (null = use camera list)
    private static boolean listBuiltThisFrame = false;

    // ---- State ------------------------------------------------------------------------------------
    private static boolean active = false;        // true while re-invoking render() from the sun's POV
    private static boolean clearedThisFrame = false; // depth cleared once per frame (before the opaque pass)
    private static boolean mapReady = false;       // the map has been built at least once (gates the receiver)
    private static boolean failed = false;
    private static boolean warned = false;

    // ---- Stage D (colored shadows) state ----------------------------------------------------------
    private static boolean targetIsColor = false;       // true while the TRANSLUCENT re-invoke is active (pick the tint FBO)
    private static boolean colorClearedThisFrame = false; // tint depth+color cleared once per frame
    private static boolean savedBlend = false;           // GL_BLEND enable to restore after the tint draw
    private static boolean coloredMapReady = false;      // tint map built at least once (gates the receiver)
    private static boolean coloredFailed = false;        // Stage D self-disable; never trips the opaque `failed`
    private static boolean coloredWarned = false;

    // The EXACT light view-projection + sun direction used to draw the map this frame. The Stage-B
    // receiver MUST reuse these (not rebuild from the live sun), or producer/receiver drift by the
    // sun's per-frame motion and shadows swim. Refreshed every shadow render.
    private static final Matrix4f lastLightVP = new Matrix4f();
    private static final Vector3f lastSunDir = new Vector3f(0.0F, 1.0F, 0.0F);

    /** True while the sun's-eye re-invoke is running — the terrain begin/end + render hooks key off this. */
    public static boolean isActive() {
        return active;
    }

    public static boolean isEnabled() {
        return enabled && !failed;
    }

    /** The shadow depth map view, for the debug viz / Target Inspector. Null until built once. */
    public static GpuTextureView depthView() {
        return view;
    }

    /** The light view-projection actually used to draw the map this frame (the receiver must reuse it). */
    public static Matrix4f lastLightViewProj() {
        return lastLightVP;
    }

    /** The sun direction actually used to draw the map this frame (for the receiver's N·L gate). */
    public static Vector3f lastSunDir() {
        return lastSunDir;
    }

    /** True once a map has been built — gates the Stage-B receiver so it never samples an unbuilt map. */
    public static boolean hasMap() {
        return mapReady && view != null;
    }

    // ---- Stage D (colored shadows) accessors ------------------------------------------------------

    /** True while the TRANSLUCENT re-invoke is running (the begin/end hooks bind the tint FBO + set du_ShadowPass). */
    public static boolean isTranslucentActive() {
        return active && targetIsColor;
    }

    /** Stage D master gate (producer + receiver). Requires the opaque producer (Stage A) on — colored
     *  shadows layer on the opaque map and reuse its resolution/matrices. Off if Stage D self-disabled. */
    public static boolean isColoredEnabled() {
        return DUShadowData.coloredEnabled && isEnabled() && !coloredFailed;
    }

    /** The translucent-occluder depth map view (Stage D), for the receiver + inspector. Null until built. */
    public static GpuTextureView coloredDepthView() {
        return DUShadowColorTarget.depthView();
    }

    /** The translucent-occluder tint view (rgb=tint, a=opacity), for the receiver + inspector. Null until built. */
    public static GpuTextureView coloredTintView() {
        return DUShadowColorTarget.colorView();
    }

    /** True once a tint map has been built this run — gates the receiver's colored sample. */
    public static boolean hasColoredMap() {
        return coloredMapReady && DUShadowColorTarget.ready();
    }

    /** Reset the per-frame clear guard. Called once per frame at {@code AfterLevel} (look-stack independent). */
    public static void beginFrame() {
        clearedThisFrame = false;
        colorClearedThisFrame = false;
        listBuiltThisFrame = false;
        shadowRenderLists = null;
        shadowRegions = null;
        // Shadow distance = render distance (capped), Complementary-style. Drives both the lateral ortho
        // extent AND (via extHoriz in lightHalfDepth) the depth bracket, so geometry out to this radius is
        // drawn into the map instead of being far-clipped to the cleared value (which read as RED in the
        // receiver debug = no occluder = no shadow past the old 64-block default).
        if (matchRenderDistance) {
            int chunks = Minecraft.getInstance().options.getEffectiveRenderDistance();
            coverageBlocks = Math.clamp(chunks * 16.0F, COVERAGE_MIN, COVERAGE_MAX);
        }
    }

    /**
     * Re-invoke Sodium terrain rendering from the sun's POV into the shadow depth map. Called from the
     * opaque terrain {@code render()} TAIL (once per frame) with that call's own arguments — we pass
     * them straight through, swapping only the matrices for a light-space orthographic projection.
     */
    public static void renderFromSun(Object renderer, Object originalMatrices, Object commandList,
                                     Object renderLists, Object pass, Object camera, Object fog, Object sampler) {
        if (failed) {
            return;
        }
        try {
            ensureReflection(renderer);
            ensureTarget();
            if (texture == null || fbo == 0) {
                return;
            }
            // Capture the EXACT matrices that rendered the terrain depth (Sodium's own ChunkRenderMatrices)
            // so the receiver reconstructs world position consistently — this is what kills the shadow bob.
            DUShadowData.captureCameraBob(
                    (Matrix4fc) matricesProj.invoke(originalMatrices),
                    (Matrix4fc) matricesModelView.invoke(originalMatrices));
            // Build the light matrices once and cache the combined VP + sun so the receiver reuses the
            // exact same projection (no producer/receiver drift). Called per non-translucent pass
            // (opaque + cutout); the cached value is identical within a frame.
            Vector3f sun = currentSun();
            Matrix4f proj = lightProjection(sun);
            Matrix4f viewMat = lightModelView(sun);
            lastLightVP.set(proj).mul(viewMat);
            lastSunDir.set(sun);
            Object matrices = matricesCtor.newInstance(proj, viewMat);
            // Bug A: swap the camera's frustum-culled list for our own camera-centred coverage-box list
            // (built once per frame, reused for the opaque + cutout re-invokes), so occluders behind/beside
            // the camera still cast. Falls back to the camera list if disabled or the gather can't resolve.
            Object listsToUse = renderLists;
            boolean usedOwnList = false;
            if (ownVisibleSet) {
                if (!listBuiltThisFrame) {
                    shadowRenderLists = buildShadowList();
                    listBuiltThisFrame = true;
                }
                if (shadowRenderLists != null) {
                    listsToUse = shadowRenderLists;
                    usedOwnList = true;
                    // Invalidate THIS pass's cached draw batch on each of our regions so render() rebuilds it
                    // from our box list (else it reuses the camera's already-filled batch and ignores our
                    // list — that was "Bug A back").
                    clearShadowBatches(pass);
                }
            }
            active = true;
            try {
                // Sodium's begin()/end() (during this nested call) route into onSodiumBegin/onSodiumEnd,
                // which bind our depth-only FBO for the draw and restore the main one after.
                renderMethod.invoke(renderer, matrices, commandList, listsToUse, pass, camera, fog,
                        Boolean.FALSE, sampler);
            } finally {
                active = false;
                // CRITICAL: the re-invoke just refilled those batches with OUR box geometry. Sodium caches
                // batches ACROSS frames (no per-frame clear while the camera is still), so leaving box
                // content in them makes the camera draw it as terrain next frame -> chunks/water "de-render"
                // until you move (which triggers Sodium's own list rebuild + clear). Re-invalidate so the
                // camera rebuilds each batch from its OWN intact list on its next pass.
                if (usedOwnList) {
                    clearShadowBatches(pass);
                }
            }
            mapReady = true;
        } catch (Throwable t) {
            failOnce("sun's-eye shadow pass threw", t);
            // Make sure a throw mid-invoke never leaves us redirected.
            if (active) {
                active = false;
                try {
                    onSodiumEnd();
                } catch (Throwable ignored) {
                    // restoring is best-effort; the outer catch already disabled us
                }
            }
        }
    }

    /**
     * Stage D: re-invoke Sodium's TRANSLUCENT terrain pass from the sun's POV into the second target
     * ({@link DUShadowColorTarget}: nearest translucent depth + tint). Called from the translucent
     * {@code render()} TAIL (the opaque + cutout {@link #renderFromSun} already ran this frame, so the light
     * matrices + own-visible-set list are current). Blend off + depth-write on → the front-most translucent
     * occluder wins. The {@code du_ShadowPass} flag (bound at the nested translucent {@code begin}) makes the
     * injected shader emit a flat tint instead of the full water shading.
     *
     * <p>Fail-soft in its OWN scope: any failure self-disables colored shadows ({@link #coloredFailed}) and
     * never trips the opaque {@link #failed}, so Stage D going down can't take A/B/C with it.</p>
     */
    public static void renderTranslucentFromSun(Object renderer, Object commandList, Object renderLists,
                                                Object pass, Object camera, Object fog, Object sampler) {
        if (failed || coloredFailed || !DUShadowData.coloredEnabled) {
            return;
        }
        try {
            ensureReflection(renderer);
            int res = Math.max(256, resolution);
            if (!DUShadowColorTarget.ensure(res)) {
                coloredFailed = true;
                return;
            }
            // Rebuild the light matrices identically to renderFromSun (same frame → same values), so the tint
            // map shares the EXACT projection the opaque map + receiver use. No new capture/cache needed.
            Vector3f sun = currentSun();
            Object matrices = matricesCtor.newInstance(lightProjection(sun), lightModelView(sun));
            // Reuse the opaque/cutout own-visible-set box list (built once this frame) so translucent casts are
            // view-independent like the opaque map, with the same batch-borrow invalidation.
            // KNOWN LIMITATION: this draws flat fluid WATER (shared index) into the tint map fine, but sorted
            // translucent BLOCKS (stained glass) submit a draw whose fragments never rasterize in this re-invoke
            // — a Sodium translucent-geometry quirk we couldn't crack with code probes (FBO/list/index/cull/
            // depth all ruled out). Needs a RenderDoc frame capture to diagnose; see the Stage D notes.
            Object listsToUse = renderLists;
            boolean usedOwnList = false;
            if (ownVisibleSet) {
                if (!listBuiltThisFrame) {
                    shadowRenderLists = buildShadowList();
                    listBuiltThisFrame = true;
                }
                if (shadowRenderLists != null) {
                    listsToUse = shadowRenderLists;
                    usedOwnList = true;
                    clearShadowBatches(pass);
                }
            }
            active = true;
            targetIsColor = true;
            try {
                renderMethod.invoke(renderer, matrices, commandList, listsToUse, pass, camera, fog,
                        Boolean.FALSE, sampler);
            } finally {
                active = false;
                targetIsColor = false;
                if (usedOwnList) {
                    clearShadowBatches(pass);
                }
            }
            coloredMapReady = true;
        } catch (Throwable t) {
            coloredFailOnce("translucent (colored) shadow pass threw", t);
            if (active) {
                active = false;
                targetIsColor = false;
                try {
                    onSodiumEnd();
                } catch (Throwable ignored) {
                    // best-effort restore; colored shadows already disabled
                }
            }
        }
    }

    /**
     * Invalidate the cached per-pass draw batch on each of our shadow regions, so the next consumer of that
     * batch rebuilds it from its own {@code ChunkRenderList}. Called BOTH before our re-invoke (so render()
     * fills the batch from our box list) AND after (so the camera, which caches batches across frames,
     * rebuilds from its own intact list next frame instead of drawing our box geometry as terrain).
     * Best-effort per region — a clear failure just leaves a region's batch to be rebuilt by Sodium anyway.
     */
    private static void clearShadowBatches(Object pass) {
        if (shadowRegions == null || regionGetCachedBatch == null) {
            return;
        }
        for (Object region : shadowRegions) {
            try {
                Object batch = regionGetCachedBatch.invoke(region, pass);
                if (batch != null) {
                    batchClear.invoke(batch);
                }
            } catch (Throwable ignored) {
                // best-effort; Sodium will rebuild this region's batch from its own list regardless
            }
        }
    }

    /** Reflectively read {@code TerrainRenderPass.isTranslucent()}; on failure, treat as translucent (skip). */
    public static boolean isTranslucent(Object pass) {
        if (pass == null) {
            return true;
        }
        try {
            if (isTranslucentMethod == null) {
                isTranslucentMethod = pass.getClass().getMethod("isTranslucent");
            }
            return (boolean) isTranslucentMethod.invoke(pass);
        } catch (Throwable t) {
            return true;
        }
    }

    // ---- Sodium begin/end redirect (called from SodiumShaderChunkRendererMixin while active) ------

    /**
     * At the terrain {@code begin()} TAIL during the sun's-eye re-invoke: save the main FBO + viewport,
     * bind our depth-only FBO, size the viewport to the shadow map, and clear depth. The light-space
     * geometry then draws its depth into our map instead of the main target.
     */
    public static void onSodiumBegin() {
        // Save the bound FBO (raw read; doesn't touch the cache), then change binding/viewport/depth
        // THROUGH GlStateManager so Blaze3D's + Sodium's cached state stays in sync. Binding our FBO with
        // raw GL would desync the cache and silently redirect every later pass (cutout/translucent/clouds)
        // to this depth-only target — i.e. make them vanish. The viewport to restore is the main target's
        // (querying GL_VIEWPORT here throws a spurious INVALID_ENUM on this driver, so read the dims instead).
        savedFbo = GL11C.glGetInteger(GL_FRAMEBUFFER_BINDING);
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        savedVpW = main.width;
        savedVpH = main.height;
        // Stage D: the translucent re-invoke targets the tint FBO (depth + color); everything else the
        // depth-only opaque FBO. Both are the same resolution (allocRes).
        int targetFbo = targetIsColor ? DUShadowColorTarget.fbo() : fbo;
        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, targetFbo);
        GlStateManager._viewport(0, 0, allocRes, allocRes);
        // Disable the scissor test for the duration: MC/Sodium leave it set to the MAIN target's rect, and
        // both glClear and the terrain draws honour it. Our shadow map is larger than the main framebuffer,
        // so an inherited scissor clips the clear + the geometry to a main-sized sub-rectangle — leaving the
        // rest of the map a stale/uncleared "plate" (a hard, camera-relative seam, since the map is
        // camera-centred). Read the enable flag raw (glIsEnabled doesn't desync the cache like the
        // GL_VIEWPORT query does); toggle THROUGH GlStateManager so Blaze3D's cache stays in sync.
        savedScissor = GL11C.glIsEnabled(GL_SCISSOR_TEST);
        if (savedScissor) {
            GlStateManager._disableScissorTest();
        }
        GlStateManager._depthMask(true);         // write depth; for the tint pass this overrides Sodium's
                                                 // translucent depthMask(false) so the nearest surface wins.
        if (targetIsColor) {
            // Tint pass: blend OFF (raw nearest tint, not alpha-blended), clear depth→1 + color→0 once/frame.
            savedBlend = GL11C.glIsEnabled(GL_BLEND);
            if (savedBlend) {
                GlStateManager._disableBlend();
            }
            if (!colorClearedThisFrame) {
                // Scissor is already disabled above; depth-mask is on. Clear the two attachments directly
                // (the DUWaterGbuffer idiom) — color0→0, depth→1 — without touching global clear state.
                GL30C.glClearBufferfv(GL_COLOR, 0, TINT_COLOR_CLEAR);
                GL30C.glClearBufferfv(GL_DEPTH, 0, TINT_DEPTH_CLEAR);
                colorClearedThisFrame = true;
            }
            // No polygon offset on the tint pass (translucent surfaces are thin; the receiver biases instead).
        } else {
            // Opaque/cutout depth pass: clear once (opaque clears, cutout accumulates with LEQUAL), cast bias.
            if (!clearedThisFrame) {
                GlStateManager._clear(GL_DEPTH_BUFFER_BIT);
                clearedThisFrame = true;
            }
            GlStateManager._polygonOffset(leafBiasFactor, leafBiasUnits);
            GlStateManager._enablePolygonOffset();
        }
    }

    /** At the terrain {@code end()} HEAD during the re-invoke: restore the main FBO + viewport (cached). */
    public static void onSodiumEnd() {
        if (targetIsColor) {
            // Restore Sodium's translucent defaults: blend on, no depth write.
            if (savedBlend) {
                GlStateManager._enableBlend();
            }
            GlStateManager._depthMask(false);
        } else {
            GlStateManager._disablePolygonOffset();
        }
        if (savedScissor) {
            GlStateManager._enableScissorTest();   // restore whatever MC/Sodium had (box untouched)
        }
        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, savedFbo);
        GlStateManager._viewport(0, 0, savedVpW, savedVpH);
    }

    /**
     * Bind the Stage-D flags onto Sodium's currently-bound terrain program (called at the translucent
     * {@code begin} TAIL, same idiom as {@code DUWater.bindWaterUniforms}). {@code du_ShadowPass} self-sets
     * from {@link #isTranslucentActive()} — 1 inside the nested tint re-invoke, 0 for the normal translucent
     * draw — so the injected shader emits a flat tint only during the shadow pass and is inert otherwise.
     */
    public static void bindColoredShadowUniforms() {
        int prog = GL11C.glGetInteger(GL_CURRENT_PROGRAM);
        if (prog == 0) {
            return;
        }
        uniform1f(prog, "du_ShadowPass", isTranslucentActive() ? 1.0F : 0.0F);
        uniform1f(prog, "du_WaterColoredShadows", DUShadowData.waterColoredShadows ? 1.0F : 0.0F);
        uniform1f(prog, "du_ColoredWaterOpacity", DUShadowData.coloredWaterOpacity);
    }

    private static void uniform1f(int prog, String name, float v) {
        int loc = org.lwjgl.opengl.GL20C.glGetUniformLocation(prog, name);
        if (loc >= 0) {
            org.lwjgl.opengl.GL20C.glUniform1f(loc, v);
        }
    }

    private static void coloredFailOnce(String detail, Throwable t) {
        coloredFailed = true;
        if (!coloredWarned) {
            coloredWarned = true;
            DragonUniverse.LOGGER.warn("[Dragon Universe] Colored shadows (Stage D) failed ({}); disabling "
                    + "them only — opaque shadows + the rest of the stack are unaffected.", detail, t);
        }
    }

    // ---- Light-space matrices (camera-relative, matching Sodium's camera-relative geometry) -------

    /** Small near margin (blocks) so a caster at the box's sun-facing edge doesn't clip the near plane. */
    private static final float LIGHT_NEAR = 1.0F;

    /** Section-straddle margin (blocks) added to the gather/depth extents so boundary sections still cast. */
    private static final float LIGHT_MARGIN = 16.0F;

    /** Horizontal (X/Z) half-extent of the gathered occluder box: the coverage + section margin. */
    private static float extHoriz() {
        return Math.max(1.0F, coverageBlocks) + LIGHT_MARGIN;
    }

    /** Vertical (Y) half-extent of the gathered occluder box: the (tunable) shadow-slab height + margin. */
    private static float extVert() {
        return Math.max(1.0F, depthHeight) + LIGHT_MARGIN;
    }

    /**
     * Half-depth of the gathered occluder box along the sun (view) axis. The box is camera-centred with a
     * DECOUPLED vertical extent ({@link #extVert}) much smaller than its horizontal extent ({@link #extHoriz}),
     * so the depth range brackets the thin ground+caster slab rather than the full coverage cube. The box's
     * support along the unit sun is {@code extHoriz·(|x|+|z|) + extVert·|y|}: an overhead sun collapses to
     * {@code extVert} (tight range → high caster-vs-ground contrast); a grazing sun is dominated by
     * {@code extHoriz·(|x|+|z|)} (inherent — reduce coverage there).
     */
    private static float lightHalfDepth(Vector3f sun) {
        return extHoriz() * (Math.abs(sun.x) + Math.abs(sun.z)) + extVert() * Math.abs(sun.y);
    }

    /**
     * Orthographic light projection. Lateral is the {@code coverageBlocks} half-extent box (the texel-snap
     * is tuned for it); depth is tightened to exactly bracket the occluder box: {@code [LIGHT_NEAR,
     * LIGHT_NEAR + 2·halfDepth]}. <b>Bug-B fix:</b> the old {@code far = 4·coverage} with the eye pushed
     * {@code 1.5·coverage} back left the geometry in the middle half of the range, so a few-block caster's
     * depth gap was a tiny fraction of [0,1] and the receiver bias swallowed short casters. Filling the
     * range with the box roughly doubles the caster-vs-ground contrast (overhead sun) and shrinks the
     * world-space size of the receiver's [0,1]-unit biases.
     */
    private static Matrix4f lightProjection(Vector3f sun) {
        float cov = Math.max(1.0F, coverageBlocks);
        float far = LIGHT_NEAR + 2.0F * lightHalfDepth(sun);
        return new Matrix4f().ortho(-cov, cov, -cov, cov, LIGHT_NEAR, far);
    }

    /** The current sun direction (shared with god rays), normalized and degeneracy-guarded. */
    private static Vector3f currentSun() {
        Vector3f sun = new Vector3f(DUFrameData.sunDir);
        if (sun.lengthSquared() < 1.0e-6F) {
            sun.set(0.0F, 1.0F, 0.0F);
        }
        return sun.normalize();
    }

    /**
     * Light view matrix in camera-relative space (camera at the origin, exactly how Sodium feeds
     * terrain vertices). Eye sits along the sun direction; the frustum looks back at the camera.
     *
     * <p><b>Texel snapping</b> stabilizes the frustum against sub-texel camera motion (the cure for
     * shadow "swim"). The ortho frustum is centred on the camera, so it slides continuously over the
     * world and shadow edges crawl as you move. We quantize the camera's world position (the frustum
     * centre) to the shadow-texel grid along the light's XY axes and apply the remainder as a light-space
     * translation, so the frustum only ever jumps in whole-texel steps. The eye sits along the sun (⊥ the
     * light's right/up axes), so it drops out of the projection — the camera world pos is the only phase.</p>
     */
    private static Matrix4f lightModelView(Vector3f sun) {
        // Eye just outside the box on the sun side: the box centre (camera/origin) sits at depth
        // LIGHT_NEAR + halfDepth, so the box spans exactly [LIGHT_NEAR, LIGHT_NEAR + 2·halfDepth] — matching
        // lightProjection(). (d is along the sun, ⊥ the light's right/up axes, so it doesn't affect the
        // texel snapping below.)
        float d = LIGHT_NEAR + lightHalfDepth(sun);
        float s = eyeTowardSun ? d : -d;
        float ex = sun.x * s, ey = sun.y * s, ez = sun.z * s;
        // up: avoid degeneracy when the sun is near-vertical
        float ux = 0.0F, uy = 1.0F, uz = 0.0F;
        if (Math.abs(sun.y) > 0.99F) {
            ux = 0.0F; uy = 0.0F; uz = 1.0F;
        }
        Matrix4f view = new Matrix4f().lookAt(ex, ey, ez, 0.0F, 0.0F, 0.0F, ux, uy, uz);

        // World-space light right/up axes = rows 0/1 of the view rotation.
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        double fx = cam.x * view.m00() + cam.y * view.m10() + cam.z * view.m20();   // camera · right
        double fy = cam.x * view.m01() + cam.y * view.m11() + cam.z * view.m21();   // camera · up
        double texel = (2.0 * Math.max(1.0F, coverageBlocks)) / Math.max(256, resolution);
        // Remainder of the camera projection w.r.t. the texel grid — add it back so the world maps to a
        // texel-quantized origin (the projection then only changes in whole-texel steps).
        float sx = (float) (fx - Math.round(fx / texel) * texel);
        float sy = (float) (fy - Math.round(fy / texel) * texel);
        return new Matrix4f().translation(sx, sy, 0.0F).mul(view);
    }

    // ---- Reflection + target lifecycle ------------------------------------------------------------

    private static void ensureReflection(Object renderer) throws Exception {
        if (renderMethod != null && matricesCtor != null) {
            return;
        }
        // render(...) — the only 8-arg method named "render" on DefaultChunkRenderer.
        Method found = null;
        for (Method m : renderer.getClass().getMethods()) {
            if (m.getName().equals("render") && m.getParameterCount() == 8) {
                found = m;
                break;
            }
        }
        if (found == null) {
            throw new NoSuchMethodException("DefaultChunkRenderer.render(8 args) not found");
        }
        found.setAccessible(true);
        renderMethod = found;

        Class<?> matricesClass = Class.forName(
                "net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices");
        // The record's canonical (Matrix4fc, Matrix4fc) constructor + its accessors.
        Constructor<?> ctor = matricesClass.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        matricesCtor = ctor;
        matricesProj = matricesClass.getMethod("projection");
        matricesModelView = matricesClass.getMethod("modelView");
    }

    // ---- Own light-space section gather (Bug A) ---------------------------------------------------

    /** Resolve the Sodium handles for the own-visible-set gather, once. Sets {@link #gatherReady}. */
    private static void ensureGatherReflection() throws Exception {
        if (gatherReady) {
            return;
        }
        String P = "net.caffeinemc.mods.sodium.client.render.";
        Class<?> swrClass = Class.forName(P + "SodiumWorldRenderer");
        Class<?> rsmClass = Class.forName(P + "chunk.RenderSectionManager");
        Class<?> sectionClass = Class.forName(P + "chunk.RenderSection");
        Class<?> regionClass = Class.forName(P + "chunk.region.RenderRegion");
        Class<?> chunkListClass = Class.forName(P + "chunk.lists.ChunkRenderList");
        Class<?> sortedListsClass = Class.forName(P + "chunk.lists.SortedRenderLists");
        Class<?> passClass = Class.forName(P + "chunk.terrain.TerrainRenderPass");
        Class<?> batchClass = Class.forName("net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch");

        swrInstanceNullable = swrClass.getMethod("instanceNullable");
        rsmField = swrClass.getDeclaredField("renderSectionManager");
        rsmField.setAccessible(true);
        sectionMapField = rsmClass.getDeclaredField("sectionByPosition");
        sectionMapField.setAccessible(true);

        chunkListCtor = chunkListClass.getConstructor(regionClass);
        chunkListReset = chunkListClass.getMethod("reset", int.class, boolean.class);
        chunkListAdd = chunkListClass.getMethod("add", int.class, int.class);
        // SortedRenderLists(ObjectArrayList<ChunkRenderList>) is package-private. Resolve by ARITY, not by
        // exact param type: the fastutil ObjectArrayList on our classpath could be a different Class than
        // the one Sodium's ctor was compiled against (relocation/classloader), so a typed lookup could miss
        // — and feeding it the wrong list type would fail too. There's exactly one ctor, so the single
        // 1-arg one is unambiguous; build the backing list from the ctor's OWN param type so the argument
        // always matches whatever ObjectArrayList Sodium expects.
        Constructor<?> srl = null;
        for (Constructor<?> c : sortedListsClass.getDeclaredConstructors()) {
            if (c.getParameterCount() == 1) {
                srl = c;
                break;
            }
        }
        if (srl == null) {
            throw new NoSuchMethodException("SortedRenderLists(1-arg) constructor not found");
        }
        srl.setAccessible(true);
        sortedListsCtor = srl;
        Class<?> backingClass = srl.getParameterTypes()[0];   // the exact ObjectArrayList class to feed it
        backingListCtor = backingClass.getDeclaredConstructor();
        backingListCtor.setAccessible(true);
        backingListAdd = backingClass.getMethod("add", Object.class);

        secCenterX = sectionClass.getMethod("getCenterX");
        secCenterY = sectionClass.getMethod("getCenterY");
        secCenterZ = sectionClass.getMethod("getCenterZ");
        secGetRegion = sectionClass.getMethod("getRegion");
        secGetIndex = sectionClass.getMethod("getSectionIndex");
        secGetFlags = sectionClass.getMethod("getFlags");
        regionGetCachedBatch = regionClass.getMethod("getCachedBatch", passClass);
        batchClear = batchClass.getMethod("clear");
        gatherReady = true;
    }

    /**
     * Build a {@code SortedRenderLists} of every built section whose world centre lies within the
     * camera-centred {@code coverage} box (all directions), into <b>our own</b> {@code ChunkRenderList}
     * instances grouped by region. View-independent by construction (no camera frustum), and — crucially —
     * it never calls {@code region.getRenderList()}, so Sodium's live per-region lists (which its camera
     * {@code SortedRenderLists} still references for the cutout/translucent draw) are left untouched.
     *
     * <p>{@code reset(frame, true)} marks the list "already sorted" so {@code add()} writes section indices
     * straight into the geometry array {@code render()} iterates — no {@code prepareForRender} needed
     * (shadow draw order is irrelevant). Returns {@code null} (→ fall back to the camera list) on failure.</p>
     */
    private static Object buildShadowList() {
        if (gatherFailed) {
            return null;
        }
        try {
            ensureGatherReflection();
            Object swr = swrInstanceNullable.invoke(null);
            if (swr == null) {
                return null;
            }
            Object rsm = rsmField.get(swr);
            if (rsm == null) {
                return null;
            }
            Map<?, ?> sections = (Map<?, ?>) sectionMapField.get(rsm);   // Long2ReferenceMap implements Map
            if (sections == null || sections.isEmpty()) {
                return null;
            }
            Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().position();
            // Box half-extents: horizontal = coverage, vertical = the (smaller) shadow-slab height, plus a
            // section's margin so straddling sections still cast. Must match lightHalfDepth()'s extents so the
            // depth range brackets exactly what we gather (no clip, no waste).
            double extH = extHoriz();
            double extV = extVert();
            int frame = ++shadowFrameCounter;

            // One own ChunkRenderList per region touched (identity-keyed: regions are shared singletons).
            IdentityHashMap<Object, Object> regionLists = new IdentityHashMap<>();
            for (Object sec : sections.values()) {
                if (sec == null) continue;
                // Cheap box reject first (short-circuits per axis) — most loaded sections are out of the box.
                double dx = (int) secCenterX.invoke(sec) - cam.x;
                if (dx < -extH || dx > extH) continue;
                double dy = (int) secCenterY.invoke(sec) - cam.y;
                if (dy < -extV || dy > extV) continue;
                double dz = (int) secCenterZ.invoke(sec) - cam.z;
                if (dz < -extH || dz > extH) continue;
                Object region = secGetRegion.invoke(sec);
                if (region == null) continue;                 // not uploaded to a region yet
                Object list = regionLists.get(region);
                if (list == null) {
                    list = chunkListCtor.newInstance(region);
                    chunkListReset.invoke(list, frame, true);  // sorted=true -> add() fills the iterated array
                    regionLists.put(region, list);
                }
                chunkListAdd.invoke(list, (int) secGetIndex.invoke(sec), (int) secGetFlags.invoke(sec));
            }
            if (regionLists.isEmpty()) {
                return null;
            }
            // Remember the regions so renderFromSun can invalidate their per-pass cached draw batches
            // before each re-invoke (else render() reuses the camera's filled batch and ignores our list).
            shadowRegions = new ArrayList<>(regionLists.keySet());
            Object lists = backingListCtor.newInstance();   // Sodium's own ObjectArrayList type
            for (Object list : regionLists.values()) {
                backingListAdd.invoke(lists, list);
            }
            return sortedListsCtor.newInstance(lists);
        } catch (Throwable t) {
            gatherFailed = true;
            if (!gatherWarned) {
                gatherWarned = true;
                DragonUniverse.LOGGER.warn("[Dragon Universe] Shadow own-visible-set gather failed; falling "
                        + "back to the camera's visible set (shadows keep working, but occluders outside the "
                        + "view won't cast).", t);
            }
            return null;
        }
    }

    /** (Re)allocate the depth map + its FBO at {@link #resolution}; no-op if already that size. */
    private static void ensureTarget() {
        int res = Math.max(256, resolution);
        if (texture != null && allocRes == res) {
            return;
        }
        closeTarget();
        var device = RenderSystem.getDevice();
        // Create through the normal RGBA8 path, then redefine storage as a real depth texture (the
        // DUHdrTarget/DUNormalBuffer trick — MC 26.1 has no depth TextureFormat to request).
        texture = device.createTexture(() -> "DU shadow depth", USAGE_ALL, TextureFormat.RGBA8, res, res, 1, 1);
        texId = ((GlTexture) texture).glId();
        GlStateManager._bindTexture(texId);
        GlStateManager._texImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, res, res, 0,
                GL_DEPTH_COMPONENT, GL_UNSIGNED_INT, (ByteBuffer) null);
        GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        GlStateManager._bindTexture(0);
        view = device.createTextureView(texture);

        // Our own depth-only FBO. Bind via GlStateManager (cached) so we don't desync Blaze3D's tracked
        // framebuffer; save/restore whatever was current. Object mgmt + attachment are raw GL (uncached).
        int prevFbo = GL11C.glGetInteger(GL_FRAMEBUFFER_BINDING);
        fbo = GL30C.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        GL30C.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, texId, 0);
        GL11C.glDrawBuffer(GL_NONE);             // depth-only: discard colour, FBO stays complete (per-FBO state)
        GL11C.glReadBuffer(GL_NONE);
        int status = GL30C.glCheckFramebufferStatus(GL_FRAMEBUFFER);
        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, prevFbo);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            failOnce("shadow FBO incomplete (status 0x" + Integer.toHexString(status) + ")", null);
            closeTarget();
            return;
        }
        allocRes = res;
    }

    private static void closeTarget() {
        if (fbo != 0) {
            GL30C.glDeleteFramebuffers(fbo);
            fbo = 0;
        }
        // Deferred close (see DUGpuTrash): a resize/realloc can happen while ImGuiMC still holds the view.
        DUGpuTrash.retire(texture, view);
        texture = null;
        view = null;
        texId = 0;
        allocRes = -1;
    }

    public static void close() {
        closeTarget();
        DUShadowColorTarget.close();
        mapReady = false;
        coloredMapReady = false;
        shadowRenderLists = null;     // drop the cached list; sections belong to Sodium and may be freed
        listBuiltThisFrame = false;
    }

    private static void failOnce(String detail, Throwable t) {
        failed = true;
        if (!warned) {
            warned = true;
            DragonUniverse.LOGGER.warn("[Dragon Universe] DU shadow pass failed ({}); disabling shadows "
                    + "(scene unaffected — falls back to no DU shadows).", detail, t);
        }
    }
}
