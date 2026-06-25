package studio.elysium.dragonuniverse.world.fluids;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.registries.DeferredRegister;
import studio.elysium.dragonuniverse.DragonUniverse;
import studio.elysium.dragonuniverse.world.item.DUItems;
import studio.elysium.dragonuniverse.world.level.block.DUBlocks;

import java.util.function.Supplier;

public class DUFluids {
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(BuiltInRegistries.FLUID, DragonUniverse.MODID);

    public static final Supplier<FlowingFluid> EXAMPLE_WATER_SOURCE = FLUIDS.register("example_water_source",
            () -> new BaseFlowingFluid.Source(DUFluids.EXAMPLE_WATER_PROPERTIES));
    public static final Supplier<FlowingFluid> EXAMPLE_WATER_FLOWING = FLUIDS.register("example_water_flowing",
            () -> new BaseFlowingFluid.Flowing(DUFluids.EXAMPLE_WATER_PROPERTIES));

    private static final BaseFlowingFluid.Properties  EXAMPLE_WATER_PROPERTIES = new BaseFlowingFluid.Properties(
            DUFluidTypes.EXAMPLE_FLUID_TYPE, EXAMPLE_WATER_SOURCE, EXAMPLE_WATER_FLOWING)
            .slopeFindDistance(2).levelDecreasePerBlock(1)
            .block(DUBlocks.EXAMPLE_WATER_LIQUID_BLOCK).bucket(DUItems.EXAMPLE_WATER_BUCKER);

    public static void register(IEventBus bus) {
        FLUIDS.register(bus);
    }
}
