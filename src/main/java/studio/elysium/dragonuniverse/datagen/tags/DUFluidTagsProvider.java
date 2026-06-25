package studio.elysium.dragonuniverse.datagen.tags;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.FluidTagsProvider;
import net.minecraft.tags.FluidTags;
import studio.elysium.dragonuniverse.DragonUniverse;
import studio.elysium.dragonuniverse.world.fluids.DUFluids;

import java.util.concurrent.CompletableFuture;

public class DUFluidTagsProvider extends FluidTagsProvider {
    public DUFluidTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, DragonUniverse.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        tag(FluidTags.WATER)
                .add(DUFluids.EXAMPLE_WATER_SOURCE.get())
                .add(DUFluids.EXAMPLE_WATER_FLOWING.get());
    }
}
