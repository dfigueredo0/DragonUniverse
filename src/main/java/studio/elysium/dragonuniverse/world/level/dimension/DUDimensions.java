package studio.elysium.dragonuniverse.world.level.dimension;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.biome.OverworldBiomes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TimelineTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.attribute.*;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import studio.elysium.dragonuniverse.DragonUniverse;

import java.util.List;
import java.util.Optional;

public class DUDimensions {
    public static final ResourceKey<LevelStem> SPACE_KEY = ResourceKey.create(Registries.LEVEL_STEM,
            Identifier.fromNamespaceAndPath(DragonUniverse.MODID, "space"));
    public static final ResourceKey<Level> SPACE_LEVEL_KEY = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(DragonUniverse.MODID, "space"));
    public static final ResourceKey<DimensionType> SPACE_DIM_KEY = ResourceKey.create(Registries.DIMENSION_TYPE,
            Identifier.fromNamespaceAndPath(DragonUniverse.MODID, "space_type"));

    public static final ResourceKey<LevelStem> NAMEK_KEY = ResourceKey.create(Registries.LEVEL_STEM,
            Identifier.fromNamespaceAndPath(DragonUniverse.MODID, "namek"));
    public static final ResourceKey<Level> NAMEK_LEVEL_KEY = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(DragonUniverse.MODID, "namek"));
    public static final ResourceKey<DimensionType> NAMEK_DIM_KEY = ResourceKey.create(Registries.DIMENSION_TYPE,
            Identifier.fromNamespaceAndPath(DragonUniverse.MODID, "namek_type"));

    public static void bootstrapType(BootstrapContext<DimensionType> context) {
        var timelines = context.lookup(Registries.TIMELINE);
        var clocks = context.lookup(Registries.WORLD_CLOCK);

        context.register(SPACE_DIM_KEY, new DimensionType(
                false,
                false,
                false,
                false,
                1.0D,
                -64,
                384,
                384,
                BlockTags.INFINIBURN_OVERWORLD,
                1.0F,
                new DimensionType.MonsterSettings(ConstantInt.of(0), 0),
                DimensionType.Skybox.NONE,
                CardinalLighting.Type.DEFAULT,
                EnvironmentAttributeMap.builder()
                        .set(EnvironmentAttributes.FOG_COLOR, -6168523)
                        .build(),
                timelines.getOrThrow(TimelineTags.UNIVERSAL),
                Optional.of(clocks.getOrThrow(WorldClocks.OVERWORLD))
        ));

        context.register(NAMEK_DIM_KEY, new DimensionType(
                true,
                true,
                false,
                false,
                0.5D,
                -64,
                384,
                384,
                BlockTags.INFINIBURN_OVERWORLD,
                .1F,
                new DimensionType.MonsterSettings(UniformInt.of(0,7), 0),
                DimensionType.Skybox.OVERWORLD,
                CardinalLighting.Type.DEFAULT,
                EnvironmentAttributeMap.builder()
                        .set(EnvironmentAttributes.FOG_COLOR, -4138753) // TODO: Change
                        .set(EnvironmentAttributes.SKY_COLOR, OverworldBiomes.calculateSkyColor(0.8F)) // TODO: Change
                        .set(EnvironmentAttributes.AMBIENT_LIGHT_COLOR, -16119286) // TODO: Change
                        .set(EnvironmentAttributes.CLOUD_COLOR, ARGB.white(0.8F))
                        .set(EnvironmentAttributes.CLOUD_HEIGHT, 192.33F)
                        .set(EnvironmentAttributes.BACKGROUND_MUSIC, BackgroundMusic.OVERWORLD) // TODO: Change
                        .set(EnvironmentAttributes.BED_RULE, BedRule.CAN_SLEEP_WHEN_DARK)
                        .set(EnvironmentAttributes.RESPAWN_ANCHOR_WORKS, false)
                        .set(EnvironmentAttributes.NETHER_PORTAL_SPAWNS_PIGLINS, true)
                        .set(EnvironmentAttributes.AMBIENT_SOUNDS, AmbientSounds.LEGACY_CAVE_SETTINGS)
                        .build(),
                timelines.getOrThrow(TimelineTags.UNIVERSAL), // maybe make custom timeline, look at Timelines.java
                Optional.of(clocks.getOrThrow(WorldClocks.OVERWORLD))
        ));
    }

    public static void bootstrapStem(BootstrapContext<LevelStem> context) {
        var biomes = context.lookup(Registries.BIOME);
        var dimensionTypes = context.lookup(Registries.DIMENSION_TYPE);
        var noiseGenSettings = context.lookup(Registries.NOISE_SETTINGS);

        NoiseBasedChunkGenerator voidBiomeGenerator = new NoiseBasedChunkGenerator(
                new FixedBiomeSource(biomes.getOrThrow(Biomes.THE_VOID)),
                noiseGenSettings.getOrThrow(NoiseGeneratorSettings.AMPLIFIED)
        );

        NoiseBasedChunkGenerator multiBiomeGenerator = new NoiseBasedChunkGenerator(
                MultiNoiseBiomeSource.createFromList(
                        new Climate.ParameterList<>(List.of(
                                Pair.of(Climate.parameters(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F), biomes.getOrThrow(Biomes.FOREST)),
                                Pair.of(Climate.parameters(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F), biomes.getOrThrow(Biomes.BIRCH_FOREST))
                        ))
                ),
                noiseGenSettings.getOrThrow(NoiseGeneratorSettings.AMPLIFIED)
        );

        context.register(SPACE_KEY, new LevelStem(dimensionTypes.getOrThrow(DUDimensions.SPACE_DIM_KEY), voidBiomeGenerator));
        context.register(NAMEK_KEY, new LevelStem(dimensionTypes.getOrThrow(NAMEK_DIM_KEY), multiBiomeGenerator));
    }
}
