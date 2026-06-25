package studio.elysium.dragonuniverse.world.item.Item;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.Equippable;
import org.jspecify.annotations.Nullable;
import studio.elysium.dragonuniverse.world.item.equipment.DUArmorMaterial;

import java.util.List;
import java.util.Map;

public class DUArmorItem extends Item {
    private static final Map<ArmorMaterial, List<MobEffectInstance>> MATERIAL_TO_EFFECT_MAP =
            (new ImmutableMap.Builder<ArmorMaterial, List<MobEffectInstance>>())
                    .put(DUArmorMaterial.EXAMPLE_ARMOR_MATERIAL, List.of(
                            new MobEffectInstance(MobEffects.JUMP_BOOST, 200, 1, false, false),
                            new MobEffectInstance(MobEffects.GLOWING, 200, 1, false, false)))
                    .build();

    public DUArmorItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack itemStack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        if(owner instanceof Player player && hasFullSuitOfArmorOn(player)) {
            evaluateArmorEffects(player);
        }
    }

    private void evaluateArmorEffects(Player player) {
        for(Map.Entry<ArmorMaterial, List<MobEffectInstance>> entry : MATERIAL_TO_EFFECT_MAP.entrySet()) {
            ArmorMaterial material = entry.getKey();
            List<MobEffectInstance> effects = entry.getValue();

            if(hasPlayerCorrectArmorOn(material, player)) {
                addEffectToPlayer(player, effects);
            }
        }
    }

    private void addEffectToPlayer(Player player, List<MobEffectInstance> effects) {
        boolean hasEffect = effects.stream().allMatch(effect -> player.hasEffect(effect.getEffect()));

        if(!hasEffect) {
            for(MobEffectInstance effect : effects) {
                player.addEffect(new MobEffectInstance(effect.getEffect(),
                        effect.getDuration(), effect.getAmplifier(), effect.isAmbient(), effect.isVisible()));
            }
        }
    }

    private boolean hasPlayerCorrectArmorOn(ArmorMaterial material, Player player) {
        Equippable boots = player.getItemBySlot(EquipmentSlot.FEET).getComponents().get(DataComponents.EQUIPPABLE);
        Equippable leggings = player.getItemBySlot(EquipmentSlot.LEGS).getComponents().get(DataComponents.EQUIPPABLE);
        Equippable chestplate = player.getItemBySlot(EquipmentSlot.CHEST).getComponents().get(DataComponents.EQUIPPABLE);
        Equippable helmet = player.getItemBySlot(EquipmentSlot.HEAD).getComponents().get(DataComponents.EQUIPPABLE);

        return boots.assetId().get().equals(material.assetId()) &&
                leggings.assetId().get().equals(material.assetId()) &&
                chestplate.assetId().get().equals(material.assetId()) &&
                helmet.assetId().get().equals(material.assetId());
    }

    private boolean hasFullSuitOfArmorOn(Player player) {
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        ItemStack leggings = player.getItemBySlot(EquipmentSlot.LEGS);
        ItemStack chestplate = player.getItemBySlot(EquipmentSlot.CHEST);
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);

        return !boots.isEmpty() && !leggings.isEmpty() && !chestplate.isEmpty() && !helmet.isEmpty();
    }
}
