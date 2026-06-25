package studio.elysium.dragonuniverse;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import studio.elysium.dragonuniverse.AttachmentType.DUAttachmentTypes;
import studio.elysium.dragonuniverse.client.KeyMapping.DUKeyMappings;
import studio.elysium.dragonuniverse.client.particle.DUVFXParticle;
import studio.elysium.dragonuniverse.client.particle.ZirconParticle;
import studio.elysium.dragonuniverse.client.render.debug.DUImGui;
import studio.elysium.dragonuniverse.client.render.debug.DUImGuiScreen;
import studio.elysium.dragonuniverse.client.renderer.entity.ChairRenderer;
import studio.elysium.dragonuniverse.core.particles.DUParticles;
import studio.elysium.dragonuniverse.network.packet.TestPacketC2S;
import studio.elysium.dragonuniverse.world.entity.DUEntities;
import studio.elysium.dragonuniverse.world.fluids.DUFluidTypes;
import studio.elysium.dragonuniverse.world.fluids.DUFluids;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = DragonUniverse.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = DragonUniverse.MODID, value = Dist.CLIENT)
public class DragonUniverseClient {
    public DragonUniverseClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        DUKeyMappings.register();
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        EntityRenderers.register(DUEntities.CHAIR.get(), ChairRenderer::new);

        // TODO: Finish
        // EntityRenderers.register(DUEntities.NIMBUS.get(), NimbusRenderer::new);
        // EntityRenderers.register(DUEntities.SPACE_POD.get(), SpacePodRenderer::new);

        event.enqueueWork(() -> {
            if (DUImGui.available()) {
                DUImGui.init();
            }
        });
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        // TODO: Finish
        // event.registerLayerDefinition(DUModelLayerLocations.NIMBUS, NimbusModel::createBodyLayer);
        // event.registerLayerDefinition(DUModelLayerLocations.SPACE_POD, SpacePodModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void onComputeFOVModifierEvent(ComputeFovModifierEvent event) {
        /**
        * check if player is pressing zoom button (or check telescope item code)
         * Change code a bit used for a Bow originally
         * float fovModifier = 1.0F; test diff values
         * int ticksUsingItem = event.getPlayer().getTicksUsingItem();
         * float scale = Math.min(ticksUsingItem / 20.0F, 1.0F);
         * fovModifier *= 1.0F - Mth.square(scale) * 0.15F;
         * event.setNewFovModifier(Mth.lerp(Minecraft.getInstance().options.fovEffectScale().get().floatValue(), 1.0F, fovModifier));
        * */
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(DUKeyMappings.DRAGON_UNIVERSE_CATEGORY);

        event.register(DUKeyMappings.PRESS_ZOOM_KEY.get());
        event.register(DUKeyMappings.TOGGLE_DEBUG_IMGUI_KEY.get());
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (DUKeyMappings.PRESS_ZOOM_KEY.get().consumeClick()) {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("Pressing Zoom Key!"));
            ClientPacketDistributor.sendToServer(new TestPacketC2S("Daedalus", 6));
        }

        if (DUKeyMappings.TOGGLE_DEBUG_IMGUI_KEY.get().consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            DUImGui.toggle();
            // Open a transparent, non-pausing screen while enabled so the cursor is freed and the
            // overlay is clickable; closing it (ESC or the toggle key) syncs DUImGui back off.
            if (DUImGui.enabled()) {
                mc.setScreen(new DUImGuiScreen());
            } else if (mc.screen instanceof DUImGuiScreen) {
                mc.setScreen(null);
            }
        }
    }

    @SubscribeEvent
    public static void registerHUD(RegisterGuiLayersEvent event) {
        // TODO: Change from sprite to be a bar, would have to change assets location most likely too.
        event.registerAboveAll(Identifier.fromNamespaceAndPath(DragonUniverse.MODID, "mana_bar"),
                (guiGraphics, deltaTracker) -> {
                    int x = guiGraphics.guiWidth() / 2;
                    int y = guiGraphics.guiHeight();

                    if(!Minecraft.getInstance().player.isCreative() && !Minecraft.getInstance().player.isSpectator()
                        && Minecraft.getInstance().player.hasData(DUAttachmentTypes.MANA)) {
                        for (int i = 0; i < 5; i++) {
                            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.fromNamespaceAndPath(DragonUniverse.MODID, "mana_icon_bg"),
                                    16, 16, 0, 0,x - 95 + i * 18, y - 55, 16, 16);
                        }

                        for (int i = 0; i < Minecraft.getInstance().player.getData(DUAttachmentTypes.MANA); i++) {
                            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.fromNamespaceAndPath(DragonUniverse.MODID, "mana_icon"),
                                    16, 16, 0, 0,x - 95 + i * 18, y - 55, 16, 16);
                        }
                    }
                });
    }

    @SubscribeEvent
    public static void registerParticleFactories(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(DUParticles.ZIRCON_PARTICLES.get(), ZirconParticle.Provider::new);
        event.registerSpriteSet(DUParticles.DU_VFX.get(), DUVFXParticle.Provider::new);
    }

    @SubscribeEvent
    public static void registerOnClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(DUFluidTypes.EXAMPLE_FLUID_EXTENSION, DUFluidTypes.EXAMPLE_FLUID_TYPE.get());
    }

    @SubscribeEvent
    public static void registerFluidModelsEvent(RegisterFluidModelsEvent event) {
        FluidModel.Unbaked exampleWaterModel = new FluidModel.Unbaked(
                new Material(Identifier.withDefaultNamespace("block/water_still")),
                new Material(Identifier.withDefaultNamespace("block/water_flow")),
                new Material(Identifier.withDefaultNamespace("block/water_overlay")),
                state -> 0xA1eb1734
        );

        event.register(exampleWaterModel, DUFluids.EXAMPLE_WATER_SOURCE.get());
        event.register(exampleWaterModel, DUFluids.EXAMPLE_WATER_FLOWING.get());
    }

    @SubscribeEvent
    public static void registerBER(EntityRenderersEvent.RegisterRenderers event) {
        //event.registerBlockEntityRenderer(DUBlockEntities.ENTITY.get(), ENTITYBlockEntityRenderer::new);
    }
}
