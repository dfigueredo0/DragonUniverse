package studio.elysium.dragonuniverse.client.render.post;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import studio.elysium.dragonuniverse.DragonUniverse;

import java.nio.ByteBuffer;

/**
 * SSR routing: the water surface G-buffer. A third color attachment ({@code RGBA16F},
 * {@code COLOR_ATTACHMENT2}) that the injected water fragment shader writes during Sodium's
 * <b>translucent</b> terrain pass — carrying the per-pixel data the composite-stage SSR pass can't get
 * any other way: the wave/ripple surface normal, the fresnel term, and the surface depth.
 *
 * <p><b>Why a separate attachment from {@link DUNormalBuffer}:</b> the geometry-normals MRT
 * ({@code location = 1}) is deliberately detached for translucent passes (so blend state never bleeds
 * into it), so the water surface never reaches it. This buffer is on {@code location = 2} and is bound
 * <i>only</i> for the translucent water pass, leaving the geometry-normals path untouched.</p>
 *
 * <p><b>Channels</b> (written by {@code SodiumShaderLoaderMixin}'s water body):
 * {@code rg} = octahedral-encoded <i>view-space</i> surface normal, {@code b} = fresnel reflectance,
 * {@code a} = the water surface window depth ({@code gl_FragCoord.z}). {@code a > 0} is the reflection
 * mask (cleared frame = 0). View-space normal so the SSR raymarch uses it directly; depth so SSR
 * reconstructs the ray origin without depending on whether the translucent pass wrote main depth.</p>
 *
 * <p><b>RGBA16F</b> via the same storage-reformat trick as {@link DUHdrTarget} (26.1's
 * {@link TextureFormat} enum has no float color format): create an {@code RGBA8} texture, then redefine
 * level-0 storage as {@code GL_RGBA16F} via raw GL. Half-float keeps the octahedral normal and depth
 * precise (an {@code RGBA8} normal would band the reflection).</p>
 *
 * <p><b>Blend:</b> the water color output ({@code location = 0}) is alpha-blended into the scene, but the
 * G-buffer must be written raw — so blending is disabled on draw-buffer index 2 while attached and
 * restored on detach. Back-to-front translucent sort means the front-most water surface wins each pixel,
 * which is exactly the surface we want to reflect from.</p>
 *
 * <p>Lifecycle mirrors {@link DUNormalBuffer}: lazily allocated at the color-target size, cleared once per
 * frame before the first write (guard reset by {@link #beginFrame()}), attached at the translucent
 * {@code begin} TAIL and detached at {@code end} HEAD. Render-thread only; fail-soft.</p>
 */
public final class DUWaterGbuffer {
    private DUWaterGbuffer() {}

    // Raw GL enums (mirror DUNormalBuffer's local-constant style).
    private static final int GL_TEXTURE_2D = 3553;
    private static final int GL_RGBA16F = 0x881A;                 // half-float storage
    private static final int GL_RGBA = 6408;
    private static final int GL_FLOAT = 5126;                     // valid type for a null RGBA16F upload
    private static final int GL_NEAREST = 9728;                   // no cross-silhouette interpolation
    private static final int GL_CLAMP_TO_EDGE = 33071;
    private static final int GL_TEXTURE_MIN_FILTER = 10241;
    private static final int GL_TEXTURE_MAG_FILTER = 10240;
    private static final int GL_TEXTURE_WRAP_S = 10242;
    private static final int GL_TEXTURE_WRAP_T = 10243;

    private static final int GL_FRAMEBUFFER = 0x8D40;
    private static final int GL_COLOR_ATTACHMENT0 = 0x8CE0;
    private static final int GL_COLOR_ATTACHMENT2 = 0x8CE2;
    private static final int GL_NONE = 0;
    private static final int GL_COLOR = 0x1800;
    private static final int GL_BLEND = 0x0BE2;

    private static final int USAGE_ALL = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC
            | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT;

    // Draw-buffer maps. The water gbuf is fragment output location=2, so it must sit at array index 2;
    // index 1 (the geometry-normal output location=1) maps to GL_NONE so that write is discarded here.
    private static final int[] DRAW_BUFFERS_WATER = { GL_COLOR_ATTACHMENT0, GL_NONE, GL_COLOR_ATTACHMENT2 };
    private static final int[] DRAW_BUFFERS_COLOR = { GL_COLOR_ATTACHMENT0 };
    private static final float[] ZERO = { 0.0F, 0.0F, 0.0F, 0.0F };

    private static GpuTexture texture;
    private static GpuTextureView view;
    private static int glId = 0;
    private static int w = -1;
    private static int h = -1;

    /** Set true once {@link #attach} clears the buffer this frame; reset by {@link #beginFrame}. */
    private static boolean clearedThisFrame = false;
    /** Set true by {@link #attach} during the translucent pass; latched into {@link #freshThisFrame}. */
    private static boolean attachedThisFrame = false;
    /** Whether the buffer was written by the translucent pass this frame (gates the SSR consumer). */
    private static boolean freshThisFrame = false;
    private static boolean warnedOnce = false;

    /** The water G-buffer view, for the SSR pass and the Target Inspector. Null until allocated. */
    public static GpuTextureView view() {
        return view;
    }

    /**
     * Per-frame bookkeeping, called once at AfterLevel (after the translucent pass has run, before the SSR
     * consumer). Latches whether the translucent pass wrote the buffer this frame into
     * {@link #freshThisFrame} — the SSR pass reads that to skip stale data — then re-arms both guards for
     * next frame. Crucially it does <b>not</b> zero the buffer: SSR self-gates on freshness instead, so the
     * last-written content survives for the Target Inspector to display.
     */
    public static void beginFrame() {
        freshThisFrame = attachedThisFrame;
        attachedThisFrame = false;
        clearedThisFrame = false;
    }

    /**
     * Whether the translucent pass wrote the G-buffer this frame. The SSR pass gates on this so that a
     * frame with no translucent pass (e.g. the camera panned off all water) doesn't raymarch last frame's
     * stale surface and paint ghost reflections.
     */
    public static boolean wasWrittenThisFrame() {
        return freshThisFrame;
    }

    /**
     * Attach the gbuf at {@code COLOR_ATTACHMENT2}, enable the water MRT draw-buffer map, clear it once
     * per frame, and disable blending on its draw-buffer index so it's written raw (not alpha-blended
     * like the color output). Must be called with the terrain FBO bound (Sodium's translucent
     * {@code begin} TAIL).
     */
    public static void attach(int colorW, int colorH) {
        ensure(colorW, colorH);
        if (texture == null) {
            return;
        }
        GL30C.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT2, GL_TEXTURE_2D, glId, 0);
        GL20C.glDrawBuffers(DRAW_BUFFERS_WATER);
        if (!clearedThisFrame) {
            GL30C.glClearBufferfv(GL_COLOR, 2, ZERO);   // draw-buffer index 2 -> COLOR_ATTACHMENT2
            clearedThisFrame = true;
        }
        GL30C.glDisablei(GL_BLEND, 2);                  // raw write on the gbuf attachment
        attachedThisFrame = true;                        // mark the buffer fresh for this frame's SSR
    }

    /**
     * Restore the single-color draw buffer, re-enable blending on index 2, and detach the gbuf, returning
     * the cached Blaze3D FBO to its color0+depth shape for subsequent passes. Called at Sodium's
     * translucent {@code end} HEAD (FBO still bound).
     */
    public static void detach() {
        GL30C.glEnablei(GL_BLEND, 2);
        GL20C.glDrawBuffers(DRAW_BUFFERS_COLOR);
        GL30C.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT2, GL_TEXTURE_2D, 0, 0);
    }

    /** (Re)allocate at the given size as an RGBA16F texture; no-op if already that size. */
    private static void ensure(int width, int height) {
        if (texture != null && w == width && h == height) {
            return;
        }
        close();
        try {
            var device = RenderSystem.getDevice();
            // Create through the normal path (RGBA8 storage), then reformat the storage to RGBA16F.
            texture = device.createTexture(() -> "DU water gbuffer", USAGE_ALL, TextureFormat.RGBA8, width, height, 1, 1);
            glId = ((GlTexture) texture).glId();
            GlStateManager._bindTexture(glId);
            GlStateManager._texImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, width, height, 0, GL_RGBA, GL_FLOAT, (ByteBuffer) null);
            GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            GlStateManager._bindTexture(0);
            view = device.createTextureView(texture);
            w = width;
            h = height;
        } catch (Throwable t) {
            if (!warnedOnce) {
                warnedOnce = true;
                DragonUniverse.LOGGER.warn("[Dragon Universe] Water SSR G-buffer alloc failed; SSR disabled "
                        + "(scene unaffected).", t);
            }
            close();
        }
    }

    public static void close() {
        // Deferred close (see DUGpuTrash): a resize can reallocate while ImGuiMC still holds the old view.
        DUGpuTrash.retire(texture, view);
        texture = null;
        view = null;
        glId = 0;
        w = -1;
        h = -1;
    }
}
