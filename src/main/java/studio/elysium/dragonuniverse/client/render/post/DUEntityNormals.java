package studio.elysium.dragonuniverse.client.render.post;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.GL30C;
import studio.elysium.dragonuniverse.DragonUniverse;

/**
 * Vanilla-pass geometry normals for the look stack. Passes render through Blaze3D's
 * {@link RenderPipeline}/core shaders and the generic GL render-pass path. MC 26.1 exposes
 * <b>no MRT render-pass API</b> (a render pass takes a single color texture), so this drives the second
 * color attachment from <i>below</i> the abstraction, via mixins on the GL command encoder / render pass.
 *
 * <p>Two vanilla shaders are handled (the mixins are thin shims forwarding here):</p>
 * <ul>
 *   <li><b>{@code core/entity}</b> (every {@code entity_*} pipeline) — entities/block-entities. Only the
 *       combined {@code ModelViewMat} is available, so the cheap output is a <b>view-space</b> normal,
 *       tagged with coverage code {@link #ENTITY_COVERAGE_CODE} (~0.667). {@code du_ssao.fsh}/{@code
 *       du_rim.fsh} branch on the tag to convert view↔world. Translucent entity passes are excluded.</li>
 *   <li><b>{@code core/rendertype_clouds}</b> — clouds (Fancy/Fast only; in Fabulous, clouds render to a
 *       separate target this never sees). Cloud faces are flat & axis-aligned, so {@code cross(dFdx,
 *       dFdy)} of the world position is the exact <b>world-space</b> normal, tagged {@code 1.0} like
 *       terrain (no consumer branch). Clouds blend ({@code TRANSLUCENT}); the blend is disabled on draw
 *       buffer 1 only (see {@code GlCommandEncoderNormalScopeMixin}'s {@code applyPipelineState} hook) so
 *       the normal output is written unblended.</li>
 * </ul>
 *
 * <p><b>MRT scoping:</b> {@link #onSetPipeline} attaches {@link DUNormalBuffer} as {@code
 * COLOR_ATTACHMENT1} + MRT draw buffers only while a handled, ready pipeline draws to the <i>main</i>
 * target; {@link #onClosePass} restores the single draw buffer at pass close (before the GL backend
 * rebinds the default framebuffer). State is per-shader so we never enable MRT for a program lacking the
 * location=1 output.</p>
 *
 * <p>Render-thread only; no synchronization. Everything is fail-soft: an anchor miss or GL throw disables
 * vanilla normals without touching Sodium terrain normals or the rest of the look stack.</p>
 */
public final class DUEntityNormals {
    private DUEntityNormals() {}

    /** Vanilla shader shared by every {@code entity_*} pipeline. */
    private static final String ENTITY_SHADER_PATH = "core/entity";
    /** Vanilla shader shared by the {@code clouds} / {@code flat_clouds} pipelines. */
    private static final String CLOUD_SHADER_PATH = "core/rendertype_clouds";

    /**
     * Coverage-alpha code an entity fragment writes to mark a <b>view-space</b> normal. Consumers split
     * on the tag: {@code a > 0.83} → world-space (terrain/clouds, ~1.0); {@code 0.5 < a ≤ 0.83} →
     * view-space (entity, ~0.667). Kept as a string so it drops straight into the GLSL.
     */
    public static final String ENTITY_COVERAGE_CODE = "0.6666667";
    /**
     * Clouds write world-space normals but use a distinct coverage code (~0.333, code 1) so consumers can
     * tell them apart from terrain: SSAO's {@code a < 0.5} gate then auto-excludes clouds (no AO darkening
     * on distant clouds), while {@code du_rim.fsh} routes clouds to the silver-lining branch instead of
     * the front-lit rim. Still world-space (like terrain), so no normal-space conversion.
     */
    public static final String CLOUD_COVERAGE_CODE = "0.3333333";

    // Per-shader readiness — flipped on a successful inject, tripped on an anchor miss (markFailed). Until
    // both stages of a shader are confirmed we never enable its MRT (the program would lack location=1).
    private static volatile boolean entityVshReady = false;
    private static volatile boolean entityFshReady = false;
    private static volatile boolean cloudVshReady = false;
    private static volatile boolean cloudFshReady = false;
    private static volatile boolean shaderFailed = false;

    // MRT scope state (render thread only).
    private static boolean mrtActive = false;        // attachment 1 + MRT draw buffers currently bound
    private static boolean mainTargetBound = false;   // the active render pass targets the main color

    // One-shot FBO completeness probe: after the first attach, verify the main FBO is actually MRT-
    // complete. If not, drawing it would raise GL_INVALID_FRAMEBUFFER_OPERATION on every draw (a per-
    // frame HIGH-severity error flood), so we log the exact status and disable vanilla normals.
    private static final int GL_FRAMEBUFFER = 0x8D40;
    private static final int GL_FRAMEBUFFER_COMPLETE = 0x8CD5;
    private static boolean completenessChecked = false;

    /** Whether the MRT (attachment 1) is currently bound — read by the per-attachment blend-disable hook. */
    public static boolean isMrtActive() {
        return mrtActive;
    }

    private static void markFailed(String detail) {
        if (!shaderFailed) {
            shaderFailed = true;
            DragonUniverse.LOGGER.warn("[Dragon Universe] Vanilla normal inject/scoping failed ({}); "
                    + "vanilla-pass normals disabled (entities/clouds get no AO/rim). Terrain normals & "
                    + "the rest of the look stack are unaffected.", detail);
        }
    }

    // ---- shader injection (called from GlDeviceShaderInjectMixin) -------------------------------------

    /** Post-process a handled vanilla shader to emit its normal output; pass everything else through. */
    public static String injectShader(Identifier id, ShaderType type, String source) {
        if (source == null || id == null) {
            return source;
        }
        try {
            String path = id.getPath();
            if (ENTITY_SHADER_PATH.equals(path)) {
                return type == ShaderType.VERTEX ? injectEntityVertex(source)
                        : type == ShaderType.FRAGMENT ? injectEntityFragment(source) : source;
            }
            if (CLOUD_SHADER_PATH.equals(path)) {
                return type == ShaderType.VERTEX ? injectCloudVertex(source)
                        : type == ShaderType.FRAGMENT ? injectCloudFragment(source) : source;
            }
        } catch (Throwable t) {
            markFailed("inject threw: " + t.getClass().getSimpleName());
        }
        return source;
    }

    private static String injectEntityVertex(String src) {
        final String declAnchor = "out vec2 texCoord0;";
        final String posAnchor = "gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);";
        if (!src.contains(declAnchor) || !src.contains(posAnchor) || !src.contains("in vec3 Normal;")) {
            markFailed("entity vsh anchors missing");
            return src;
        }
        // ModelViewMat is camera*model, so mat3(ModelViewMat) * Normal is the view-space normal
        // (normalized in the fragment stage). No view→world matrix is available here — hence view-space.
        String out = src
                .replace(declAnchor, declAnchor + "\nout vec3 du_vNormal;")
                .replace(posAnchor, posAnchor + "\n    du_vNormal = mat3(ModelViewMat) * Normal;");
        entityVshReady = true;
        return out;
    }

    private static String injectEntityFragment(String src) {
        final String declAnchor = "out vec4 fragColor;";
        final String mainAnchor = "void main() {";
        if (!src.contains(declAnchor) || !src.contains(mainAnchor)) {
            markFailed("entity fsh anchors missing");
            return src;
        }
        // Writing at the top of main is safe: a later `discard` (alpha-cutout / dissolve) culls ALL of
        // the fragment's outputs, this normal included, so cut-out holes keep coverage 0.
        String write = "\n    du_Normal = vec4(normalize(du_vNormal) * 0.5 + 0.5, " + ENTITY_COVERAGE_CODE + ");";
        String out = src
                .replace(declAnchor, declAnchor + "\nlayout(location = 1) out vec4 du_Normal;\nin vec3 du_vNormal;")
                .replace(mainAnchor, mainAnchor + write);
        entityFshReady = true;
        return out;
    }

    private static String injectCloudVertex(String src) {
        final String declAnchor = "out vec4 vertexColor;";
        final String posAnchor = "gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);";
        if (!src.contains(declAnchor) || !src.contains(posAnchor)) {
            markFailed("cloud vsh anchors missing");
            return src;
        }
        // `pos` is the camera-relative world position of the cloud face vertex (same world-aligned frame
        // as Sodium terrain's d_WorldPos), so dFdx/dFdy of it in the fragment stage gives a world-space
        // normal — translation-invariant, so camera-relative vs absolute doesn't matter for direction.
        String out = src
                .replace(declAnchor, declAnchor + "\nout vec3 du_cloudPos;")
                .replace(posAnchor, posAnchor + "\n    du_cloudPos = pos;");
        cloudVshReady = true;
        return out;
    }

    private static String injectCloudFragment(String src) {
        final String declAnchor = "out vec4 fragColor;";
        final String mainAnchor = "void main() {";
        if (!src.contains(declAnchor) || !src.contains(mainAnchor)) {
            markFailed("cloud fsh anchors missing");
            return src;
        }
        // Flat axis-aligned cloud faces: cross(dFdx, dFdy) of the world position is the exact face normal.
        // Same sign convention as the Sodium terrain inject (verified +Y top). World-space normal, but a
        // distinct coverage code (~0.333) so SSAO excludes clouds and rim routes them to silver-lining.
        // The blend on draw buffer 1 is disabled around cloud draws so this write isn't alpha-blended.
        String write = "\n    du_Normal = vec4(normalize(cross(dFdx(du_cloudPos), dFdy(du_cloudPos))) * 0.5 + 0.5, "
                + CLOUD_COVERAGE_CODE + ");";
        String out = src
                .replace(declAnchor, declAnchor + "\nlayout(location = 1) out vec4 du_Normal;\nin vec3 du_cloudPos;")
                .replace(mainAnchor, mainAnchor + write);
        cloudFshReady = true;
        return out;
    }

    // ---- MRT scoping (called from the GL backend mixins) ---------------------------------------------

    /**
     * A new render pass is about to bind its FBO: record whether it targets the main color (the only
     * target we ever enable MRT for). We do NOT detach here — at this point the previous pass has already
     * run {@code finishRenderPass}, which binds the <i>default</i> framebuffer (0), so a draw-buffer/
     * attachment call would hit FBO 0 and raise GL_INVALID_OPERATION. Cleanup is in {@link #onClosePass()}.
     */
    public static void onCreateRenderPass(GpuTextureView colorTexture) {
        mainTargetBound = isMainColor(colorTexture);
    }

    /**
     * A render pass is closing. If it left MRT enabled, detach now — we're at {@code close()} HEAD, before
     * {@code finishRenderPass} rebinds the default framebuffer, so the pass's own (main) FBO is still
     * bound and the restore lands on the right target.
     */
    public static void onClosePass() {
        if (mrtActive) {
            safeDetach();
            mrtActive = false;
        }
    }

    /**
     * The active pipeline changed. Enable MRT iff it's a handled, ready vanilla normal pipeline drawing to
     * the main target and the look stack is on; otherwise restore the single color draw buffer.
     * State-tracked so we only touch GL on a transition.
     */
    public static void onSetPipeline(RenderPipeline pipeline) {
        boolean want = mainTargetBound && DUPostChain.isEnabled() && isNormalPipeline(pipeline);
        if (want && !mrtActive) {
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            if (main == null || main.width <= 0 || main.height <= 0) {
                return;
            }
            try {
                DUNormalBuffer.attach(main.width, main.height);
                mrtActive = true;
                if (!completenessChecked) {
                    completenessChecked = true;
                    int status = GL30C.glCheckFramebufferStatus(GL_FRAMEBUFFER);
                    if (status != GL_FRAMEBUFFER_COMPLETE) {
                        DUNormalBuffer.detach();
                        mrtActive = false;
                        markFailed("MRT FBO incomplete: 0x" + Integer.toHexString(status));
                    }
                }
            } catch (Throwable t) {
                markFailed("attach threw: " + t.getClass().getSimpleName());
            }
        } else if (!want && mrtActive) {
            safeDetach();
            mrtActive = false;
        }
    }

    /** True for a ready {@code core/entity} (non-translucent) or {@code core/rendertype_clouds} pipeline. */
    private static boolean isNormalPipeline(RenderPipeline pipeline) {
        if (pipeline == null || shaderFailed) {
            return false;
        }
        Identifier vsh = pipeline.getVertexShader();
        if (vsh == null) {
            return false;
        }
        String path = vsh.getPath();
        if (ENTITY_SHADER_PATH.equals(path)) {
            if (!entityVshReady || !entityFshReady) {
                return false;
            }
            Identifier loc = pipeline.getLocation();   // exclude blended entity passes (entity_shadow etc.
            return loc == null || !loc.getPath().contains("translucent");   // also excluded: wrong shader)
        }
        if (CLOUD_SHADER_PATH.equals(path)) {
            return cloudVshReady && cloudFshReady;      // clouds blend, but draw buffer 1 blend is disabled
        }
        return false;
    }

    private static void safeDetach() {
        try {
            DUNormalBuffer.detach();
        } catch (Throwable t) {
            markFailed("detach threw: " + t.getClass().getSimpleName());
        }
    }

    private static boolean isMainColor(GpuTextureView view) {
        try {
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            return main != null && view != null && view == main.getColorTextureView();
        } catch (Throwable t) {
            return false;
        }
    }
}
