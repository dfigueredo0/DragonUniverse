package studio.elysium.dragonuniverse.world.level.levelgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.featuresize.TwoLayersFeatureSize;
import net.minecraft.world.level.levelgen.feature.foliageplacers.AcaciaFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;
import studio.elysium.dragonuniverse.DragonUniverse;
import studio.elysium.dragonuniverse.world.level.block.DUBlocks;
import studio.elysium.dragonuniverse.world.level.levelgen.feature.trunkplacers.SpiralTrunkPlacer;

import java.util.List;

public class DUConfiguredFeatures {
    public static final ResourceKey<ConfiguredFeature<?, ?>> EXAMPLE_OVERWORLD_ORES_KEY = registerKey("example_overworld_ores");
    public static final ResourceKey<ConfiguredFeature<?, ?>> EXAMPLE_NETHER_ORES_KEY = registerKey("example_nether_ores");
    public static final ResourceKey<ConfiguredFeature<?, ?>> EXAMPLE_END_ORES_KEY = registerKey("example_end_ores");

    public static final ResourceKey<ConfiguredFeature<?, ?>> EXAMPLE_TREE_KEY = registerKey("example_tree_key");

    public static void bootstrap(BootstrapContext<ConfiguredFeature<?, ?>> context) {
        List<OreConfiguration.TargetBlockState> overworldExampleOres = List.of(
                OreConfiguration.target(new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES), DUBlocks.EXAMPLE_BLOCK.get().defaultBlockState()),
                OreConfiguration.target(new TagMatchTest(BlockTags.DEEPSLATE_ORE_REPLACEABLES), DUBlocks.EXAMPLE_BLOCK.get().defaultBlockState())
        );

        register(context, EXAMPLE_OVERWORLD_ORES_KEY, Feature.ORE, new OreConfiguration(overworldExampleOres, 12));
        register(context, EXAMPLE_NETHER_ORES_KEY,  Feature.ORE, new OreConfiguration(
                new BlockMatchTest(Blocks.NETHERRACK), DUBlocks.EXAMPLE_BLOCK.get().defaultBlockState(), 12));
        register(context, EXAMPLE_END_ORES_KEY,  Feature.ORE, new OreConfiguration(
                new BlockMatchTest(Blocks.END_STONE), DUBlocks.EXAMPLE_BLOCK.get().defaultBlockState(), 12));

        register(context, EXAMPLE_TREE_KEY, Feature.TREE, new TreeConfiguration.TreeConfigurationBuilder(
                BlockStateProvider.simple(DUBlocks.EXAMPLE_LOG.get()),
                new SpiralTrunkPlacer(3, 3, 5),
                BlockStateProvider.simple(DUBlocks.EXAMPLE_LEAVES.get()),
                new AcaciaFoliagePlacer(ConstantInt.of(3), ConstantInt.of(2)),
                new TwoLayersFeatureSize(1, 0, 2)
        ).belowTrunkProvider(BlockStateProvider.simple(Blocks.STONE)).build());
    }

    public static ResourceKey<ConfiguredFeature<?, ?>> registerKey(String name) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, Identifier.fromNamespaceAndPath(DragonUniverse.MODID, name));
    }

    private static <FC extends FeatureConfiguration, F extends Feature<FC>> void register(BootstrapContext<ConfiguredFeature<?,?>> context,
                                                                                          ResourceKey<ConfiguredFeature<?,?>> key,
                                                                                          F feature, FC configuration) {
        context.register(key, new ConfiguredFeature<>(feature, configuration));
    }
}
