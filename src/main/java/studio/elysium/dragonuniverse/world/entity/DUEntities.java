package studio.elysium.dragonuniverse.world.entity;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import studio.elysium.dragonuniverse.DragonUniverse;
import studio.elysium.dragonuniverse.world.item.DUItems;

import java.util.function.Supplier;

public class DUEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.createEntities(DragonUniverse.MODID);

    public static final ResourceKey<EntityType<?>> CHAIR_KEY = ResourceKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(DragonUniverse.MODID, "chair"));
    public static final ResourceKey<EntityType<?>> NIMBUS_KEY = ResourceKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(DragonUniverse.MODID, "nimbus"));
    public static final ResourceKey<EntityType<?>> SPACE_POD_KEY = ResourceKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(DragonUniverse.MODID, "space_pod"));

    public static final Supplier<EntityType<ChairEntity>> CHAIR = ENTITY_TYPES.register("chair",
            () -> EntityType.Builder.of(ChairEntity::new, MobCategory.MISC).noLootTable()
                    .sized(0.5F, 0.5F).build(CHAIR_KEY));

    // TODO: Make Custom Vehicle EntityType?
    public static final Supplier<EntityType<Boat>> NIMBUS = ENTITY_TYPES.register("nimbus",
            () -> EntityType.Builder.<Boat>of((entityType, level) -> new Boat(entityType, level, DUItems.NIMBUS),
                    MobCategory.MISC).eyeHeight(0.5625F).clientTrackingRange(10).noLootTable()
                    .sized(1.375F, 0.5625F).build(NIMBUS_KEY));
    public static final Supplier<EntityType<Boat>> SPACE_POD = ENTITY_TYPES.register("space_pod",
            () -> EntityType.Builder.<Boat>of((entityType, level) -> new Boat(entityType, level, DUItems.SPACE_POD),
                            MobCategory.MISC).eyeHeight(0.5625F).clientTrackingRange(10).noLootTable()
                    .sized(1.375F, 0.5625F).build(SPACE_POD_KEY));

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
