package studio.elysium.dragonuniverse.datagen.painting;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.PaintingVariantTagsProvider;
import net.minecraft.tags.PaintingVariantTags;
import net.minecraft.tags.TagEntry;
import studio.elysium.dragonuniverse.DragonUniverse;
import studio.elysium.dragonuniverse.world.entity.decoration.DUPaintings;

import java.util.concurrent.CompletableFuture;

public class DUPaintingTagsProvider extends PaintingVariantTagsProvider {
    public DUPaintingTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, DragonUniverse.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        getOrCreateRawBuilder(PaintingVariantTags.PLACEABLE)
                .add(TagEntry.optionalElement(DUPaintings.EXAMPLE_KEY.identifier()));
    }
}
