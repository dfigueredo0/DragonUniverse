package studio.elysium.dragonuniverse.datagen;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import studio.elysium.dragonuniverse.world.level.block.DUBlocks;

import java.util.Set;

public class DUBlockLootTableProvider extends BlockLootSubProvider {
    protected DUBlockLootTableProvider(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected void generate() {
        dropSelf(DUBlocks.EXAMPLE_BLOCK.get());

        dropSelf(DUBlocks.CHAIR.get());

        dropSelf(DUBlocks.EXAMPLE_LOG.get());
        dropSelf(DUBlocks.EXAMPLE_WOOD.get());
        dropSelf(DUBlocks.STRIPPED_EXAMPLE_LOG.get());
        dropSelf(DUBlocks.STRIPPED_EXAMPLE_WOOD.get());
        dropSelf(DUBlocks.EXAMPLE_PLANKS.get());
        dropSelf(DUBlocks.EXAMPLE_SAPLING.get());

        add(DUBlocks.EXAMPLE_LEAVES.get(), block -> createLeavesDrops(block, DUBlocks.EXAMPLE_SAPLING.get(), NORMAL_LEAVES_SAPLING_CHANCES));
        add(DUBlocks.POTTED_EXAMPLE_SAPLING.get(), createPotFlowerItemTable(DUBlocks.EXAMPLE_SAPLING.get()));
    }

    protected LootTable.Builder createMultipleOreDrops(Block block, Item item, float minDrops, float maxDrops) {
        HolderLookup.RegistryLookup<Enchantment> registryLookup = this.registries.lookupOrThrow(Registries.ENCHANTMENT);

        return this.createSilkTouchDispatchTable(block,
                this.applyExplosionDecay(block, LootItem.lootTableItem(item)
                        .apply(SetItemCountFunction.setCount(UniformGenerator.between(minDrops, maxDrops)))
                        .apply(ApplyBonusCount.addOreBonusCount(registryLookup.getOrThrow(Enchantments.FORTUNE)))));
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return DUBlocks.BLOCKS.getEntries().stream().map(Holder::value)::iterator;
    }
}

