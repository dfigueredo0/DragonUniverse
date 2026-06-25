package studio.elysium.dragonuniverse.world.item.trading;

import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.TradeCost;
import net.minecraft.world.item.trading.VillagerTrade;
import studio.elysium.dragonuniverse.DragonUniverse;
import studio.elysium.dragonuniverse.world.item.DUItems;

import java.util.List;
import java.util.Optional;

public class DUVillagerTrades {
    public static final ResourceKey<VillagerTrade> FARMER_1_EMERALD_RADISH = createKey("farmer/1/emerald_radish");
    public static final ResourceKey<VillagerTrade> FARMER_1_DIAMOND_RADISH_SEEDS = createKey("farmer/1/diamond_radish_seeds");

    public static final ResourceKey<VillagerTrade> EXAMPLEGER_1_EMERALD_RADISH = createKey("exampleperer/1/emerald_radish");
    public static final ResourceKey<VillagerTrade> EXAMPLEGER_2_DIAMOND_RADISH = createKey("exampleperer/2/diamond_radish");

    public static void bootstrap(BootstrapContext<VillagerTrade> bootstrapContext) {
        register(bootstrapContext, FARMER_1_EMERALD_RADISH, new VillagerTrade(
                new TradeCost(Items.EMERALD, 4),
                new ItemStackTemplate(DUItems.RADISH, 2),
                12, 12, 0.05F, Optional.empty(), // merchantPredicate limits what type of villager can have this trade
                List.of() // for enchanted items
        ));
        register(bootstrapContext, FARMER_1_DIAMOND_RADISH_SEEDS, new VillagerTrade(
                new TradeCost(Items.DIAMOND, 4),
                new ItemStackTemplate(DUItems.RADISH, 2),
                12, 12, 0.05F, Optional.empty(), List.of()
        ));

        register(bootstrapContext, EXAMPLEGER_1_EMERALD_RADISH, new VillagerTrade(
                new TradeCost(Items.EMERALD, 4),
                new ItemStackTemplate(DUItems.RADISH, 2),
                12, 12, 0.05F, Optional.empty(), List.of()
        ));
        register(bootstrapContext, EXAMPLEGER_2_DIAMOND_RADISH, new VillagerTrade(
                new TradeCost(Items.DIAMOND, 4),
                new ItemStackTemplate(DUItems.RADISH, 5),
                12, 12, 0.05F, Optional.empty(), List.of()
        ));
    }

    private static ResourceKey<VillagerTrade> createKey(String id) {
        return ResourceKey.create(Registries.VILLAGER_TRADE, Identifier.fromNamespaceAndPath(DragonUniverse.MODID, id));
    }

    private static void register(BootstrapContext<VillagerTrade> context, ResourceKey<VillagerTrade> key, VillagerTrade trade) {
        context.register(key, trade);
    }
}
