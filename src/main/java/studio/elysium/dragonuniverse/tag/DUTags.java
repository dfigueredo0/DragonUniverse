package studio.elysium.dragonuniverse.tag;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.VillagerTradeTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.block.Block;
import studio.elysium.dragonuniverse.DragonUniverse;

public class DUTags {
    public static class Blocks {
        public static final TagKey<Block> NEEDS_EXAMPLE_TOOL = createTag("needs_example_tool");

        public static final TagKey<Block> INCORRECT_FOR_EXAMPLE_TOOL = createTag("incorrect_for_example_tool");

        private static TagKey<Block> createTag(String name) {
            return BlockTags.create(Identifier.fromNamespaceAndPath(DragonUniverse.MODID, name));
        }
    }

    public static class Items {
        public static final TagKey<Item> TRANSFORMABLE_ITEMS = createTag("transformable_items");

        public static final TagKey<Item> EXAMPLE_TOOLS_REPAIRABLES = createTag("example_tools_repairables");

        public static final TagKey<Item> EXAMPLE_LOGS = createTag("example_logs");

        private static TagKey<Item> createTag(String name) {
            return ItemTags.create(Identifier.fromNamespaceAndPath(DragonUniverse.MODID, name));
        }
    }

    public static class Trades {
        public static final TagKey<VillagerTrade> EXAMPLEGER_LEVEL_1 = createTag("exampleger/level_1");
        public static final TagKey<VillagerTrade> EXAMPLEGER_LEVEL_2 = createTag("exampleger/level_2");

        private static TagKey<VillagerTrade> createTag(String name) {
            return TagKey.create(Registries.VILLAGER_TRADE, Identifier.fromNamespaceAndPath(DragonUniverse.MODID, name));
        }
    }
}
