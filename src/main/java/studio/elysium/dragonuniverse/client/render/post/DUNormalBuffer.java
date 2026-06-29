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
 * Geometry-normals G-buffer: a second color attachment ({@code RGB10_A2}, world-space normals
 * encoded {@code N*0.5+0.5}, with the 2-bit alpha as a coverage flag) that Sodium terrain writes into
 * via MRT during the opaque/cutout passes. Replaces the depth-reconstructed normals, whose precision
 * floor caused the horizon banding and the view-bob rim pulse — see the MRT plan.
 *
 * <p>The Sodium terrain fragment shader has the <i>true interpolated world position</i>
 * (pre-depth-quantization), so {@code cross(dFdx, dFdy)} of it is the exact flat face
 * normal — no precision floor, hence no banding and no temporal pulse.</p>
 *
 * <p><b>RGB10_A2 the same way {@link DUHdrTarget} does float:</b> MC 26.1's {@link TextureFormat} enum
 * has no 10-10-10-2 format, so we let the device make a normal {@code RGBA8} texture (mutable
 * {@code glTexImage2D} storage in the GL backend) then redefine level-0 storage as {@code GL_RGB10_A2}
 * via raw GL. 10-bit channels give smoother normals for future SSAO at the same 4-byte footprint; the
 * 2-bit alpha is a free per-pixel "geometry wrote here" flag (cleared frame = 0, geometry = 3).</p>
 *
 * <p>Lifecycle: lazily allocated at the color-target size when first attached; cleared to zero once per
 * frame (the frame guard, reset by {@link #beginFrame()}) <i>before</i> the first opaque pass so holes
 * read coverage 0; attached as {@code COLOR_ATTACHMENT1} only for non-translucent terrain passes (so
 * translucent blend state never bleeds into the normal/coverage output) and detached after each, so the
 * cached Blaze3D FBO returns to its pristine color0+depth shape for the entity/Blaze3D passes.</p>
 *
 * <p>All access is on the render thread during world render (the Sodium mixins) and at {@code AfterLevel}
 * (the look stack) — single-threaded GL, no synchronization needed.</p>
 */
public final class DUNormalBuffer {
    private DUNormalBuffer() {}

    // Raw GL enums (avoid pulling LWJGL constants into call sites; mirror DUHdrTarget's style).
    private static final int GL_TEXTURE_2D = 3553;
    private static final int GL_RGB10_A2 = 0x8059;                 // 10/10/10/2 normal storage
    private static final int GL_RGBA = 6408;
    private static final int GL_UNSIGNED_INT_2_10_10_10_REV = 0x8368; // valid type for a null RGB10_A2 upload
    private static final int GL_NEAREST = 9728;                    // no cross-silhouette interpolation
    private static final int GL_CLAMP_TO_EDGE = 33071;
    private static final int GL_TEXTURE_MIN_FILTER = 10241;
    private static final int GL_TEXTURE_MAG_FILTER = 10240;
    private static final int GL_TEXTURE_WRAP_S = 10242;
    private static final int GL_TEXTURE_WRAP_T = 10243;

    private static final int GL_FRAMEBUFFER = 0x8D40;
    private static final int GL_COLOR_ATTACHMENT0 = 0x8CE0;
    private static final int GL_COLOR_ATTACHMENT1 = 0x8CE1;
    private static final int GL_COLOR = 0x1800;

    private static final int USAGE_ALL = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC
            | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT;

    // Draw-buffer maps: MRT (color + normals) for the terrain pass, single (color) to restore.
    private static final int[] DRAW_BUFFERS_MRT = { GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1 };
    private static final int[] DRAW_BUFFERS_COLOR = { GL_COLOR_ATTACHMENT0 };
    private static final float[] ZERO = { 0.0F, 0.0F, 0.0F, 0.0F };

    private static GpuTexture texture;
    private static GpuTextureView view;
    private static int glId = 0;
    private static int w = -1;
    private static int h = -1;

    /** Set true once {@link DUNormalBuffer#attach} clears the buffer this frame; reset by {@link #beginFrame}. */
    private static boolean clearedThisFrame = false;

    // Fail-soft readiness: the GLSL inject mixin flips these on per-shader success, and trips
    // shaderFailed on any anchor mismatch (newer Sodium) so we never attach to a program that lacks
    // the location=1 normal output. False until the terrain shaders are (re)compiled with our inject.
    private static volatile boolean vshReady = false;
    private static volatile boolean fshReady = false;
    private static volatile boolean shaderFailed = false;

    /** True when the injected terrain shaders are confirmed to emit the normal output. */
    public static boolean shaderReady() {
        return vshReady && fshReady && !shaderFailed;
    }

    public static void markVshReady() { vshReady = true; }
    public static void markFshReady() { fshReady = true; }

    /** Called by the GLSL mixin when an anchor is missing (Sodium shape changed): disable, log once. */
    public static void markShaderFailed(String detail) {
        if (!shaderFailed) {
            shaderFailed = true;
            DragonUniverse.LOGGER.warn("[Dragon Universe] Sodium terrain shader shape not recognized ({}); "
                    + "geometry normals disabled (rim/SSAO will see no terrain normals). Look stack otherwise "
                    + "unaffected.", detail);
        }
    }

    /**
     * Whether terrain should write normals this frame: the look stack is on and the injected shaders are
     * confirmed ready. Gating attach (not the GLSL inject) by the toggle avoids recompiling Sodium's
     * cached programs when the look stack flips — the discarded normal output costs nothing when
     * {@code DRAW_BUFFERS} excludes attachment 1.
     */
    public static boolean shouldCapture() {
        return DUPostChain.isEnabled() && shaderReady();
    }

    /** The world-space normals view, for the look stack (rim) and the Target Inspector. Null until run. */
    public static GpuTextureView view() {
        return view;
    }

    /** Reset the per-frame clear guard. Called once per frame from {@link studio.elysium.dragonuniverse.client.render.vfx.DUWorldRenderer}. */
    public static void beginFrame() {
        clearedThisFrame = false;
    }

    /**
     * Attach the normals texture to the currently-bound FBO as {@code COLOR_ATTACHMENT1} and enable MRT.
     * Allocates/resizes to the color-target size on demand, and clears the attachment to zero the first
     * time per frame so holes (sky, undrawn pixels) read coverage 0. Must be called at a point where the
     * terrain FBO is the bound framebuffer (Sodium's {@code begin} TAIL).
     */
    public static void attach(int colorW, int colorH) {
        ensure(colorW, colorH);
        if (texture == null) {
            return;
        }
        GL30C.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, glId, 0);
        GL20C.glDrawBuffers(DRAW_BUFFERS_MRT);
        if (!clearedThisFrame) {
            // Clear only draw-buffer index 1 (the normals attachment); leaves the scene color intact.
            GL30C.glClearBufferfv(GL_COLOR, 1, ZERO);
            clearedThisFrame = true;
        }
    }

    /**
     * Restore the single-color draw buffer and detach the normals texture, returning the cached Blaze3D
     * FBO to its color0+depth shape so the subsequent entity/Blaze3D passes see no dangling attachment.
     * Called at Sodium's {@code end} HEAD (FBO still bound from {@code begin}).
     */
    public static void detach() {
        GL20C.glDrawBuffers(DRAW_BUFFERS_COLOR);
        GL30C.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, 0, 0);
    }

    /** (Re)allocate at the given size as an RGB10_A2 texture; no-op if already that size. */
    private static void ensure(int width, int height) {
        if (texture != null && w == width && h == height) {
            return;
        }
        close();
        var device = RenderSystem.getDevice();
        // Create through the normal path (RGBA8 storage), then reformat the storage to RGB10_A2. Both are
        // 4 bytes/pixel, so the texture's size bookkeeping stays consistent (unlike the float case).
        texture = device.createTexture(() -> "DU geometry normals", USAGE_ALL, TextureFormat.RGBA8, width, height, 1, 1);
        glId = ((GlTexture) texture).glId();
        GlStateManager._bindTexture(glId);
        GlStateManager._texImage2D(GL_TEXTURE_2D, 0, GL_RGB10_A2, width, height, 0, GL_RGBA,
                GL_UNSIGNED_INT_2_10_10_10_REV, (ByteBuffer) null);
        GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        GlStateManager._bindTexture(0);
        view = device.createTextureView(texture);
        w = width;
        h = height;
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
