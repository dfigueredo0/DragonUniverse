package studio.elysium.dragonuniverse.client.render.post;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import studio.elysium.dragonuniverse.DragonUniverse;

import java.nio.ByteBuffer;

/**
 * Stage D (colored shadows): the <b>second</b> sun's-eye target — a translucent-occluder depth map plus a
 * tint color buffer. Where the opaque shadow map ({@link DUShadowPass}) says "lit" but a translucent surface
 * (stained glass, water) is between the fragment and the sun, the receiver tints the sunlight by the stored
 * color instead of darkening it.
 *
 * <p>One FBO with two attachments at the shadow-map resolution:</p>
 * <ul>
 *   <li><b>{@code DEPTH_COMPONENT24}</b> depth (the {@link DUHdrTarget}/{@link DUShadowPass} storage-reformat
 *       trick — 26.1 has no requestable depth {@link TextureFormat}): the nearest-to-light translucent
 *       surface's depth (LEQUAL, depth-write on, blend off during the draw so the front-most occluder wins).</li>
 *   <li><b>{@code RGBA8}</b> color: rgb = the occluder's plain dyed albedo / water material tint, a = opacity.
 *       LDR on purpose — the tint is never premultiplied by light, so it can't clip in 8-bit.</li>
 * </ul>
 *
 * <p>{@link DUShadowPass} owns the per-frame bind/clear (its {@code onSodiumBegin}/{@code onSodiumEnd} window,
 * picking this FBO while the translucent re-invoke is active); this class is just the resource holder. Lazily
 * (re)allocated at the shadow resolution; all on the render thread; fail-soft (alloc failure → no colored
 * shadows, the opaque stack is unaffected).</p>
 */
public final class DUShadowColorTarget {
    private DUShadowColorTarget() {}

    // Raw GL enums (mirror DUShadowPass's local-constant style; keep LWJGL constants out of call sites).
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
    private static final int GL_COLOR_ATTACHMENT0 = 0x8CE0;
    private static final int GL_FRAMEBUFFER_COMPLETE = 0x8CD5;
    private static final int GL_NONE = 0;

    private static final int USAGE_ALL = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC
            | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT;

    private static GpuTexture depthTex;
    private static GpuTexture colorTex;
    private static GpuTextureView depthView;
    private static GpuTextureView colorView;
    private static int depthId = 0;
    private static int colorId = 0;
    private static int fbo = 0;
    private static int allocRes = -1;
    private static boolean warned = false;

    /** The translucent depth map view (for the receiver + Target Inspector). Null until built. */
    public static GpuTextureView depthView() {
        return depthView;
    }

    /** The tint color view (rgb=tint, a=opacity), for the receiver + Target Inspector. Null until built. */
    public static GpuTextureView colorView() {
        return colorView;
    }

    /** The FBO id, for {@link DUShadowPass} to bind during the translucent re-invoke. 0 until built. */
    public static int fbo() {
        return fbo;
    }

    public static boolean ready() {
        return fbo != 0 && depthView != null && colorView != null;
    }

    /**
     * (Re)allocate the depth+color attachments and the FBO at {@code res} (the shadow-map resolution); no-op
     * if already that size. Returns false on any failure (caller skips colored shadows for the frame).
     */
    public static boolean ensure(int res) {
        if (fbo != 0 && allocRes == res) {
            return true;
        }
        close();
        try {
            var device = RenderSystem.getDevice();

            // Color: a plain RGBA8 tint target (rgb = dyed albedo / water tint, a = opacity).
            colorTex = device.createTexture(() -> "DU shadow tint", USAGE_ALL, TextureFormat.RGBA8, res, res, 1, 1);
            colorId = ((GlTexture) colorTex).glId();
            GlStateManager._bindTexture(colorId);
            GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

            // Depth: RGBA8 storage redefined as DEPTH_COMPONENT24 via raw GL (same trick as DUShadowPass).
            depthTex = device.createTexture(() -> "DU shadow tint depth", USAGE_ALL, TextureFormat.RGBA8, res, res, 1, 1);
            depthId = ((GlTexture) depthTex).glId();
            GlStateManager._bindTexture(depthId);
            GlStateManager._texImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, res, res, 0,
                    GL_DEPTH_COMPONENT, GL_UNSIGNED_INT, (ByteBuffer) null);
            GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            GlStateManager._bindTexture(0);

            colorView = device.createTextureView(colorTex);
            depthView = device.createTextureView(depthTex);

            // Own FBO: color0 + depth. Bind via GlStateManager (cached); save/restore the prior binding.
            int prevFbo = GL11C.glGetInteger(GL_FRAMEBUFFER_BINDING);
            fbo = GL30C.glGenFramebuffers();
            GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            GL30C.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorId, 0);
            GL30C.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthId, 0);
            GL11C.glDrawBuffer(GL_COLOR_ATTACHMENT0);
            GL11C.glReadBuffer(GL_NONE);
            int status = GL30C.glCheckFramebufferStatus(GL_FRAMEBUFFER);
            GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, prevFbo);
            if (status != GL_FRAMEBUFFER_COMPLETE) {
                if (!warned) {
                    warned = true;
                    DragonUniverse.LOGGER.warn("[Dragon Universe] Shadow tint FBO incomplete (0x{}); colored "
                            + "shadows disabled (opaque shadows unaffected).", Integer.toHexString(status));
                }
                close();
                return false;
            }
            allocRes = res;
            return true;
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                DragonUniverse.LOGGER.warn("[Dragon Universe] Shadow tint target alloc failed; colored shadows "
                        + "disabled (opaque shadows unaffected).", t);
            }
            close();
            return false;
        }
    }

    public static void close() {
        if (fbo != 0) {
            GL30C.glDeleteFramebuffers(fbo);
            fbo = 0;
        }
        // Deferred close (see DUGpuTrash): a realloc can happen while ImGuiMC still holds a view.
        DUGpuTrash.retire(depthTex, depthView);
        DUGpuTrash.retire(colorTex, colorView);
        depthTex = null;
        colorTex = null;
        depthView = null;
        colorView = null;
        depthId = 0;
        colorId = 0;
        allocRes = -1;
    }
}
