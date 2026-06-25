package studio.elysium.dragonuniverse.datagen;

import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import studio.elysium.dragonuniverse.DragonUniverse;
import studio.elysium.dragonuniverse.datagen.advancements.DUAdvancements;
import studio.elysium.dragonuniverse.datagen.painting.DUPaintingTagsProvider;
import studio.elysium.dragonuniverse.datagen.tags.DUBlockTagProvider;
import studio.elysium.dragonuniverse.datagen.tags.DUEnchantmentTagProvider;
import studio.elysium.dragonuniverse.datagen.tags.DUFluidTagsProvider;
import studio.elysium.dragonuniverse.datagen.tags.DUItemTagsProvider;
import studio.elysium.dragonuniverse.datagen.villager.DUPOITags;
import studio.elysium.dragonuniverse.datagen.villager.DUVillagerTradeTags;

import java.util.Collections;
import java.util.List;

@EventBusSubscriber(modid = DragonUniverse.MODID)
public class DataGenerators {
    @SubscribeEvent
    public static void gatherClientData(GatherDataEvent.Client event) {
        DataGenerator generator = event.getGenerator();
        PackOutput packOutput = generator.getPackOutput();
        var lookupProvider = event.getLookupProvider();

        generator.addProvider(true, new DUModelProvider(packOutput));
        generator.addProvider(true, new LootTableProvider(packOutput, Collections.emptySet(),
                List.of(new LootTableProvider.SubProviderEntry(DUBlockLootTableProvider::new, LootContextParamSets.BLOCK),
                        new LootTableProvider.SubProviderEntry(DUEntityLootTableProvider::new, LootContextParamSets.ENTITY)
                ), lookupProvider));
        generator.addProvider(true, new DUBlockTagProvider(packOutput, lookupProvider));
        generator.addProvider(true, new DURecipeProvider.Runner(packOutput, lookupProvider));
        generator.addProvider(true, new DUDataMapProvider(packOutput, lookupProvider));
        generator.addProvider(true, new DUItemTagsProvider(packOutput, lookupProvider));

        generator.addProvider(true, new DUEquipmentAsset(packOutput));
        generator.addProvider(true, new DUGlobalLootModifierProvider(packOutput, lookupProvider));

        generator.addProvider(true, new DUSoundsProvider(packOutput));
        generator.addProvider(true, new DUDatapackProvider(packOutput, lookupProvider));

        generator.addProvider(true, new DUVillagerTradeTags(packOutput, lookupProvider));
        generator.addProvider(true, new DUPOITags(packOutput, lookupProvider));

        generator.addProvider(true, new DUPaintingTagsProvider(packOutput, lookupProvider));
        generator.addProvider(true, new DUEnchantmentTagProvider(packOutput, lookupProvider));

        generator.addProvider(true, new DUAdvancements(packOutput, lookupProvider));

        generator.addProvider(true, new DUFluidTagsProvider(packOutput, lookupProvider));
    }
}
