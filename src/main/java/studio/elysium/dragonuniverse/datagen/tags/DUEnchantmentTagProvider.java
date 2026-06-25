package studio.elysium.dragonuniverse.datagen.tags;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.EnchantmentTagsProvider;
import net.minecraft.tags.EnchantmentTags;
import studio.elysium.dragonuniverse.DragonUniverse;
import studio.elysium.dragonuniverse.world.item.enchantment.DUEnchantments;

import java.util.concurrent.CompletableFuture;

public class DUEnchantmentTagProvider extends EnchantmentTagsProvider {
    public DUEnchantmentTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, DragonUniverse.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        tag(EnchantmentTags.IN_ENCHANTING_TABLE)
                .addOptional(DUEnchantments.LIGHTNING_STRIKER);
    }
}
