package studio.elysium.dragonuniverse.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import studio.elysium.dragonuniverse.AttachmentType.DUAttachmentTypes;
import studio.elysium.dragonuniverse.AttachmentType.handler.ManaHandler;
import studio.elysium.dragonuniverse.DragonUniverse;
import studio.elysium.dragonuniverse.command.ReturnHomeCommand;
import studio.elysium.dragonuniverse.command.SetHomeCommand;
import studio.elysium.dragonuniverse.network.ClientboundPackets;
import studio.elysium.dragonuniverse.network.ServerboundPackets;
import studio.elysium.dragonuniverse.network.packet.ManaPacketS2C;
import studio.elysium.dragonuniverse.network.packet.TestPacketC2S;

@EventBusSubscriber(modid = DragonUniverse.MODID)
public class DUEvents {
    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(TestPacketC2S.TYPE, TestPacketC2S.STREAM_CODEC, ServerboundPackets::handleTestPacket);
        registrar.playToClient(ManaPacketS2C.TYPE, ManaPacketS2C.STREAM_CODEC, ClientboundPackets::handleManaPacket);
    }

    @SubscribeEvent
    public static void setPlayersManaOnSpawn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player.hasData(DUAttachmentTypes.MANA)) {
            ManaHandler.setMana((ServerPlayer) player, player.getData(DUAttachmentTypes.MANA));
        } else {
            ManaHandler.setMana((ServerPlayer) player, 5);
        }
    }

    @SubscribeEvent
    public static void setPlayersManaOnClone(PlayerEvent.Clone event) {
        Player player = event.getEntity();
        ManaHandler.setMana((ServerPlayer) player, event.getOriginal().getData(DUAttachmentTypes.MANA));
    }

    @SubscribeEvent
    public static void setPlayersManaOnDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        ManaHandler.setMana((ServerPlayer) player, player.getData(DUAttachmentTypes.MANA));
    }

    @SubscribeEvent
    public static void setPlayersManaOnRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        ManaHandler.setMana((ServerPlayer) player, player.getData(DUAttachmentTypes.MANA));
    }

    @SubscribeEvent
    public static void onCommandsRegister(RegisterCommandsEvent event) {
        SetHomeCommand.register(event.getDispatcher());
        ReturnHomeCommand.register(event.getDispatcher());
    }
    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        // event.put(DUEntities.ENTITY.get(), ENTITY.createENTITYAttributes().build())
    }

    @SubscribeEvent
    public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        // event.register(DUEntities.ENTITY.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
        // PathFinderMob::checkMobSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}
