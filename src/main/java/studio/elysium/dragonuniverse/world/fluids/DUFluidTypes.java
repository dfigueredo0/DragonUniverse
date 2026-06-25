package studio.elysium.dragonuniverse.world.fluids;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.joml.Vector4f;
import studio.elysium.dragonuniverse.DragonUniverse;

import java.util.function.Supplier;

public class DUFluidTypes {
    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, DragonUniverse.MODID);

    public static final Supplier<FluidType> EXAMPLE_FLUID_TYPE = FLUID_TYPES.register("example_fluid_type",
            () -> new FluidType(FluidType.Properties.create().isWaterLike(true)));

    public static IClientFluidTypeExtensions EXAMPLE_FLUID_EXTENSION = new IClientFluidTypeExtensions() {
        @Override
        public void modifyFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenWorldAmount, Vector4f fluidFogColor) {
            fluidFogColor.set(0.83F, 0.16F, 0.16F);
            IClientFluidTypeExtensions.super.modifyFogColor(camera, partialTick, level, renderDistance, darkenWorldAmount, fluidFogColor);
        }
    };

    public static void register(IEventBus eventBus) {
        FLUID_TYPES.register(eventBus);
    }
}
