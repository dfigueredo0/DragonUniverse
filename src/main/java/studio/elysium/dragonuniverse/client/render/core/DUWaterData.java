package studio.elysium.dragonuniverse.client.render.core;

import org.joml.Vector3f;

/**
 * Water-material parameters for the depth-aware water injected into Sodium's translucent terrain
 * pass (see {@code mixin/sodium/SodiumShaderLoaderMixin} for the GLSL inject and
 * {@code client/render/water/DUWater} for the per-frame uniform binding).
 *
 * <p>Unlike the look-stack UBOs ({@link DULookData}), these are <b>not</b> a std140 buffer: we
 * bind them as plain GL uniforms straight onto Sodium's shared terrain program, whose UBO binding space
 * we don't own. So this class is just a tunable holder the debug panel writes and {@code DUWater} reads.</p>
 *
 * <p>All values are independent, clean-room defaults (standard depth-fade / fresnel / foam constants);</p>
 */
public final class DUWaterData {
    private DUWaterData() {}

    public static boolean enabled = true;

    // --- Depth-fade material (drives surface alpha + fog from the through-water path length) ---
    /** Exponential falloff rate for surface alpha vs. the view-ray path through water (per block). */
    public static float alphaK = 0.35F;
    /** Shoreline clarity floor (thin water never fully opaque). */
    public static float alphaMin = 0.18F;
    /** Deep-water opacity ceiling. */
    public static float alphaMax = 0.92F;
    /** Exponential falloff rate for the deep-water fog tint (per block). */
    public static float fogDensity = 0.18F;
    /** Tint deep water settles toward as the path length grows. */
    public static final Vector3f deepColor = new Vector3f(0.015F, 0.11F, 0.16F);
    /** Multiplier applied to the lit scene colour for shallow (clear) water. */
    public static final Vector3f shallowColor = new Vector3f(0.55F, 0.85F, 0.92F);

    // --- Shoreline foam (from the world-up Y-delta between the water surface and the geometry behind) ---
    /** Vertical separation (blocks) over which the foam band fades out. */
    public static float foamWidth = 0.55F;
    /** Foam colour. */
    public static final Vector3f foamColor = new Vector3f(0.95F, 0.98F, 1.0F);

    // --- Fresnel + sun glint ---
    /** Water's normal-incidence reflectance (~2%). */
    public static float fresnelF0 = 0.02F;
    /** Specular exponent for the fresnel-weighted sun highlight. */
    public static float glint = 220.0F;

    // --- Multi-octave normal perturbation (small-scale surface detail) ---
    /** Base ripple spatial frequency (cycles per block). */
    public static float normalScale = 0.35F;
    /** Slope amount of the perturbation (scaled per-pixel by fresnel + skylight). */
    public static float normalStrength = 1.4F;
    /** Scroll speed of the octaves over time. */
    public static float normalSpeed = 0.6F;

    // --- Gerstner vertex displacement; analytic normal feeds the fsh ---
    /** Whether the water surface is geometrically displaced by the Gerstner sum. */
    public static boolean wavesEnabled = true;
    /** Base wave amplitude (blocks) — the longest wave; shorter waves scale down from this. */
    public static float waveAmplitude = 0.12F;
    /** Base wavelength (blocks) — the longest wave; shorter waves scale down from this. */
    public static float waveLength = 6.0F;
    /** Phase speed multiplier (1 ≈ deep-water dispersion). */
    public static float waveSpeed = 1.0F;
    /** Steepness/sharpness of the trochoidal crests (0 = round sine, →1 = sharp; clamped to avoid loops). */
    public static float waveSteepness = 0.5F;

    // --- Underwater surface (camera below the surface: surface seen from below) ---
    /** Tint the underside reflects toward at grazing angles (total internal reflection). */
    public static final Vector3f underColor = new Vector3f(0.02F, 0.10F, 0.14F);
    /** Inverted-fresnel exponent: lower = sharper rise to total internal reflection at grazing. */
    public static float underFresnelPow = 3.0F;
    /** Underside alpha looking straight up (transmissive). */
    public static float underAlphaMin = 0.25F;
    /** Underside alpha at grazing (near-opaque mirror). */
    public static float underAlphaMax = 0.92F;

    // --- Underwater scene fog (a look-stack pass, NOT the surface material) ---
    /** Whether the submerged scene is tinted by distance toward the underwater colour. */
    public static boolean underwaterFog = true;
    /** Colour the submerged scene fades toward with distance. */
    public static final Vector3f underwaterFogColor = new Vector3f(0.02F, 0.10F, 0.16F);
    /** Exponential underwater fog density (per block). */
    public static float underwaterFogDensity = 0.08F;

    // --- Ripple simulation (persistent ping-pong height field, world-anchored) ---
    /** Whether the sim runs, displaces the surface, and is layered into the water normal. */
    public static boolean rippleEnabled = true;
    /** Velocity damping per step (energy loss; <1 so ripples fade). */
    public static float rippleDamping = 0.996F;
    /** Lattice propagation coefficient C = (c·dt/dx)² — stability requires < 0.5. */
    public static float ripplePropagation = 0.25F;
    /** Slow per-step height decay so still water settles flat. */
    public static float rippleHeightDecay = 0.999F;
    /** Slope gain when the sim height field is turned into a normal perturbation in the water material. */
    public static float rippleNormalStrength = 6.0F;
    /** World-Y displacement applied to the water surface per unit of simulated height (vertex stage). */
    public static float rippleDisplaceScale = 0.2F;
    /** Debug/source injection amplitude. */
    public static float rippleInjectStrength = 0.6F;
    /** Debug/source injection radius, in world blocks. */
    public static float rippleInjectRadius = 1.0F;
    /** Debug: continuously inject at the captured test-source world point (steady ripple rings). */
    public static boolean rippleTestSource = false;

    // --- Real disturbance sources ---
    /** Moving entities in water leave a wake (a per-frame disturbance at their feet, scaled by speed). */
    public static boolean wakesEnabled = true;
    /** Wake amplitude per (block/tick) of an entity's horizontal speed. */
    public static float wakeStrength = 4.0F;
    /** Wake disturbance radius, in world blocks. */
    public static float wakeRadius = 0.8F;
    /** Minimum horizontal speed (blocks/tick) below which an entity makes no wake. */
    public static float wakeSpeedThreshold = 0.02F;

    /** Player charge radiates ripples from under them onto the water below (event-driven). */
    public static boolean chargeEnabled = true;
    /** Debug: force the "charging" state on (otherwise driven by the player using an item). */
    public static boolean debugCharge = false;
    /** Charge-ripple amplitude per frame while charging. */
    public static float chargeStrength = 0.5F;
    /** Charge-ripple radius, in world blocks. */
    public static float chargeRadius = 1.5F;
}
