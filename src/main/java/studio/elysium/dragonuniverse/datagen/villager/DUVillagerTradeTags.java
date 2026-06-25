package studio.elysium.dragonuniverse.datagen.villager;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.VillagerTradesTagsProvider;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.VillagerTradeTags;
import studio.elysium.dragonuniverse.tag.DUTags;
import studio.elysium.dragonuniverse.world.item.trading.DUVillagerTrades;

import java.util.concurrent.CompletableFuture;

public class DUVillagerTradeTags extends VillagerTradesTagsProvider {
    public DUVillagerTradeTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        getOrCreateRawBuilder(VillagerTradeTags.FARMER_LEVEL_1)
                .add(TagEntry.element(DUVillagerTrades.FARMER_1_EMERALD_RADISH.identifier()))
                .add(TagEntry.element(DUVillagerTrades.FARMER_1_DIAMOND_RADISH_SEEDS.identifier()));

        getOrCreateRawBuilder(DUTags.Trades.EXAMPLEGER_LEVEL_1)
                .add(TagEntry.element(DUVillagerTrades.EXAMPLEGER_1_EMERALD_RADISH.identifier()));

        getOrCreateRawBuilder(DUTags.Trades.EXAMPLEGER_LEVEL_2)
                .add(TagEntry.element(DUVillagerTrades.EXAMPLEGER_2_DIAMOND_RADISH.identifier()));
    }
}
