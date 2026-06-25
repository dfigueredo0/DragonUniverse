package studio.elysium.dragonuniverse.datagen.tags;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import studio.elysium.dragonuniverse.DragonUniverse;
import studio.elysium.dragonuniverse.world.level.block.DUBlocks;

import java.util.concurrent.CompletableFuture;

public class DUBlockTagProvider extends BlockTagsProvider {
    public DUBlockTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, DragonUniverse.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(BlockTags.FLOWER_POTS)
                .add(DUBlocks.POTTED_EXAMPLE_SAPLING.get());

        tag(BlockTags.LOGS_THAT_BURN)
                .add(DUBlocks.EXAMPLE_LOG.get())
                .add(DUBlocks.EXAMPLE_WOOD.get())
                .add(DUBlocks.STRIPPED_EXAMPLE_LOG.get())
                .add(DUBlocks.STRIPPED_EXAMPLE_WOOD.get());

        tag(BlockTags.LEAVES)
                .add(DUBlocks.EXAMPLE_LEAVES.get());

        tag(BlockTags.SAPLINGS)
            .add(DUBlocks.EXAMPLE_SAPLING.get());

        tag(BlockTags.PLANKS)
                .add(DUBlocks.EXAMPLE_PLANKS.get());
    }
}
