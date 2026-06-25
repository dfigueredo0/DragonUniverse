package studio.elysium.dragonuniverse.datagen;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.EntityLootSubProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.flag.FeatureFlags;
import studio.elysium.dragonuniverse.world.entity.DUEntities;

import java.util.stream.Stream;

public class DUEntityLootTableProvider extends EntityLootSubProvider {
    protected DUEntityLootTableProvider(HolderLookup.Provider registries) {
        super(FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    public void generate() {

    }

    @Override
    protected Stream<EntityType<?>> getKnownEntityTypes() {
        return DUEntities.ENTITY_TYPES.getEntries().stream().map(Holder::value);
    }
}
