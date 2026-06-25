package studio.elysium.dragonuniverse.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import studio.elysium.dragonuniverse.DragonUniverse;
import studio.elysium.dragonuniverse.tag.DUTags;
import studio.elysium.dragonuniverse.world.level.block.DUBlocks;
import studio.elysium.dragonuniverse.world.item.DUItems;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DURecipeProvider extends RecipeProvider {
    protected DURecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
        super(registries, output);
    }

    @Override
    protected void buildRecipes() {
        List<ItemLike> EXAMPLE_SMELTABLES = List.of(DUItems.EXAMPLE_ITEM, DUBlocks.EXAMPLE_BLOCK);

        woodFromLogs(DUBlocks.EXAMPLE_WOOD, DUBlocks.EXAMPLE_LOG);
        woodFromLogs(DUBlocks.STRIPPED_EXAMPLE_WOOD, DUBlocks.STRIPPED_EXAMPLE_LOG);
        planksFromLogs(DUBlocks.EXAMPLE_PLANKS, DUTags.Items.EXAMPLE_LOGS, 4);
    }

    @Override
    protected <T extends AbstractCookingRecipe> void oreCooking(AbstractCookingRecipe.Factory<T> factory, List<ItemLike> smeltables, RecipeCategory craftingCategory, CookingBookCategory cookingCategory, ItemLike result, float experience, int cookingTime, String group, String fromDesc) {
        for (ItemLike item : smeltables) {
            SimpleCookingRecipeBuilder.generic(Ingredient.of(item), craftingCategory, cookingCategory, result, experience, cookingTime, factory)
                    .group(group)
                    .unlockedBy(getHasName(item), this.has(item))
                    .save(this.output, DragonUniverse.MODID + ":" + getItemName(result) + fromDesc + "_" + getItemName(item));
        }
    }

    public static class Runner extends RecipeProvider.Runner {
        public Runner(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> registries) {
            super(packOutput, registries);
        }

        @Override
        protected RecipeProvider createRecipeProvider(HolderLookup.Provider provider, RecipeOutput recipeOutput) {
            return new DURecipeProvider(provider, recipeOutput);
        }

        @Override
        public String getName() {
            return "Dragon Universe Recipes";
        }
    }
}
