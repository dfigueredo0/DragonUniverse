package studio.elysium.dragonuniverse.client.render.sky;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import studio.elysium.dragonuniverse.DragonUniverse;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Lazily builds + caches a GL cubemap texture from six face PNGs, mirroring vanilla's
 * {@code CubeMapTexture} (same face→suffix mapping so orientation matches). Faces are
 * {@code <base>_0.png}..{@code _5.png}.
 */
public final class DUCubemap {
    private DUCubemap() {}

    // Maps cubemap face index 0..5 (+X,-X,+Y,-Y,+Z,-Z) to face-file suffix, matching vanilla.
    private static final String[] SUFFIXES = { "_1.png", "_3.png", "_5.png", "_4.png", "_0.png", "_2.png" };

    private static final Map<Identifier, GpuTextureView> CACHE = new HashMap<>();
    private static final Map<Identifier, GpuTexture> TEXTURES = new HashMap<>();

    /** Returns the cubemap view for {@code base}, loading it once. Null if the faces failed to load. */
    public static GpuTextureView getOrLoad(Identifier base) {
        if (CACHE.containsKey(base)) {
            return CACHE.get(base);
        }
        GpuTextureView view = tryLoad(base); // may be null
        CACHE.put(base, view); // cache even null so we don't retry every frame
        return view;
    }

    private static GpuTextureView tryLoad(Identifier base) {
        ResourceManager rm = Minecraft.getInstance().getResourceManager();
        NativeImage[] faces = new NativeImage[6];
        try {
            int w = -1;
            int h = -1;
            for (int i = 0; i < 6; i++) {
                Resource res = rm.getResource(base.withSuffix(SUFFIXES[i])).orElseThrow();
                try (InputStream in = res.open()) {
                    faces[i] = NativeImage.read(in);
                }
                if (i == 0) {
                    w = faces[0].getWidth();
                    h = faces[0].getHeight();
                }
            }

            GpuDevice device = RenderSystem.getDevice();
            GpuTexture texture = device.createTexture(() -> base + " cubemap",
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_CUBEMAP_COMPATIBLE,
                    TextureFormat.RGBA8, w, h, 6, 1);
            GpuTextureView view = device.createTextureView(texture);
            for (int i = 0; i < 6; i++) {
                device.createCommandEncoder().writeToTexture(texture, faces[i], 0, i, 0, 0, w, h, 0, 0);
            }
            TEXTURES.put(base, texture);
            return view;
        } catch (Exception e) {
            DragonUniverse.LOGGER.warn("[Dragon Universe] Failed to load cubemap {} (need {}_0.png .. _5.png)", base, base, e);
            return null;
        } finally {
            for (NativeImage img : faces) {
                if (img != null) {
                    img.close();
                }
            }
        }
    }

    public static void closeAll() {
        for (GpuTextureView view : CACHE.values()) {
            if (view != null) {
                view.close();
            }
        }
        for (GpuTexture texture : TEXTURES.values()) {
            texture.close();
        }
        CACHE.clear();
        TEXTURES.clear();
    }
}
