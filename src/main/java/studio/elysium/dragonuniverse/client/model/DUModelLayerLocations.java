package studio.elysium.dragonuniverse.client.model;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.Identifier;
import studio.elysium.dragonuniverse.DragonUniverse;


public class DUModelLayerLocations {
    // TODO: Layer double check, will most likely need own Model class
    public static final ModelLayerLocation NIMBUS =
            new ModelLayerLocation(Identifier.fromNamespaceAndPath(DragonUniverse.MODID, "nimbus"), "main");


    public static final ModelLayerLocation SPACE_POD =
            new ModelLayerLocation(Identifier.fromNamespaceAndPath(DragonUniverse.MODID, "space_pod"), "main");
}
