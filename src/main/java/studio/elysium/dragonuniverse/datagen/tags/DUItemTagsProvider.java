package studio.elysium.dragonuniverse.datagen.tags;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.ItemTags;
import net.neoforged.neoforge.common.data.ItemTagsProvider;
import studio.elysium.dragonuniverse.DragonUniverse;
import studio.elysium.dragonuniverse.tag.DUTags;
import studio.elysium.dragonuniverse.world.item.DUItems;
import studio.elysium.dragonuniverse.world.level.block.DUBlocks;

import java.util.concurrent.CompletableFuture;

public class DUItemTagsProvider extends ItemTagsProvider {
    public DUItemTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, DragonUniverse.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        tag(DUTags.Items.TRANSFORMABLE_ITEMS)
                .add(DUItems.EXAMPLE_ITEM.get());

        tag(DUTags.Items.EXAMPLE_TOOLS_REPAIRABLES)
                .add(DUItems.EXAMPLE_ITEM.get());

        tag(ItemTags.SWORDS).add(DUItems.EXAMPLE_SWORD.get());
        tag(ItemTags.PICKAXES).add(DUItems.EXAMPLE_PICKAXE.get());
        tag(ItemTags.SHOVELS).add(DUItems.EXAMPLE_SHOVEL.get());
        tag(ItemTags.AXES).add(DUItems.EXAMPLE_AXE.get());
        tag(ItemTags.HOES).add(DUItems.EXAMPLE_HOE.get());

        tag(ItemTags.HEAD_ARMOR).add(DUItems.EXAMPLE_HELMET.get());
        tag(ItemTags.CHEST_ARMOR).add(DUItems.EXAMPLE_CHESTPLATE.get());
        tag(ItemTags.LEG_ARMOR).add(DUItems.EXAMPLE_LEGGINGS.get());
        tag(ItemTags.FOOT_ARMOR).add(DUItems.EXAMPLE_BOOTS.get());

        tag(ItemTags.PLANKS)
                .add(DUBlocks.EXAMPLE_PLANKS.asItem());

        tag(ItemTags.LOGS_THAT_BURN)
                .add(DUBlocks.EXAMPLE_LOG.asItem())
                .add(DUBlocks.EXAMPLE_WOOD.asItem())
                .add(DUBlocks.STRIPPED_EXAMPLE_LOG.asItem())
                .add(DUBlocks.STRIPPED_EXAMPLE_WOOD.asItem());

        tag(DUTags.Items.EXAMPLE_LOGS)
                .add(DUBlocks.EXAMPLE_LOG.asItem())
                .add(DUBlocks.EXAMPLE_WOOD.asItem())
                .add(DUBlocks.STRIPPED_EXAMPLE_LOG.asItem())
                .add(DUBlocks.STRIPPED_EXAMPLE_WOOD.asItem());
    }
}
