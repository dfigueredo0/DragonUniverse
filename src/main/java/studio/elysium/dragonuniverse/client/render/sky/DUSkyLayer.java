package studio.elysium.dragonuniverse.client.render.sky;

import net.minecraft.resources.Identifier;

/**
 * One compositing layer of a skybox. Either a procedural type (starfield/nebula/
 * atmosphere) or a texture-backed equirectangular panorama. Mutable so the ImGui panel can
 * toggle/tune it live.
 */
public final class DUSkyLayer {
    public enum Type {
        STARFIELD,   // additive procedural points
        NEBULA,      // additive procedural fbm clouds
        ATMOSPHERE,  // alpha procedural gradient + sun glow
        TEXTURE,     // authored equirectangular panorama (sampled by view direction)
        CUBEMAP      // authored 6-face cubemap (sampled by view direction)
    }

    public final Type type;
    /** For {@link Type#TEXTURE} the equirect panorama; for {@link Type#CUBEMAP} the face base id. */
    public final Identifier texture;
    /** TEXTURE/CUBEMAP only: additive blend if true, premultiplied-alpha blend if false. */
    public final boolean additive;

    public boolean enabled = true;
    public float r, g, b;
    public float opacity;
    public float rotation;  // radians, spun around the vertical axis (drift / sunrise rotation)
    public float scale;     // procedural feature scale (unused by TEXTURE)
    public float seed;

    /** Procedural layer. */
    public DUSkyLayer(Type type, float r, float g, float b, float opacity, float scale) {
        this.type = type;
        this.texture = null;
        this.additive = false;
        this.r = r;
        this.g = g;
        this.b = b;
        this.opacity = opacity;
        this.scale = scale;
        this.rotation = 0f;
        this.seed = 0f;
    }

    /** Texture-backed (equirectangular) layer; rgb is a tint multiplier. */
    public DUSkyLayer(Identifier texture, boolean additive, float r, float g, float b, float opacity) {
        this(Type.TEXTURE, texture, additive, r, g, b, opacity);
    }

    /** Cubemap layer; {@code base} resolves faces {@code base_0.png}..{@code _5.png}. rgb tints. */
    public static DUSkyLayer cubemap(Identifier base, boolean additive, float r, float g, float b, float opacity) {
        return new DUSkyLayer(Type.CUBEMAP, base, additive, r, g, b, opacity);
    }

    private DUSkyLayer(Type type, Identifier texture, boolean additive, float r, float g, float b, float opacity) {
        this.type = type;
        this.texture = texture;
        this.additive = additive;
        this.r = r;
        this.g = g;
        this.b = b;
        this.opacity = opacity;
        this.scale = 1f;
        this.rotation = 0f;
        this.seed = 0f;
    }
}
