package studio.elysium.dragonuniverse.world.planet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * <p>All values are render-side authoring inputs; none of them drive world generation. A later
 * dimension-export phase ignores this record entirely (it reads only body identity / radius /
 * surface from {@link PlanetDef}).</p>
 *
 * @param size               shell thickness as a multiple of the planet radius (e.g. {@code 0.1}
 *                           = atmosphere extends 10% past the surface).
 * @param falloff            exponential density falloff with altitude through the shell.
 * @param density            base optical density of the shell.
 * @param scatterCoefficients per-channel Rayleigh scattering coefficients (RGB), the wavelength
 *                           tint of the scattered light.
 * @param brightnessFactor   overall scattered-light brightness multiplier.
 * @param densityFactor      multiplier applied on top of {@code density} (kept separate so the
 *                           editor can scrub "thickness" and "opacity" independently).
 */
public record AtmosphereDef(
        float size,
        float falloff,
        float density,
        Vector3f scatterCoefficients,
        float brightnessFactor,
        float densityFactor
) {
    /** Vector3f-typed view of {@link ExtraCodecs#VECTOR3F} (yields the immutable {@link Vector3fc}). */
    static final Codec<Vector3f> VECTOR3F = ExtraCodecs.VECTOR3F.xmap(Vector3f::new, v -> v);

    /** Earth-like default so a freshly-spawned planet reads sensibly before any editing. */
    public static final AtmosphereDef DEFAULT = new AtmosphereDef(
            0.1f, 4.0f, 1.0f, new Vector3f(0.18f, 0.46f, 1.0f), 1.0f, 1.0f);

    public static final Codec<AtmosphereDef> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("size", DEFAULT.size).forGetter(AtmosphereDef::size),
            Codec.FLOAT.optionalFieldOf("falloff", DEFAULT.falloff).forGetter(AtmosphereDef::falloff),
            Codec.FLOAT.optionalFieldOf("density", DEFAULT.density).forGetter(AtmosphereDef::density),
            VECTOR3F.optionalFieldOf("scatter_coefficients", DEFAULT.scatterCoefficients).forGetter(AtmosphereDef::scatterCoefficients),
            Codec.FLOAT.optionalFieldOf("brightness_factor", DEFAULT.brightnessFactor).forGetter(AtmosphereDef::brightnessFactor),
            Codec.FLOAT.optionalFieldOf("density_factor", DEFAULT.densityFactor).forGetter(AtmosphereDef::densityFactor)
    ).apply(instance, AtmosphereDef::new));

    /** Defensive copy so the immutable contract holds even though Vector3f is mutable. */
    public AtmosphereDef {
        scatterCoefficients = new Vector3f(scatterCoefficients);
    }
}
