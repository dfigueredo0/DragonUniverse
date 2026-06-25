package studio.elysium.dragonuniverse.loot;

import com.mojang.serialization.MapCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import studio.elysium.dragonuniverse.DragonUniverse;

import java.util.function.Supplier;

public class DULootModifiers {
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, DragonUniverse.MODID);

    public static final Supplier<MapCodec<AddItemStackModifier>> ADD_ITEMSTACK =
            LOOT_MODIFIERS.register("add_itemstack", () -> AddItemStackModifier.CODEC);

    public static void register(IEventBus eventBus) {
        LOOT_MODIFIERS.register(eventBus);
    }

}
