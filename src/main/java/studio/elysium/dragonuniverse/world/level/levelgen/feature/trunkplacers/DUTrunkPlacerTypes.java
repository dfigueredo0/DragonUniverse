package studio.elysium.dragonuniverse.world.level.levelgen.feature.trunkplacers;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import studio.elysium.dragonuniverse.DragonUniverse;

import java.util.function.Supplier;

public class DUTrunkPlacerTypes {
    public static final DeferredRegister<TrunkPlacerType<?>> TRUNK_PLACERS =
            DeferredRegister.create(Registries.TRUNK_PLACER_TYPE, DragonUniverse.MODID);

    public static final Supplier<TrunkPlacerType<SpiralTrunkPlacer>> SPIRAL_TRUNK_PLACER =
            TRUNK_PLACERS.register("spiral_trunk_placer", () -> new TrunkPlacerType<>(SpiralTrunkPlacer.CODEC));

    public static void register(IEventBus bus) {
        TRUNK_PLACERS.register(bus);
    }
}
