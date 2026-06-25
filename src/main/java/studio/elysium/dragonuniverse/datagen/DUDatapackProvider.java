package studio.elysium.dragonuniverse.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import studio.elysium.dragonuniverse.DragonUniverse;
import studio.elysium.dragonuniverse.world.damagesource.DUDamageTypes;
import studio.elysium.dragonuniverse.world.entity.decoration.DUPaintings;
import studio.elysium.dragonuniverse.world.item.DUJukeboxSongs;
import studio.elysium.dragonuniverse.world.item.enchantment.DUEnchantments;
import studio.elysium.dragonuniverse.world.item.trading.DUTradeSets;
import studio.elysium.dragonuniverse.world.item.trading.DUVillagerTrades;
import studio.elysium.dragonuniverse.world.level.biome.DUBiomes;
import studio.elysium.dragonuniverse.world.level.dimension.DUDimensions;
import studio.elysium.dragonuniverse.world.level.levelgen.DUBiomeModifiers;
import studio.elysium.dragonuniverse.world.level.levelgen.DUConfiguredFeatures;
import studio.elysium.dragonuniverse.world.level.levelgen.DUPlacedFeatures;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class DUDatapackProvider extends DatapackBuiltinEntriesProvider {
    public static final RegistrySetBuilder BUILDER = new RegistrySetBuilder()
            .add(Registries.JUKEBOX_SONG, DUJukeboxSongs::bootstrap)
            .add(Registries.DAMAGE_TYPE, DUDamageTypes::bootstrap)
            .add(Registries.VILLAGER_TRADE, DUVillagerTrades::bootstrap)
            .add(Registries.TRADE_SET, DUTradeSets::bootstrap)
            .add(Registries.PAINTING_VARIANT, DUPaintings::bootstrap)
            .add(Registries.ENCHANTMENT, DUEnchantments::bootstrap)
            .add(Registries.CONFIGURED_FEATURE, DUConfiguredFeatures::bootstrap)
            .add(Registries.PLACED_FEATURE, DUPlacedFeatures::bootstrap)
            .add(NeoForgeRegistries.Keys.BIOME_MODIFIERS, DUBiomeModifiers::bootstrap)
            .add(Registries.DIMENSION_TYPE, DUDimensions::bootstrapType)
            .add(Registries.LEVEL_STEM, DUDimensions::bootstrapStem)
            .add(Registries.BIOME, DUBiomes::bootstrap);

    public DUDatapackProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries, BUILDER, Set.of(DragonUniverse.MODID));
    }
}
