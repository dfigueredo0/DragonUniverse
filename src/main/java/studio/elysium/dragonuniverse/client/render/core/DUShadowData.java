package studio.elysium.dragonuniverse.client.render.core;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * std140 UBO for the Stage-B shadow receiver ({@code du_shadow_apply}). Holds the light view-projection
 * the map was drawn with plus <b>bob-inclusive</b> inverse camera matrices and the receiver tunables.
 *
 * <p>Layout (272 bytes): {@code mat4 lightVP} · {@code mat4 invProjBob} · {@code mat4 invViewBob} ·
 * {@code vec4 bias} (depthBias, normalOffset, slopeBias, strength) · {@code vec4 sun} (sunDir.xyz,
 * horizonFade) · {@code vec4 tint} (rgb, texelWorld) · {@code vec4 pcf} (radiusTexels, tapCount, penumbra,
 * pad) · {@code vec4 colored} (Stage D: strength, filterTaps, filterRadius, enabled).</p>
 *
 * <p><b>Why bob-inclusive inverses (not the shared {@code DUViewData}):</b> the look stack captures
 * bob-<i>less</i> camera matrices (fine for SSAO/rim), but the depth buffer has view-bob baked in, and
 * shadows are sensitive enough that the mismatch makes them bob as you walk. So the receiver reconstructs
 * world position with the live bob-ful projection + modelview captured at {@code AfterTranslucentParticles}
 * (where the camera pose is still active) — without disturbing the shared bob-less capture. {@code lightVP}
 * + sun come from {@code DUShadowPass} (the exact values it drew the map with).</p>
 */
public final class DUShadowData {
    public static final int STD140_SIZE = 272;

    // ---- Receiver tunables (debug panel) ----------------------------------------------------------
    public static boolean enabled = false;
    /** Tiny constant depth-compare nudge (normalized [0,1] depth) for stubborn acne. Keep ~0; the
     *  normal-offset below does the real work and is range-independent (this term scales with the range). */
    public static float depthBias = 0.0F;
    /** Normal-offset bias, BASE, in <b>shadow texels</b> (× the texel's world size in-shader). The main,
     *  range-independent acne lever — survives the render-distance-scaled depth range. */
    public static float normalOffset = 2.5F;
    /** Normal-offset bias, SLOPE, in shadow texels per unit grazing slope: extra push on faces edge-on to
     *  the sun (where acne lives) without over-pushing flat ground (which would peter-pan short casters). */
    public static float slopeBias = 1.5F;
    /** Shadow darkening amount (0 = none, 1 = full tint). */
    public static float strength = 0.6F;
    /** Shadow colour the lit scene is multiplied toward (Stage D will drive this from occluders). */
    public static final Vector3f tint = new Vector3f(0.05F, 0.06F, 0.10F);

    // ---- Stage C: PCF softening --------------------------------------------------------------------
    /** Filter half-extent in shadow-map texels. Bigger = softer/wider penumbra (and more aliasing reach). */
    public static float pcfRadius = 2.0F;
    /** PCF tap count (Vogel spiral). More = smoother penumbra, linearly more texture reads. */
    public static int pcfTaps = 16;
    /**
     * Distance-scaled penumbra (PCSS-lite). 0 = constant-radius PCF (one loop, default). &gt;0 runs a cheap
     * blocker search and widens the filter with the receiver→occluder depth gap (contact crisp, far casts
     * soft). Raw texels-per-unit-light-depth-gap dial — tune by eye in the panel; ~100–300 reads well at
     * default coverage/resolution.
     */
    public static float pcfPenumbra = 0.0F;

    // ---- Stage D: colored shadows (translucent occluders tint the sun instead of darkening) ---------
    /** Master enable. Gates BOTH the producer's translucent re-invoke and the receiver's colored sample. */
    public static boolean coloredEnabled = false;
    /** Sub-toggle: water participates as a colored-shadow caster (off → water writes no tint). */
    public static boolean waterColoredShadows = true;
    /** Opacity (tint alpha) written for water occluders — how strongly water tints its shadow. */
    public static float coloredWaterOpacity = 0.5F;
    /** Colored-shadow tint amount (0 = none). Kept low by default — the underwater fog already owns most
     *  of the water color, so this is a subtle additive, not a co-equal tint. */
    public static float coloredStrength = 0.5F;
    /** PCF tap count for the translucent/colored sample. Lower than the opaque PCF (cheaper); the soft edge
     *  comes from the coverage filter, the tint color is a single center tap. */
    public static int coloredFilterTaps = 8;
    /** PCF filter radius (texels) for the colored sample — tuned so the colored edge matches the opaque PCF
     *  penumbra rather than a hard line. */
    public static float coloredFilterRadius = 2.5F;
    /** Effective per-frame colored gate (set by {@code DUPostChain}: enabled AND the tint map is built this
     *  run). Drives {@code uColored.w} so the receiver never samples an unbuilt/garbage tint map. */
    public static boolean coloredRuntime = false;

    // Bob-inclusive inverse camera matrices, captured live at AfterTranslucentParticles.
    private static final Matrix4f invProjBob = new Matrix4f();
    private static final Matrix4f invViewBob = new Matrix4f();

    private static GpuBuffer buffer;
    private static ByteBuffer cpu;

    private DUShadowData() {}

    /**
     * Capture the camera matrices that actually rendered the terrain depth buffer — Sodium's own
     * {@code ChunkRenderMatrices} (projection + camera-relative modelview), grabbed in the shadow mixin.
     * Reconstructing with their inverses round-trips the depth buffer exactly, wherever view-bob lives
     * (26.1 bakes it into the projection, not the modelview), so shadows don't bob.
     */
    public static void captureCameraBob(Matrix4fc projection, Matrix4fc modelView) {
        invProjBob.set(projection).invert();
        invViewBob.set(modelView).invert();
    }

    private static void ensure() {
        if (buffer != null) {
            return;
        }
        cpu = MemoryUtil.memAlloc(STD140_SIZE);
        buffer = RenderSystem.getDevice().createBuffer(
                () -> "DragonUniverse DUShadow UBO",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                STD140_SIZE);
    }

    /** Upload with the EXACT light VP + sun the map was drawn with this frame (from DUShadowPass).
     *  {@code texelWorld} = the shadow map's world blocks-per-texel (2·coverage/resolution), so the receiver
     *  can size its normal-offset bias to the texel — range-independent acne control. */
    public static void upload(Matrix4f lightVP, Vector3f sunDir, float texelWorld) {
        ensure();
        // Fade shadows out as the sun nears/drops below the horizon (no direct sun => no cast shadows).
        float horizonFade = Math.clamp((sunDir.y - 0.05F) / 0.15F, 0.0F, 1.0F);
        cpu.clear();
        lightVP.get(0, cpu);            // column-major mat4 at the given byte offset (does not move position)
        invProjBob.get(64, cpu);
        invViewBob.get(128, cpu);
        cpu.position(192);
        cpu.putFloat(depthBias).putFloat(normalOffset).putFloat(slopeBias).putFloat(strength);
        cpu.putFloat(sunDir.x).putFloat(sunDir.y).putFloat(sunDir.z).putFloat(horizonFade);
        cpu.putFloat(tint.x).putFloat(tint.y).putFloat(tint.z).putFloat(texelWorld);
        cpu.putFloat(pcfRadius).putFloat(Math.max(1, pcfTaps)).putFloat(pcfPenumbra).putFloat(0.0F);
        // uColored: colored-shadow strength, colored PCF taps, colored PCF radius (texels), master enable.
        cpu.putFloat(coloredStrength).putFloat(Math.max(1, coloredFilterTaps))
                .putFloat(coloredFilterRadius).putFloat(coloredRuntime ? 1.0F : 0.0F);
        cpu.flip();
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(0, STD140_SIZE), cpu);
    }

    public static GpuBufferSlice slice() {
        ensure();
        return buffer.slice(0, STD140_SIZE);
    }

    public static void close() {
        if (buffer != null) {
            buffer.close();
            buffer = null;
        }
        if (cpu != null) {
            MemoryUtil.memFree(cpu);
            cpu = null;
        }
    }
}
