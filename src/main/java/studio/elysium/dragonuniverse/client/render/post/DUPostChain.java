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
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import studio.elysium.dragonuniverse.DragonUniverse;
import studio.elysium.dragonuniverse.client.render.core.DUFrameData;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * <p>Hand-roll rather than vanilla's JSON {@code PostChain} because that system bakes its
 * custom uniforms once at load time and never refreshes them — there is no hook to feed a
 * per-frame dynamic UBO like {@link DUFrameData}. Each step mirrors {@code PostPass}'s draw block:
 * open a {@link RenderPass}, bind samplers/UBO, draw a vertexless fullscreen triangle.</p>
 *
 * <p>Per-frame chain (all fullscreen):</p>
 * <ol>
 *   <li>copy main color → {@code sceneCopy}</li>
 *   <li><b>fog</b>: sceneCopy + main depth → main color</li>
 *   <li>copy fogged main color → {@code sceneCopy}</li>
 *   <li><b>bright-extract</b>: sceneCopy → {@code bloomA} (half-res)</li>
 *   <li><b>blur H</b>: bloomA → bloomB</li>
 *   <li><b>blur V</b>: bloomB → bloomA</li>
 *   <li><b>composite</b>: sceneCopy + bloomA (+ DUFrame UBO) → main color</li>
 * </ol>
 */
@EventBusSubscriber(modid = DragonUniverse.MODID, value = Dist.CLIENT)
public final class DUPostChain {
    private DUPostChain() {}

    private static boolean enabled = false;
    private static boolean warnedOnce = false;

    private static final PostTarget sceneCopy =
            new PostTarget("DU scene copy", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING);
    private static final PostTarget bloomA =
            new PostTarget("DU bloom A", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING);
    private static final PostTarget bloomB =
            new PostTarget("DU bloom B", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING);
    private static final PostTarget depthViz =
            new PostTarget("DU depth viz", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING);
    // A controlled copy of the final composited frame for the inspector. We never hand ImGui the
    // live main render target view — Minecraft closes it on resize, which crashes ImGui's deferred
    // draw ("Texture view ... has been closed"). Copies into a texture we own dodge that entirely.
    private static final PostTarget presentCopy =
            new PostTarget("DU present copy", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING);

    public static boolean isEnabled() {
        return enabled;
    }

    /** The fogged scene snapshot (composite base). Null until the chain has run once. */
    public static GpuTextureView sceneCopyView() {
        return sceneCopy.view;
    }

    /** The final blurred bloom buffer (half-res). Null until the chain has run once. */
    public static GpuTextureView bloomView() {
        return bloomA.view;
    }

    /** Linearized grayscale depth (half-res), for the Target Inspector. Null until run once. */
    public static GpuTextureView depthVizView() {
        return depthViz.view;
    }

    /** A copy of the final composited frame, for the Target Inspector. Null until run once. */
    public static GpuTextureView presentView() {
        return presentCopy.view;
    }

    /** Enabling also flips {@link DUFrameData#postProcessingEnabled} so the UBO is uploaded. */
    public static void setEnabled(boolean value) {
        enabled = value;
        DUFrameData.postProcessingEnabled = value;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent.AfterLevel event) {
        if (!enabled) {
            return;
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
        int bw = Math.max(1, w / 2);
        int bh = Math.max(1, h / 2);

        sceneCopy.ensure(w, h);
        bloomA.ensure(bw, bh);
        bloomB.ensure(bw, bh);
        depthViz.ensure(bw, bh);
        presentCopy.ensure(w, h);

        try {
            CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
            GpuSampler clamp = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

            // 1) snapshot scene
            enc.copyTextureToTexture(mainColor, sceneCopy.texture, 0, 0, 0, 0, 0, w, h);

            // 2) depth fog -> main
            runPass(enc, "DU fog", mainColorView, DUPostPipelines.FOG, pass -> {
                pass.setUniform("DUFrame", DUFrameData.slice());
                pass.bindTexture("InSampler", sceneCopy.view, clamp);
                pass.bindTexture("DepthSampler", mainDepthView, clamp);
            });

            // 3) re-snapshot fogged scene as the bloom source + composite base
            enc.copyTextureToTexture(mainColor, sceneCopy.texture, 0, 0, 0, 0, 0, w, h);

            // 4) bright-extract -> bloomA (half-res)
            runPass(enc, "DU bright", bloomA.view, DUPostPipelines.BRIGHT,
                    pass -> pass.bindTexture("InSampler", sceneCopy.view, clamp));

            // 5) blur horizontal -> bloomB
            runPass(enc, "DU blurH", bloomB.view, DUPostPipelines.BLUR_H,
                    pass -> pass.bindTexture("InSampler", bloomA.view, clamp));

            // 6) blur vertical -> bloomA
            runPass(enc, "DU blurV", bloomA.view, DUPostPipelines.BLUR_V,
                    pass -> pass.bindTexture("InSampler", bloomB.view, clamp));

            // 7) composite -> main
            runPass(enc, "DU composite", mainColorView, DUPostPipelines.COMPOSITE, pass -> {
                pass.setUniform("DUFrame", DUFrameData.slice());
                pass.bindTexture("InSampler", sceneCopy.view, clamp);
                pass.bindTexture("BloomSampler", bloomA.view, clamp);
            });

            // 8) debug: linearized depth visualization (doesn't touch the main target)
            runPass(enc, "DU depthviz", depthViz.view, DUPostPipelines.DEPTHVIZ, pass -> {
                pass.setUniform("DUFrame", DUFrameData.slice());
                pass.bindTexture("DepthSampler", mainDepthView, clamp);
            });

            // 9) debug: snapshot the final composited frame for the inspector (owned texture)
            enc.copyTextureToTexture(mainColor, presentCopy.texture, 0, 0, 0, 0, 0, w, h);
        } catch (Exception e) {
            // If AFTER_LEVEL turns out to be inside an open render pass on this platform, degrade
            // gracefully instead of crashing, and surface it once for diagnosis.
            if (!warnedOnce) {
                warnedOnce = true;
                DragonUniverse.LOGGER.warn("[Dragon Universe] Post chain failed; disabling. "
                        + "The AFTER_LEVEL hook may need to move.", e);
            }
            setEnabled(false);
        }
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
            if (view != null) {
                view.close();
                view = null;
            }
            if (texture != null) {
                texture.close();
                texture = null;
            }
            w = -1;
            h = -1;
        }
    }
}
