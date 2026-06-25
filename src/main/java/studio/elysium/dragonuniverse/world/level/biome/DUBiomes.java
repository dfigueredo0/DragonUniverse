package studio.elysium.dragonuniverse.world.level.biome;

import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import studio.elysium.dragonuniverse.DragonUniverse;
import studio.elysium.dragonuniverse.world.level.biome.region.NetherRegion;
import studio.elysium.dragonuniverse.world.level.biome.region.OverworldRegion;
import terrablender.api.RegionType;
import terrablender.api.Regions;

public class DUBiomes {
    public static void registerBiomes() {
        Regions.register(new OverworldRegion(Identifier.fromNamespaceAndPath(DragonUniverse.MODID, "dragonuniverse_overworld"), 20));
        Regions.register(new NetherRegion(Identifier.fromNamespaceAndPath(DragonUniverse.MODID, "dragonuniverse_nether"), 20));

        // EndBiomeRegistry.registerHighlandsBiome(CUSTOM_END_BIOME, WEIGHT=20);
    }

    public static void bootstrap(BootstrapContext<Biome> context) {
        var carver = context.lookup(Registries.CONFIGURED_CARVER);
        var placedFeatures = context.lookup(Registries.PLACED_FEATURE);
    }

    private static void register(BootstrapContext<Biome> context, ResourceKey<Biome> key, Biome biome) {
        context.register(key, biome);
    }

    private static ResourceKey<Biome> registerKey(String name) {
        return ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(DragonUniverse.MODID, name));
    }
}
