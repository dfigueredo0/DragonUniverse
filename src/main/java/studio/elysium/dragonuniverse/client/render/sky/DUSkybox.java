package studio.elysium.dragonuniverse.client.render.sky;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 5D — an ordered, combinable stack of sky layers composited bottom-to-top. Ships a couple
 * of example skyboxes (an airless starfield, a thick tinted atmosphere) selectable from ImGui.
 */
public final class DUSkybox {
    public final String name;
    private final List<DUSkyLayer> layers = new ArrayList<>();

    public DUSkybox(String name) {
        this.name = name;
    }

    public DUSkybox add(DUSkyLayer layer) {
        layers.add(layer);
        return this;
    }

    public List<DUSkyLayer> layers() {
        return layers;
    }

    /** Deep-space look: an opaque near-black base, a faint nebula wash, then a dense starfield. */
    public static DUSkybox airlessStarfield() {
        return new DUSkybox("Airless Starfield")
                // ATMOSPHERE with a near-black color + full opacity paints over the vanilla day sky.
                .add(new DUSkyLayer(DUSkyLayer.Type.ATMOSPHERE, 0.02f, 0.02f, 0.05f, 1.0f, 1f))
                .add(new DUSkyLayer(DUSkyLayer.Type.NEBULA, 0.25f, 0.10f, 0.45f, 0.35f, 2.2f))
                .add(new DUSkyLayer(DUSkyLayer.Type.STARFIELD, 1.0f, 1.0f, 1.0f, 1.0f, 120f));
    }

    /** Planetary look: a tinted atmosphere with sun glow over very faint stars. */
    public static DUSkybox thickAtmosphere() {
        DUSkyLayer stars = new DUSkyLayer(DUSkyLayer.Type.STARFIELD, 0.8f, 0.9f, 1.0f, 0.25f, 140f);
        return new DUSkybox("Thick Atmosphere")
                .add(stars)
                .add(new DUSkyLayer(DUSkyLayer.Type.ATMOSPHERE, 0.35f, 0.55f, 0.95f, 0.9f, 1f));
    }

    /**
     * Combinable example: an opaque black base + an authored equirect panorama (additive) +
     * procedural twinkling stars on top — demonstrates texture and procedural layers stacked.
     * Uses vanilla's end-sky texture as a placeholder; point it at a real equirect panorama.
     */
    public static DUSkybox texturedNebula() {
        Identifier panorama = Identifier.withDefaultNamespace("textures/environment/end_sky.png");
        return new DUSkybox("Textured Nebula")
                .add(new DUSkyLayer(DUSkyLayer.Type.ATMOSPHERE, 0.02f, 0.02f, 0.05f, 1.0f, 1f))
                .add(new DUSkyLayer(panorama, true, 0.7f, 0.6f, 1.0f, 0.9f))
                .add(new DUSkyLayer(DUSkyLayer.Type.STARFIELD, 1.0f, 1.0f, 1.0f, 0.8f, 120f));
    }

    /**
     * Cubemap example: an opaque 6-face cubemap fills the whole sky, with procedural stars on top.
     * Uses vanilla's title-screen panorama faces as a placeholder; supply your own
     * {@code <base>_0.png}..{@code _5.png} for a real space cubemap.
     */
    public static DUSkybox cubemapSky() {
        Identifier base = Identifier.withDefaultNamespace("textures/gui/title/background/panorama");
        return new DUSkybox("Cubemap (Panorama)")
                .add(DUSkyLayer.cubemap(base, false, 1f, 1f, 1f, 1.0f))
                .add(new DUSkyLayer(DUSkyLayer.Type.STARFIELD, 1.0f, 1.0f, 1.0f, 0.6f, 120f));
    }
}
