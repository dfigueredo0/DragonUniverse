package studio.elysium.dragonuniverse.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.GlobalLootModifierProvider;
import studio.elysium.dragonuniverse.DragonUniverse;

import java.util.concurrent.CompletableFuture;

public class DUGlobalLootModifierProvider extends GlobalLootModifierProvider {
    public DUGlobalLootModifierProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries, DragonUniverse.MODID);
    }

    @Override
    protected void start() {

    }
}
