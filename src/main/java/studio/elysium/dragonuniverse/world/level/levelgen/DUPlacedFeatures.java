package studio.elysium.dragonuniverse.world.level.levelgen;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.data.worldgen.placement.VegetationPlacements;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import studio.elysium.dragonuniverse.DragonUniverse;
import studio.elysium.dragonuniverse.world.level.block.DUBlocks;

import java.util.List;

public class DUPlacedFeatures {
    public static final ResourceKey<PlacedFeature> EXAMPLE_OVERWORLD_ORES_PLACED_KEY = registerKey("example_overworld_ores_placed");
    public static final ResourceKey<PlacedFeature> EXAMPLE_NETHER_ORES_PLACED_KEY = registerKey("example_nether_ores_placed");
    public static final ResourceKey<PlacedFeature> EXAMPLE_END_ORES_PLACED_KEY = registerKey("example_ores_ores_placed");

    public static final ResourceKey<PlacedFeature> EXAMPLE_TREE_PLACED_KEY =registerKey("example_tree_placed");

    public static void bootstrap(BootstrapContext<PlacedFeature> context) {
        var configuredFeatures = context.lookup(Registries.CONFIGURED_FEATURE);

        register(context, EXAMPLE_OVERWORLD_ORES_PLACED_KEY, configuredFeatures.getOrThrow(DUConfiguredFeatures.EXAMPLE_OVERWORLD_ORES_KEY),
                DUOrePlacements.commonOrePlacement(12,
                        HeightRangePlacement.triangle(VerticalAnchor.absolute(-64), VerticalAnchor.absolute(80))));
        register(context, EXAMPLE_NETHER_ORES_PLACED_KEY, configuredFeatures.getOrThrow(DUConfiguredFeatures.EXAMPLE_NETHER_ORES_KEY),
                DUOrePlacements.commonOrePlacement(12,
                        HeightRangePlacement.triangle(VerticalAnchor.absolute(-64), VerticalAnchor.absolute(80))));
        register(context, EXAMPLE_END_ORES_PLACED_KEY, configuredFeatures.getOrThrow(DUConfiguredFeatures.EXAMPLE_END_ORES_KEY),
                DUOrePlacements.commonOrePlacement(12,
                        HeightRangePlacement.triangle(VerticalAnchor.absolute(-64), VerticalAnchor.absolute(80))));

        register(context, EXAMPLE_TREE_PLACED_KEY, configuredFeatures.getOrThrow(DUConfiguredFeatures.EXAMPLE_TREE_KEY),
                VegetationPlacements.treePlacement(PlacementUtils.countExtra(3, 0.1F, 2),
                        DUBlocks.EXAMPLE_SAPLING.get()));
    }

    private static ResourceKey<PlacedFeature> registerKey(String name) {
        return ResourceKey.create(Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath(DragonUniverse.MODID, name));
    }

    private static void register(BootstrapContext<PlacedFeature> context, ResourceKey<PlacedFeature> key, Holder<ConfiguredFeature<?, ?>> configuration,
                                 List<PlacementModifier> modifiers) {
        context.register(key, new PlacedFeature(configuration, List.copyOf(modifiers)));
    }
}
