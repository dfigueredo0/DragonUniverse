package studio.elysium.dragonuniverse.world.entity.villager;

import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.block.SoundType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import studio.elysium.dragonuniverse.DragonUniverse;
import studio.elysium.dragonuniverse.sounds.DUSounds;
import studio.elysium.dragonuniverse.world.item.trading.DUTradeSets;
import studio.elysium.dragonuniverse.world.level.block.DUBlocks;

public class DUVillagers {
    public static final DeferredRegister<PoiType> POI_TYEPS =
            DeferredRegister.create(BuiltInRegistries.POINT_OF_INTEREST_TYPE, DragonUniverse.MODID);
    public static final DeferredRegister<VillagerProfession> VILLAGER_PROFESSION =
            DeferredRegister.create(BuiltInRegistries.VILLAGER_PROFESSION, DragonUniverse.MODID);

    public static final Holder<PoiType> EXAMPLE_POI = POI_TYEPS.register("example_poi",
            () -> new PoiType(ImmutableSet.copyOf(DUBlocks.EXAMPLE_BLOCK.get().getStateDefinition().getPossibleStates()), 1, 1));

    public static final Holder<VillagerProfession> EXAMPLEGER = VILLAGER_PROFESSION.register("exampleger",
            () -> new VillagerProfession(Component.literal("Exampleger"), holder -> holder.value() == EXAMPLE_POI.value(),
                    holder -> holder.value() == EXAMPLE_POI.value(), ImmutableSet.of(), ImmutableSet.of(),
                    SoundType.AMETHYST.getBreakSound(), Int2ObjectMap.ofEntries(
                        Int2ObjectMap.entry(1, DUTradeSets.EXAMPLEGER_LEVEL_1),
                        Int2ObjectMap.entry(2, DUTradeSets.EXAMPLEGER_LEVEL_2)
                    ))
            );

    public static void register(IEventBus eventBus) {
        POI_TYEPS.register(eventBus);
        VILLAGER_PROFESSION.register(eventBus);
    }
}
