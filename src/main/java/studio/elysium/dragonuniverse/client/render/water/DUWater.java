package studio.elysium.dragonuniverse.client.render.water;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;
import studio.elysium.dragonuniverse.DragonUniverse;
import studio.elysium.dragonuniverse.client.render.core.DUFrameData;
import studio.elysium.dragonuniverse.client.render.core.DUSsrData;
import studio.elysium.dragonuniverse.client.render.core.DUWaterData;
import studio.elysium.dragonuniverse.client.render.post.DUNormalBuffer;

/**
 * Depth-aware water — the CPU/orchestration half. The shading itself is injected straight into
 * Sodium's translucent terrain fragment shader ({@code mixin/sodium/SodiumShaderLoaderMixin}); this class
 * feeds that injected code everything it can't get from the terrain pass on its own:
 *
 * <ul>
 *   <li><b>An opaque-depth snapshot</b> — you cannot sample the depth attachment you're rendering into,
 *       so {@link #captureOpaqueDepth()} copies the main depth texture into our own {@link #depthCopy}
 *       just before the translucent terrain pass draws (at Sodium's {@code begin} HEAD). The water
 *       fragment reads it to recover the geometry behind the surface.</li>
 *   <li><b>Per-frame camera data</b> — {@link #onAfterSky} captures {@code invProj}, the world→view
 *       rotation, the view-space sun direction, and time (early in the frame, where the modelview is the
 *       camera rotation), then {@link #bindWaterUniforms()} pushes them as plain GL uniforms onto
 *       Sodium's program at {@code begin} TAIL.</li>
 *   <li><b>The water sprite's atlas-UV bounds</b> — how the injected shader tells a water fragment from
 *       any other translucent terrain (Sodium has no per-fragment material id).</li>
 * </ul>
 *
 * <p>Everything in view space (matching {@code du_ssao}). Reuses the {@code d_WorldPos} varying the
 * geometry-normals inject already adds, so water requires {@link DUNormalBuffer#shaderReady()}.</p>
 *
 * <p>Render-thread only; all GL is raw-GL in the same idiom as {@link DUNormalBuffer}/{@code
 * DUEntityNormals}. Fail-soft: a missing texture, a failed copy, or an absent program disables water
 * without touching Sodium terrain or the rest of the look stack.</p>
 */
@EventBusSubscriber(modid = DragonUniverse.MODID, value = Dist.CLIENT)
public final class DUWater {
    private DUWater() {}

    // Raw GL enums (mirror DUNormalBuffer's local-constant style).
    private static final int GL_TEXTURE_2D = 3553;
    private static final int GL_TEXTURE0 = 0x84C0;
    private static final int GL_CURRENT_PROGRAM = 0x8B8D;
    private static final int GL_NEAREST = 9728;
    private static final int GL_CLAMP_TO_EDGE = 33071;
    private static final int GL_TEXTURE_MIN_FILTER = 10241;
    private static final int GL_TEXTURE_MAG_FILTER = 10240;
    private static final int GL_TEXTURE_WRAP_S = 10242;
    private static final int GL_TEXTURE_WRAP_T = 10243;

    private static final int USAGE = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING;

    /** Texture unit our opaque-depth copy is bound to on Sodium's program (well clear of its samplers). */
    private static final int DEPTH_UNIT = 6;
    /** Texture unit the Stage 3 ripple-sim field is bound to on Sodium's program. */
    private static final int RIPPLE_UNIT = 7;

    // Per-frame camera data captured at AfterSky (matrices are rotation-only / projection there, valid for
    // the whole frame). Column-major float arrays ready for glUniformMatrix*.
    private static final Matrix4f INV_PROJ = new Matrix4f();
    private static final Matrix3f WORLD_TO_VIEW = new Matrix3f();
    private static final Vector3f SUN_TMP = new Vector3f();
    private static final float[] invProj = new float[16];
    private static final float[] normalToView = new float[9];
    private static final Vector3f sunVS = new Vector3f();
    private static float time;
    private static boolean matricesReady = false;

    /** Set true once {@link #captureOpaqueDepth()} succeeds this frame; reset each frame at AfterSky. */
    private static boolean depthReadyThisFrame = false;

    // Water sprite atlas-UV bounds (u0,v0,u1,v1), fetched lazily once the block atlas is stitched.
    private static float[] stillUV = null;
    private static float[] flowUV = null;

    // Opaque-depth copy target (DEPTH32, same as the main target's depth so copyTextureToTexture matches).
    private static GpuTexture depthCopy;
    private static GpuTextureView depthCopyView;
    private static int depthCopyGlId = 0;
    private static int dw = -1;
    private static int dh = -1;

    private static volatile boolean fshReady = false;
    private static volatile boolean failed = false;

    /** Called by the loader mixin when the water override is appended to Sodium's terrain fsh. */
    public static void markFshReady() {
        fshReady = true;
    }

    /** Disable water (fail-soft), logging once. Terrain + the look stack are unaffected. */
    public static void markFailed(String detail) {
        if (!failed) {
            failed = true;
            DragonUniverse.LOGGER.warn("[Dragon Universe] Water material disabled ({}); Sodium terrain and the "
                    + "look stack are unaffected.", detail);
        }
    }

    /**
     * Whether the injected water shader is present and usable. Needs the water override (fsh) AND the
     * geometry-normals inject (it supplies the {@code d_WorldPos} varying the water code reuses).
     */
    public static boolean shaderReady() {
        return fshReady && !failed && DUNormalBuffer.shaderReady();
    }

    /**
     * Whether the camera eye is in water right now — a <b>live</b> read (not a per-frame capture),
     * because the sky stage ({@code AfterSky}) doesn't fire while submerged, so a flag captured there
     * would stay stale-false underwater. {@code getFluidInCamera()} recomputes from the camera position,
     * so it's valid at any point in the frame. Read by the fsh eye branch (via {@link #bindWaterUniforms})
     * and the look-stack underwater fog pass ({@code DUPostChain}).
     */
    public static boolean isEyeInWater() {
        try {
            Minecraft mc = Minecraft.getInstance();
            return mc.level != null && mc.gameRenderer.getMainCamera().getFluidInCamera() == FogType.WATER;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * The opaque-depth snapshot (scene depth <i>without</i> translucent water), for the SSR raymarch's
     * scene hit-test — so reflections test against solid geometry and never self-intersect the water
     * plane. Captured each frame at the translucent pass; null until {@link #captureOpaqueDepth} runs.
     */
    public static GpuTextureView opaqueDepthView() {
        return depthCopyView;
    }

    // ---- per-frame camera capture (early in the frame, before the translucent terrain pass) ----------

    // Captured at AfterSky (above water, same-frame for that frame's translucent bind) AND at AfterLevel
    // (which always fires — even submerged, when AfterSky is skipped — so the matrices stay fresh, at most
    // one frame old, underwater). The eye flag itself is read live (see isEyeInWater), so eye transitions
    // are instant regardless of which stages fired.
    @SubscribeEvent
    public static void onAfterSky(RenderLevelStageEvent.AfterSky event) {
        capture(event.getLevelRenderState().cameraRenderState);
    }

    @SubscribeEvent
    public static void onAfterLevel(RenderLevelStageEvent.AfterLevel event) {
        capture(event.getLevelRenderState().cameraRenderState);
    }

    private static void capture(CameraRenderState cam) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || cam == null) {
            matricesReady = false;
            return;
        }
        try {
            INV_PROJ.set(cam.projectionMatrix).invert().get(invProj);
            WORLD_TO_VIEW.set(cam.viewRotationMatrix);   // world->view rotation
            WORLD_TO_VIEW.get(normalToView);
            SUN_TMP.set(DUFrameData.sunDir);
            WORLD_TO_VIEW.transform(SUN_TMP).normalize();
            sunVS.set(SUN_TMP);
            float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            time = mc.level.getGameTime() + partial;
            matricesReady = true;
            ensureWaterSprites();
        } catch (Throwable t) {
            matricesReady = false;
        }
    }

    // ---- opaque-depth snapshot (Sodium translucent begin HEAD) ----------------------------------------

    /**
     * Copy the main target's (opaque) depth into {@link #depthCopy} so the water fragment can read the
     * geometry behind the surface. Called just before the translucent terrain pass draws — at that point
     * the depth buffer holds solid/cutout terrain + entities + block entities, but no translucent water yet.
     */
    public static void captureOpaqueDepth() {
        depthReadyThisFrame = false;
        if (!DUWaterData.enabled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        if (main == null) {
            return;
        }
        GpuTexture src = main.getDepthTexture();
        int w = main.width;
        int h = main.height;
        if (src == null || w <= 0 || h <= 0) {
            return;
        }
        try {
            ensureDepthCopy(w, h);
            if (depthCopy == null) {
                return;
            }
            RenderSystem.getDevice().createCommandEncoder()
                    .copyTextureToTexture(src, depthCopy, 0, 0, 0, 0, 0, w, h);
            depthReadyThisFrame = true;
        } catch (Throwable t) {
            markFailed("opaque-depth copy threw: " + t.getClass().getSimpleName());
        }
    }

    // ---- bind the injected uniforms onto Sodium's program (translucent begin TAIL) --------------------

    /**
     * Push the per-frame camera data, the opaque-depth sampler, the water-sprite bounds, and all material
     * tunables onto Sodium's currently-bound terrain program. {@code du_WaterEnabled} carries the effective
     * gate, so when water is off (or the depth/matrices weren't ready) the injected branch stays inert and
     * never samples the stale depth copy.
     */
    public static void bindWaterUniforms() {
        if (!matricesReady) {
            return;
        }
        int prog = GL11C.glGetInteger(GL_CURRENT_PROGRAM);
        if (prog == 0) {
            return;
        }
        boolean ready = stillUV != null && flowUV != null;
        // Material (fsh) gate: needs the depth snapshot. Wave (vsh) gate: doesn't.
        boolean matEff = DUWaterData.enabled && depthReadyThisFrame && ready;
        boolean waveEff = DUWaterData.enabled && DUWaterData.wavesEnabled && ready;

        // Always bind the depth sampler to its unit + set both gates, even when off (so the gates reliably
        // turn their branches off rather than leaving them reading a stale binding).
        if (depthCopyGlId != 0) {
            GlStateManager._activeTexture(GL_TEXTURE0 + DEPTH_UNIT);
            GlStateManager._bindTexture(depthCopyGlId);
            GlStateManager._activeTexture(GL_TEXTURE0);
        }
        u1i(prog, "du_OpaqueDepth", DEPTH_UNIT);
        u1f(prog, "du_WaterEnabled", matEff ? 1.0F : 0.0F);
        u1f(prog, "du_WaveEnabled", waveEff ? 1.0F : 0.0F);
        if (!ready) {
            return;
        }

        // Shared by both stages (water-UV gates) + the vsh Gerstner inputs — bound whenever the sprite
        // bounds are known, independent of the depth snapshot, so waves run even on a frame the copy missed.
        u4f(prog, "du_WaterStillUV", stillUV[0], stillUV[1], stillUV[2], stillUV[3]);
        u4f(prog, "du_WaterFlowUV", flowUV[0], flowUV[1], flowUV[2], flowUV[3]);
        u1f(prog, "du_Time", time);
        u1f(prog, "du_WaveAmplitude", DUWaterData.waveAmplitude);
        u1f(prog, "du_WaveLength", DUWaterData.waveLength);
        u1f(prog, "du_WaveSpeed", DUWaterData.waveSpeed);
        u1f(prog, "du_WaveSteepness", DUWaterData.waveSteepness);

        // Ripple-sim field (own texture unit). Bound in the always-run section because the VERTEX stage
        // samples it for surface displacement, which must not depend on the depth snapshot.
        int rippleGl = DUWaterSim.fieldGlId();
        if (rippleGl != 0) {
            GlStateManager._activeTexture(GL_TEXTURE0 + RIPPLE_UNIT);
            GlStateManager._bindTexture(rippleGl);
            GlStateManager._activeTexture(GL_TEXTURE0);
        }
        u1i(prog, "du_RippleField", RIPPLE_UNIT);
        u1f(prog, "du_RippleEnabled", (DUWaterData.rippleEnabled && rippleGl != 0) ? 1.0F : 0.0F);
        u1f(prog, "du_RippleExtent", DUWaterSim.EXTENT);
        u1f(prog, "du_RippleTexel", 1.0F / DUWaterSim.FIELD_SIZE);
        u1f(prog, "du_RippleNormalStr", DUWaterData.rippleNormalStrength);
        u1f(prog, "du_RippleDisplaceScale", DUWaterData.rippleDisplaceScale);

        if (!matEff) {
            return;
        }

        // fsh-only material params (need the depth snapshot to be meaningful).
        um4(prog, "du_InvProj", invProj);
        um3(prog, "du_NormalToView", normalToView);
        u3f(prog, "du_SunDirVS", sunVS.x, sunVS.y, sunVS.z);
        u1f(prog, "du_EyeInWater", isEyeInWater() ? 1.0F : 0.0F);
        u1f(prog, "du_SsrEnabled", DUSsrData.enabled ? 1.0F : 0.0F);   // gates the SSR gbuf write
        u3f(prog, "du_UnderColor", DUWaterData.underColor.x, DUWaterData.underColor.y, DUWaterData.underColor.z);
        u1f(prog, "du_UnderFresnelPow", DUWaterData.underFresnelPow);
        u1f(prog, "du_UnderAlphaMin", DUWaterData.underAlphaMin);
        u1f(prog, "du_UnderAlphaMax", DUWaterData.underAlphaMax);

        u1f(prog, "du_AlphaK", DUWaterData.alphaK);
        u1f(prog, "du_AlphaMin", DUWaterData.alphaMin);
        u1f(prog, "du_AlphaMax", DUWaterData.alphaMax);
        u1f(prog, "du_FogDensity", DUWaterData.fogDensity);
        u3f(prog, "du_DeepColor", DUWaterData.deepColor.x, DUWaterData.deepColor.y, DUWaterData.deepColor.z);
        u3f(prog, "du_ShallowColor", DUWaterData.shallowColor.x, DUWaterData.shallowColor.y, DUWaterData.shallowColor.z);
        u1f(prog, "du_FoamWidth", DUWaterData.foamWidth);
        u3f(prog, "du_FoamColor", DUWaterData.foamColor.x, DUWaterData.foamColor.y, DUWaterData.foamColor.z);
        u1f(prog, "du_FresnelF0", DUWaterData.fresnelF0);
        u1f(prog, "du_Glint", DUWaterData.glint);
        u1f(prog, "du_NormalScale", DUWaterData.normalScale);
        u1f(prog, "du_NormalStrength", DUWaterData.normalStrength);
        u1f(prog, "du_NormalSpeed", DUWaterData.normalSpeed);
    }

    // ---- helpers -------------------------------------------------------------------------------------

    private static void u1i(int prog, String name, int v) {
        int loc = GL20C.glGetUniformLocation(prog, name);
        if (loc >= 0) {
            GL20C.glUniform1i(loc, v);
        }
    }

    private static void u1f(int prog, String name, float v) {
        int loc = GL20C.glGetUniformLocation(prog, name);
        if (loc >= 0) {
            GL20C.glUniform1f(loc, v);
        }
    }

    private static void u3f(int prog, String name, float x, float y, float z) {
        int loc = GL20C.glGetUniformLocation(prog, name);
        if (loc >= 0) {
            GL20C.glUniform3f(loc, x, y, z);
        }
    }

    private static void u4f(int prog, String name, float x, float y, float z, float w) {
        int loc = GL20C.glGetUniformLocation(prog, name);
        if (loc >= 0) {
            GL20C.glUniform4f(loc, x, y, z, w);
        }
    }

    private static void um4(int prog, String name, float[] m) {
        int loc = GL20C.glGetUniformLocation(prog, name);
        if (loc >= 0) {
            GL20C.glUniformMatrix4fv(loc, false, m);
        }
    }

    private static void um3(int prog, String name, float[] m) {
        int loc = GL20C.glGetUniformLocation(prog, name);
        if (loc >= 0) {
            GL20C.glUniformMatrix3fv(loc, false, m);
        }
    }

    private static void ensureWaterSprites() {
        if (stillUV != null && flowUV != null) {
            return;
        }
        try {
            AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
            if (tex instanceof TextureAtlas atlas) {
                TextureAtlasSprite still = atlas.getSprite(Identifier.withDefaultNamespace("block/water_still"));
                TextureAtlasSprite flow = atlas.getSprite(Identifier.withDefaultNamespace("block/water_flow"));
                if (still != null) {
                    stillUV = new float[] { still.getU0(), still.getV0(), still.getU1(), still.getV1() };
                }
                if (flow != null) {
                    flowUV = new float[] { flow.getU0(), flow.getV0(), flow.getU1(), flow.getV1() };
                }
                if (flowUV == null) {
                    flowUV = stillUV;   // fall back to the still bounds rather than a null gate
                }
            }
        } catch (Throwable t) {
            // Atlas not stitched yet (or unavailable) — retry next frame.
        }
    }

    private static void ensureDepthCopy(int width, int height) {
        if (depthCopy != null && dw == width && dh == height) {
            return;
        }
        closeDepth();
        var device = RenderSystem.getDevice();
        depthCopy = device.createTexture(() -> "DU water opaque-depth", USAGE, TextureFormat.DEPTH32, width, height, 1, 1);
        depthCopyGlId = ((GlTexture) depthCopy).glId();
        // Sampled raw (no GpuSampler object), so set NEAREST + clamp on the texture itself.
        GlStateManager._bindTexture(depthCopyGlId);
        GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        GlStateManager._bindTexture(0);
        depthCopyView = device.createTextureView(depthCopy);
        dw = width;
        dh = height;
    }

    private static void closeDepth() {
        if (depthCopyView != null) {
            depthCopyView.close();
            depthCopyView = null;
        }
        if (depthCopy != null) {
            depthCopy.close();
            depthCopy = null;
        }
        depthCopyGlId = 0;
        dw = -1;
        dh = -1;
    }
}
