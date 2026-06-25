package studio.elysium.dragonuniverse.world.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import studio.elysium.dragonuniverse.DragonUniverse;

import java.util.function.Supplier;

public class DUCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =  DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DragonUniverse.MODID);

    // TODO: Change ICON, translation key needed.
    // could prob make a collector or supplier to accept all custom items to not manually add one by one.
    public static final Supplier<CreativeModeTab> DU_ALL_TABS = CREATIVE_MODE_TABS.register("du_all_tab",
            () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(DUItems.EXAMPLE_ITEM.get()))
                    .title(Component.translatable("creativetab.dragonuniverse.du_all_tabs"))
                    .displayItems(((itemDisplayParameters, output) -> {
                        output.accept(DUItems.EXAMPLE_ITEM);
                    }))
                    .build());

    public static void register(IEventBus bus) {
        CREATIVE_MODE_TABS.register(bus);
    }
}
