package studio.elysium.dragonuniverse.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import studio.elysium.dragonuniverse.AttachmentType.DUAttachmentTypes;
import studio.elysium.dragonuniverse.network.packet.ManaPacketS2C;

public class ClientboundPackets {
    public static void handleManaPacket(ManaPacketS2C manaPacketS2C, IPayloadContext iPayloadContext) {
        iPayloadContext.player().setData(DUAttachmentTypes.MANA, manaPacketS2C.newValue());
    }
}
