package studio.elysium.dragonuniverse.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.DataMapProvider;
import net.neoforged.neoforge.registries.datamaps.builtin.FurnaceFuel;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;
import net.neoforged.neoforge.registries.datamaps.builtin.Strippable;
import studio.elysium.dragonuniverse.world.item.DUItems;
import studio.elysium.dragonuniverse.world.level.block.DUBlocks;

import java.util.concurrent.CompletableFuture;

public class DUDataMapProvider extends DataMapProvider {
    /**
     * Create a new provider.
     *
     * @param packOutput     the output location
     * @param lookupProvider a {@linkplain CompletableFuture} supplying the registries
     */
    protected DUDataMapProvider(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(packOutput, lookupProvider);
    }

    @Override
    protected void gather(HolderLookup.Provider provider) {
        builder(NeoForgeDataMaps.FURNACE_FUELS)
                .add(DUItems.EXAMPLE_ITEM.getId(), new FurnaceFuel(2400), false);

        builder(NeoForgeDataMaps.STRIPPABLES)
                .add(DUBlocks.EXAMPLE_LOG, new Strippable(DUBlocks.STRIPPED_EXAMPLE_LOG.get()), false)
                .add(DUBlocks.EXAMPLE_WOOD, new Strippable(DUBlocks.STRIPPED_EXAMPLE_WOOD.get()), false);
    }
}
