package studio.elysium.dragonuniverse;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import studio.elysium.dragonuniverse.AttachmentType.DUAttachmentTypes;
import studio.elysium.dragonuniverse.core.component.DUDataComponentTypes;
import studio.elysium.dragonuniverse.core.particles.DUParticles;
import studio.elysium.dragonuniverse.loot.DULootModifiers;
import studio.elysium.dragonuniverse.world.inventory.DUMenuTypes;
import studio.elysium.dragonuniverse.sounds.DUSounds;
import studio.elysium.dragonuniverse.world.entity.DUEntities;
import studio.elysium.dragonuniverse.world.entity.villager.DUVillagers;
import studio.elysium.dragonuniverse.world.fluids.DUFluidTypes;
import studio.elysium.dragonuniverse.world.fluids.DUFluids;
import studio.elysium.dragonuniverse.world.item.enchantment.effects.DUEnchantmentEffects;
import studio.elysium.dragonuniverse.world.level.biome.DUBiomes;
import studio.elysium.dragonuniverse.world.level.block.DUBlocks;
import studio.elysium.dragonuniverse.world.item.DUCreativeModeTabs;
import studio.elysium.dragonuniverse.world.item.DUItems;
import studio.elysium.dragonuniverse.world.item.consume_effects.DUConsumeEffects;
import studio.elysium.dragonuniverse.world.level.levelgen.feature.trunkplacers.DUTrunkPlacerTypes;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(DragonUniverse.MODID)
public class DragonUniverse {
    public static final String MODID = "dragonuniverse";
    public static final Logger LOGGER = LogUtils.getLogger();

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public DragonUniverse(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        DUCreativeModeTabs.register(modEventBus);
        DUItems.register(modEventBus);
        DUBlocks.register(modEventBus);

        DUDataComponentTypes.register(modEventBus);
        DUAttachmentTypes.register(modEventBus);

        DUConsumeEffects.register(modEventBus);
        DULootModifiers.register(modEventBus);

        DUSounds.register(modEventBus);

        DUVillagers.register(modEventBus);

        DUParticles.register(modEventBus);
        DUEnchantmentEffects.register(modEventBus);

        DUFluidTypes.register(modEventBus);
        DUFluids.register(modEventBus);

        DUTrunkPlacerTypes.register(modEventBus);

        DUEntities.register(modEventBus);

        DUMenuTypes.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (DragonUniverse) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ((FlowerPotBlock) Blocks.FLOWER_POT).addPlant(DUBlocks.EXAMPLE_SAPLING.getId(), DUBlocks.POTTED_EXAMPLE_SAPLING);

            DUBiomes.registerBiomes();
        });
    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {

    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }
}
