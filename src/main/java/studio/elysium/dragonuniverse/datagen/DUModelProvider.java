package studio.elysium.dragonuniverse.datagen;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TexturedModel;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import studio.elysium.dragonuniverse.DragonUniverse;
import studio.elysium.dragonuniverse.world.level.block.DUBlocks;
import studio.elysium.dragonuniverse.world.item.DUItems;
import studio.elysium.dragonuniverse.world.item.equipment.DUArmorMaterial;

public class DUModelProvider extends ModelProvider {
    public DUModelProvider(PackOutput output) {
        super(output, DragonUniverse.MODID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        itemModels.generateFlatItem(DUItems.EXAMPLE_ITEM.get(), ModelTemplates.FLAT_ITEM);

        itemModels.generateFlatItem(DUItems.RADISH.get(),  ModelTemplates.FLAT_ITEM);

        itemModels.generateFlatItem(DUItems.EXAMPLE_SWORD.get(), ModelTemplates.FLAT_HANDHELD_ITEM);
        itemModels.generateFlatItem(DUItems.EXAMPLE_PICKAXE.get(), ModelTemplates.FLAT_HANDHELD_ITEM);
        itemModels.generateFlatItem(DUItems.EXAMPLE_SHOVEL.get(), ModelTemplates.FLAT_HANDHELD_ITEM);
        itemModels.generateFlatItem(DUItems.EXAMPLE_AXE.get(), ModelTemplates.FLAT_HANDHELD_ITEM);
        itemModels.generateFlatItem(DUItems.EXAMPLE_HOE.get(), ModelTemplates.FLAT_HANDHELD_ITEM);

        itemModels.generateTrimmableItem(DUItems.EXAMPLE_HELMET.get(), DUArmorMaterial.EXAMPLE_KEY, ItemModelGenerators.TRIM_PREFIX_HELMET, false);
        itemModels.generateTrimmableItem(DUItems.EXAMPLE_CHESTPLATE.get(), DUArmorMaterial.EXAMPLE_KEY, ItemModelGenerators.TRIM_PREFIX_CHESTPLATE, false);
        itemModels.generateTrimmableItem(DUItems.EXAMPLE_LEGGINGS.get(), DUArmorMaterial.EXAMPLE_KEY, ItemModelGenerators.TRIM_PREFIX_LEGGINGS, false);
        itemModels.generateTrimmableItem(DUItems.EXAMPLE_BOOTS.get(), DUArmorMaterial.EXAMPLE_KEY, ItemModelGenerators.TRIM_PREFIX_BOOTS, false);

        itemModels.generateFlatItem(DUItems.CHA_LA_HEAD_CHA_LA_MUSIC_DISC.get(),  ModelTemplates.FLAT_ITEM);

        itemModels.declareCustomModelItem(DUItems.STAFF.get());

        itemModels.generateFlatItem(DUItems.EXAMPLE_WATER_BUCKER.get(), ModelTemplates.FLAT_ITEM);

        itemModels.generateFlatItem(DUItems.NIMBUS.get(),  ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(DUItems.SPACE_POD.get(),  ModelTemplates.FLAT_ITEM);

        blockModels.createTrivialCube(DUBlocks.EXAMPLE_BLOCK.get());

        blockModels.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(DUBlocks.CHAIR.get(),
                BlockModelGenerators.plainVariant(Identifier.fromNamespaceAndPath(DragonUniverse.MODID, "block/chair")))
                .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));

        blockModels.createNonTemplateModelBlock(DUBlocks.EXAMPLE_WATER_LIQUID_BLOCK.get());

        blockModels.createTrivialCube(DUBlocks.EXAMPLE_PLANKS.get());
        blockModels.woodProvider(DUBlocks.EXAMPLE_LOG.get()).logWithHorizontal(DUBlocks.EXAMPLE_LOG.get()).wood(DUBlocks.EXAMPLE_WOOD.get());
        blockModels.woodProvider(DUBlocks.STRIPPED_EXAMPLE_LOG.get()).logWithHorizontal(DUBlocks.STRIPPED_EXAMPLE_LOG.get()).wood(DUBlocks.STRIPPED_EXAMPLE_WOOD.get());

        blockModels.createTintedLeaves(DUBlocks.EXAMPLE_LEAVES.get(), TexturedModel.LEAVES, -12012255);
        blockModels.createPlantWithDefaultItem(DUBlocks.EXAMPLE_SAPLING.get(), DUBlocks.POTTED_EXAMPLE_SAPLING.get(), BlockModelGenerators.PlantType.TINTED);

    }
}
