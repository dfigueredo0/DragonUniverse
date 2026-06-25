package studio.elysium.dragonuniverse.datagen.advancements;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.criterion.ItemPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.advancements.AdvancementProvider;
import net.minecraft.data.advancements.AdvancementSubProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import studio.elysium.dragonuniverse.DragonUniverse;
import studio.elysium.dragonuniverse.world.item.DUItems;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static net.minecraft.advancements.criterion.InventoryChangeTrigger.TriggerInstance.hasItems;

public class DUAdvancements extends AdvancementProvider {
    public DUAdvancements(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries, List.of(new DragonUniverseAdvancements()));
    }

    public static class DragonUniverseAdvancements implements AdvancementSubProvider {

        @Override
        public void generate(HolderLookup.Provider registries, Consumer<AdvancementHolder> output) {
            var items = registries.lookupOrThrow(Registries.ITEM);
            var blocks = registries.lookupOrThrow(Registries.BLOCK);

            AdvancementHolder root = Advancement.Builder.advancement()
                    .display(
                            DUItems.EXAMPLE_ITEM,
                            Component.translatable("advancements.dragonuniverse.root.title"),
                            Component.translatable("advancements.dragonuniverse.root.description"),
                            Identifier.withDefaultNamespace("gui/advancements/backgrounds/adventure"),
                            AdvancementType.TASK,
                            false,
                            false,
                            false
                    ).addCriterion("has_example_item", hasItems(ItemPredicate.Builder.item().of(items, DUItems.EXAMPLE_ITEM.asItem())))
                    .save(output, Identifier.fromNamespaceAndPath(DragonUniverse.MODID, "dragonuniverse/root"));
        }
    }
}
