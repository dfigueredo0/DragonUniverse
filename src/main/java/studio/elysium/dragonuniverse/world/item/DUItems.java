package studio.elysium.dragonuniverse.world.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import studio.elysium.dragonuniverse.DragonUniverse;
import studio.elysium.dragonuniverse.world.entity.DUEntities;
import studio.elysium.dragonuniverse.world.fluids.DUFluids;
import studio.elysium.dragonuniverse.world.food.DUFoodProperties;
import studio.elysium.dragonuniverse.world.item.equipment.DUArmorMaterial;

import java.util.function.Consumer;

public class DUItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DragonUniverse.MODID);

    public static DeferredItem<Item> EXAMPLE_ITEM = ITEMS.registerSimpleItem("example_item", properties -> properties);

    public static final DeferredItem<Item> RADISH = ITEMS.registerItem("radish",
            properties -> new Item(properties.food(DUFoodProperties.RADISH, DUFoodProperties.RADISH_EFFECT)) {
                @Override
                public void appendHoverText(ItemStack itemStack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag tooltipFlag) {
                    builder.accept(Component.translatable("tooltip.dragon_universe.radish"));
                    super.appendHoverText(itemStack, context, display, builder, tooltipFlag);
                }
            });

    public static final DeferredItem<Item> EXAMPLE_SWORD = ITEMS.registerItem("example_sword",
            properties -> new Item(properties.sword(DUToolMaterial.EXAMPLE, 3.0F, -2.4F)));
    public static final DeferredItem<Item> EXAMPLE_PICKAXE = ITEMS.registerItem("example_pickaxe",
            properties -> new Item(properties.pickaxe(DUToolMaterial.EXAMPLE, 1.0F, -2.8F)));
    public static final DeferredItem<Item> EXAMPLE_SHOVEL = ITEMS.registerItem("example_shovel",
            properties -> new ShovelItem(DUToolMaterial.EXAMPLE, 1.5F, -3.0F, properties));
    public static final DeferredItem<Item> EXAMPLE_AXE = ITEMS.registerItem("example_axe",
            properties -> new AxeItem(DUToolMaterial.EXAMPLE, 6.0F, -3.2F, properties));
    public static final DeferredItem<Item> EXAMPLE_HOE = ITEMS.registerItem("example_hoe",
            properties -> new HoeItem(DUToolMaterial.EXAMPLE, 0F, -3.0F, properties));

    public static final DeferredItem<Item> EXAMPLE_HELMET = ITEMS.registerItem("example_helmet",
            properties -> new Item(properties.humanoidArmor(DUArmorMaterial.EXAMPLE_ARMOR_MATERIAL, ArmorType.HELMET)));
    public static final DeferredItem<Item> EXAMPLE_CHESTPLATE = ITEMS.registerItem("example_chestplate",
            properties -> new Item(properties.humanoidArmor(DUArmorMaterial.EXAMPLE_ARMOR_MATERIAL, ArmorType.CHESTPLATE)));
    public static final DeferredItem<Item> EXAMPLE_LEGGINGS = ITEMS.registerItem("example_leggings",
            properties -> new Item(properties.humanoidArmor(DUArmorMaterial.EXAMPLE_ARMOR_MATERIAL, ArmorType.LEGGINGS)));
    public static final DeferredItem<Item> EXAMPLE_BOOTS = ITEMS.registerItem("example_boots",
            properties -> new Item(properties.humanoidArmor(DUArmorMaterial.EXAMPLE_ARMOR_MATERIAL, ArmorType.BOOTS)));

    public static final DeferredItem<Item> CHA_LA_HEAD_CHA_LA_MUSIC_DISC = ITEMS.registerItem("cha_la_head_cha_la_music_disc",
    properties -> new Item(properties.jukeboxPlayable(DUJukeboxSongs.CHA_LA_HEAD_CHA_LA_KEY).stacksTo(1)));

    public static final DeferredItem<Item> STAFF = ITEMS.registerItem("staff",
            properties -> new Item(properties.stacksTo(1).rarity(Rarity.EPIC)));

    public static final DeferredItem<Item> EXAMPLE_WATER_BUCKER = ITEMS.registerItem("example_water_bucket",
            properties -> new BucketItem(DUFluids.EXAMPLE_WATER_SOURCE.get(), properties.stacksTo(1).craftRemainder(Items.BUCKET)));

    // TODO: Change to Custom Vehicle Entity if change in DUEntities is done
    public static final DeferredItem<Item> NIMBUS = ITEMS.registerItem("nimbus",
            properties -> new BoatItem(DUEntities.NIMBUS.get(), properties.stacksTo(1)));
    public static final DeferredItem<Item> SPACE_POD = ITEMS.registerItem("space_pod",
            properties -> new BoatItem(DUEntities.SPACE_POD.get(), properties.stacksTo(1)));

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
