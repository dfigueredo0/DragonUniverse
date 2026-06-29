package studio.elysium.dragonuniverse.client.render.post;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;

/**
 * Hi-Z min-depth pyramid for distance-stable SSAO. A mip chain where each level holds the <i>closest</i>
 * (min) linear scene depth over its footprint, so an SSAO sample whose world-space kernel has shrunk to a
 * few pixels at distance can read a footprint-matched mip and still see the nearest occluder there, rather
 * than point-sampling a single texel and aliasing/collapsing.
 *
 * <p><b>Format:</b> plain {@code RGBA8} with a real mip chain (natively supported, unlike a float format —
 * 26.1 has none in {@link TextureFormat}). Linear depth (normalized {@code -viewZ / FAR}) is float-packed
 * into the four bytes (~32-bit), so the min-downsample operates on full-precision depth. No raw-GL
 * storage reformat needed (cf. {@code DUHdrTarget}); only per-mip views, which the device supports
 * directly via {@code createTextureView(texture, baseMip, 1)}.</p>
 *
 * <p>Level 0 is written by the linearize pass ({@code du_hiz_init}); levels 1..N-1 by repeated min
 * downsample ({@code du_hiz_down}, reading level k-1, writing level k). SSAO samples the full view with
 * {@code textureLod} (needs a mipmap-enabled sampler). All on the render thread; fail-soft (a null view
 * just disables the Hi-Z path).</p>
 */
public final class DUHiZBuffer {
    private DUHiZBuffer() {}

    private static final int USAGE = GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING;

    private static GpuTexture texture;
    private static GpuTextureView fullView;        // all mips — for textureLod sampling in SSAO
    private static GpuTextureView[] levelViews;    // one per mip — render target + downsample input
    private static int w = -1;
    private static int h = -1;
    private static int mips = 0;

    /** Which mip level the debug viz pass renders (clamped). Set from the Target Inspector slider. */
    private static int vizLevel = 0;

    public static int mipCount() {
        return mips;
    }

    /** The full-mip view (for {@code textureLod} sampling). Null until built once. */
    public static GpuTextureView view() {
        return fullView;
    }

    /** A single-mip-level view (render target / downsample input). Null if out of range or not built. */
    public static GpuTextureView level(int k) {
        return (levelViews == null || k < 0 || k >= mips) ? null : levelViews[k];
    }

    public static void setVizLevel(int level) {
        vizLevel = level;
    }

    public static int vizLevel() {
        return Math.clamp(mips - 1, 0, vizLevel);
    }

    /** (Re)allocate the pyramid at the given size; no-op if already that size. */
    public static void ensure(int width, int height) {
        if (texture != null && w == width && h == height) {
            return;
        }
        close();
        // Mip count. IMPORTANT: GlDevice.createTexture allocates each level with the RAW shift
        // (width >> i, height >> i) — no max(1, ...) clamp — so a level becomes 0-sized (and the whole
        // texture mip-INCOMPLETE → textureLod returns black) as soon as i exceeds log2(min(w,h)). So the
        // count is limited by the SMALLER dimension, not counted down to 1x1 off the larger one (which
        // over-counts for non-square/16:9 sizes and silently breaks the pyramid).
        int m = 1;
        while ((width >> m) > 0 && (height >> m) > 0) {
            m++;
        }
        final int levels = m;
        var device = RenderSystem.getDevice();
        texture = device.createTexture(() -> "DU Hi-Z pyramid", USAGE, TextureFormat.RGBA8, width, height, 1, levels);
        fullView = device.createTextureView(texture);
        levelViews = new GpuTextureView[levels];
        for (int k = 0; k < levels; k++) {
            levelViews[k] = device.createTextureView(texture, k, 1);
        }
        w = width;
        h = height;
        mips = levels;
    }

    public static void close() {
        // Deferred close (see DUGpuTrash): a resize can reallocate while ImGuiMC still holds a view.
        if (levelViews != null) {
            for (GpuTextureView v : levelViews) {
                DUGpuTrash.retire(null, v);
            }
            levelViews = null;
        }
        DUGpuTrash.retire(texture, fullView);
        texture = null;
        fullView = null;
        w = -1;
        h = -1;
        mips = 0;
    }
}
