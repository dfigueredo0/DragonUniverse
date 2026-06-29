package studio.elysium.dragonuniverse.world.planet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Optional flat ring/annulus around a planet, sampled by radius in the orbital shader (a second
 * disc intersection). Purely render-side; ignored by dimension export.
 *
 * @param texture     ring texture sampled across {@code [innerRadius, outerRadius]}.
 * @param innerRadius inner edge, in the same world units as {@link PlanetDef#radius()}.
 * @param outerRadius outer edge, in the same world units as {@link PlanetDef#radius()}.
 * @param tilt        optional tilt of the ring plane in degrees; absent = aligned to the planet's
 *                    equatorial plane.
 */
public record RingDef(
        Identifier texture,
        float innerRadius,
        float outerRadius,
        Optional<Float> tilt
) {
    public static final Codec<RingDef> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("texture").forGetter(RingDef::texture),
            Codec.FLOAT.fieldOf("inner_radius").forGetter(RingDef::innerRadius),
            Codec.FLOAT.fieldOf("outer_radius").forGetter(RingDef::outerRadius),
            Codec.FLOAT.optionalFieldOf("tilt").forGetter(RingDef::tilt)
    ).apply(instance, RingDef::new));
}
