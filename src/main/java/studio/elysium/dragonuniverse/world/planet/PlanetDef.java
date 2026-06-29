package studio.elysium.dragonuniverse.world.planet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;

import java.util.Optional;

/**
 * Immutable, Codec-backed definition of one logical planetary body.
 *
 * <p>One {@code PlanetDef} is two representations of the same body: the far/orbital ray-marched
 * sphere (this phase) and, later, a real custom dimension you can stand on. The JSON is authored to
 * serve <em>both</em> — body identity ({@link #id}), {@link #radius}, and the reserved
 * {@link #surface} hook are kept stable and dimension-meaningful so a later phase can read the same
 * file to drive a dimension definition without a schema migration.</p>
 *
 * <p>This type is deliberately dist-agnostic (no client imports) so the export phase can load it
 * server-side. The renderer and ImGui editor consume it but stay client-only.</p>
 *
 * @param id         stable body identity (namespace:path). Drives the JSON filename and is the key
 *                   in {@link PlanetRegistry}.
 * @param texture    surface/albedo texture spherically mapped onto the orbital sphere.
 * @param radius     body radius in world units. Shared with the future dimension (per-body scale /
 *                   gravity reference), so do not treat it as render-only.
 * @param position   body center in world space (orbital placement).
 * @param atmosphere atmosphere shell authoring params; see {@link AtmosphereDef}.
 * @param ring       optional ring; see {@link RingDef}.
 * @param surface    reserved hook for the dimension-export phase: the dimension id this body's
 *                   surface maps to. Unused by the orbital renderer. See the TODO below.
 */
public record PlanetDef(
        Identifier id,
        Identifier texture,
        float radius,
        Vector3f position,
        AtmosphereDef atmosphere,
        Optional<RingDef> ring,
        // TODO Phase: dimension export — a later phase reads THIS field (plus id + radius) to bind the
        // orbital body to a real custom dimension at data/<modid>/dimension/<surface>.json and to
        // perform the orbital -> surface DimensionTransition. Reserved and persisted now; the renderer
        // and editor never read it. Do NOT repurpose id/radius/surface for render-only state.
        Optional<Identifier> surface
) {
    public static final Codec<PlanetDef> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("id").forGetter(PlanetDef::id),
            Identifier.CODEC.fieldOf("texture").forGetter(PlanetDef::texture),
            Codec.FLOAT.fieldOf("radius").forGetter(PlanetDef::radius),
            AtmosphereDef.VECTOR3F.fieldOf("position").forGetter(PlanetDef::position),
            AtmosphereDef.CODEC.optionalFieldOf("atmosphere", AtmosphereDef.DEFAULT).forGetter(PlanetDef::atmosphere),
            RingDef.CODEC.optionalFieldOf("ring").forGetter(PlanetDef::ring),
            Identifier.CODEC.optionalFieldOf("surface").forGetter(PlanetDef::surface)
    ).apply(instance, PlanetDef::new));

    /** Defensive copy so the immutable contract holds even though Vector3f is mutable. */
    public PlanetDef {
        position = new Vector3f(position);
    }

    public static PlanetDef createDefault(Identifier id, Vector3f position) {
        return new PlanetDef(
                id,
                Identifier.fromNamespaceAndPath(id.getNamespace(), "textures/planet/" + id.getPath() + ".png"),
                64.0f,
                new Vector3f(position),
                AtmosphereDef.DEFAULT,
                Optional.empty(),
                Optional.empty());
    }

    public PlanetDef withTexture(Identifier texture) {
        return new PlanetDef(id, texture, radius, position, atmosphere, ring, surface);
    }

    public PlanetDef withRadius(float radius) {
        return new PlanetDef(id, texture, radius, position, atmosphere, ring, surface);
    }

    public PlanetDef withPosition(Vector3f position) {
        return new PlanetDef(id, texture, radius, position, atmosphere, ring, surface);
    }

    public PlanetDef withAtmosphere(AtmosphereDef atmosphere) {
        return new PlanetDef(id, texture, radius, position, atmosphere, ring, surface);
    }

    public PlanetDef withRing(Optional<RingDef> ring) {
        return new PlanetDef(id, texture, radius, position, atmosphere, ring, surface);
    }

    public PlanetDef withId(Identifier id) {
        return new PlanetDef(id, texture, radius, position, atmosphere, ring, surface);
    }
}
