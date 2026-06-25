package studio.elysium.dragonuniverse.world.item.equipment;

import com.google.common.collect.Maps;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import studio.elysium.dragonuniverse.DragonUniverse;
import studio.elysium.dragonuniverse.tag.DUTags;

import java.util.Map;


public class DUArmorMaterial {
    private static ResourceKey<? extends Registry<EquipmentAsset>> ROOT_ID =
            ResourceKey.createRegistryKey(Identifier.withDefaultNamespace("equipment_asset"));

    public static ResourceKey<EquipmentAsset> EXAMPLE_KEY = ResourceKey.create(ROOT_ID,
            Identifier.fromNamespaceAndPath(DragonUniverse.MODID, "example"));

    public static final ArmorMaterial EXAMPLE_ARMOR_MATERIAL = new ArmorMaterial(29,
            makeDefense(5, 7, 9, 5, 11), 10, SoundEvents.ARMOR_EQUIP_DIAMOND,
            2.0F, 0.1F, DUTags.Items.EXAMPLE_TOOLS_REPAIRABLES, EXAMPLE_KEY);

    private static Map<ArmorType, Integer> makeDefense(int boots, int legs, int chest, int helm, int body) {
        return Maps.newEnumMap(
            Map.of(ArmorType.BOOTS, boots, ArmorType.LEGGINGS, legs, ArmorType.CHESTPLATE, chest, ArmorType.HELMET, helm, ArmorType.BODY, body)
                );
    }
}
