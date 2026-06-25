package studio.elysium.dragonuniverse.world.item.enchantment.effects;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import studio.elysium.dragonuniverse.DragonUniverse;

import java.util.function.Supplier;

public class DUEnchantmentEffects {
    public static final DeferredRegister<MapCodec<? extends EnchantmentEntityEffect>> ENTITY_ENCHANTMENT_EFFECTS =
            DeferredRegister.create(Registries.ENCHANTMENT_ENTITY_EFFECT_TYPE, DragonUniverse.MODID);

    public static final Supplier<MapCodec<? extends EnchantmentEntityEffect>> LIGHTNING_STRIKER =
            ENTITY_ENCHANTMENT_EFFECTS.register("lightning_striker", () -> LightningStrikeEnchantmentEffect.CODEC);

    public static void register(IEventBus bus) {
        ENTITY_ENCHANTMENT_EFFECTS.register(bus);
    }
}
