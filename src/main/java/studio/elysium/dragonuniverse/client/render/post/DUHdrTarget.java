package studio.elysium.dragonuniverse.client.render.post;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;

import java.nio.ByteBuffer;

/**
 * A floating-point ({@code RGBA16F}) offscreen color target — the HDR scene buffer the look stack
 * composites into so additive VFX accumulate past 1.0 instead of clipping at the RGBA8 main target.
 *
 * <p>MC 26.1's Blaze3D {@link TextureFormat} enum has no float color format (only {@code RGBA8}), so
 * we can't ask {@link com.mojang.blaze3d.systems.GpuDevice#createTexture} for one directly. Instead
 * we let the device create a normal {@code RGBA8} {@link GlTexture} (the GL backend uses <i>mutable</i>
 * {@code glTexImage2D} storage, not immutable {@code glTexStorage2D}), then redefine its level-0
 * storage as {@code GL_RGBA16F} via raw GL using the texture's public {@link GlTexture#glId()}. The
 * texture's bookkeeping {@code format} field still reports {@code RGBA8}, but the real GL storage is
 * float — this is the same below-the-abstraction trick Iris uses, minus the reflection.</p>
 *
 * <p><b>Sharp edge:</b> never {@code copyTextureToTexture} between this float buffer and the RGBA8
 * main target — {@code glCopyImageSubData} requires matching internal formats. Move data with a
 * fullscreen sample-and-write pass instead (see {@link DUPostChain}).</p>
 */
final class DUHdrTarget {
    // Raw GL enum values (avoid pulling LWJGL GL constants into call sites).
    private static final int GL_TEXTURE_2D = 3553;
    private static final int GL_RGBA16F = 0x881A;        // 34842 — half-float RGBA internal format
    private static final int GL_RGBA = 6408;
    private static final int GL_FLOAT = 5126;            // valid type for a null-data RGBA16F upload
    private static final int GL_LINEAR = 9729;
    private static final int GL_CLAMP_TO_EDGE = 33071;
    private static final int GL_TEXTURE_MIN_FILTER = 10241;
    private static final int GL_TEXTURE_MAG_FILTER = 10240;
    private static final int GL_TEXTURE_WRAP_S = 10242;
    private static final int GL_TEXTURE_WRAP_T = 10243;

    // All usage bits (copy dst/src | texture binding | render attachment) — same as the main target.
    private static final int USAGE_ALL = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC
            | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT;

    private final String label;
    private GpuTexture texture;
    private GpuTextureView view;
    private int w = -1;
    private int h = -1;

    DUHdrTarget(String label) {
        this.label = label;
    }

    GpuTextureView view() {
        return view;
    }

    GpuTexture texture() {
        return texture;
    }

    /** (Re)allocate at the given size as an RGBA16F texture; no-op if already that size. */
    void ensure(int width, int height) {
        if (texture != null && w == width && h == height) {
            return;
        }
        close();
        var device = RenderSystem.getDevice();
        // Create through the normal path (RGBA8 storage), then reformat the storage to RGBA16F.
        texture = device.createTexture(() -> label, USAGE_ALL, TextureFormat.RGBA8, width, height, 1, 1);
        int id = ((GlTexture) texture).glId();
        GlStateManager._bindTexture(id);
        GlStateManager._texImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, width, height, 0, GL_RGBA, GL_FLOAT, (ByteBuffer) null);
        GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        GlStateManager._bindTexture(0);
        view = device.createTextureView(texture);
        w = width;
        h = height;
    }

    void close() {
        // Deferred close: ImGuiMC may still draw this view during endFrame after a resize triggered
        // the reallocation. Park it in the trash and let DUGpuTrash close it a few frames later.
        DUGpuTrash.retire(texture, view);
        texture = null;
        view = null;
        w = -1;
        h = -1;
    }
}
