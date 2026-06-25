package studio.elysium.dragonuniverse.AttachmentType.handler;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import studio.elysium.dragonuniverse.AttachmentType.DUAttachmentTypes;
import studio.elysium.dragonuniverse.network.packet.ManaPacketS2C;

public class ManaHandler {
    public static void setMana(ServerPlayer player, int val) {
        player.setData(DUAttachmentTypes.MANA, val);
        //PacketDistributor.sendToPlayer(player, new ManaPacketS2C(0, val));
    }

    public static void addMana(ServerPlayer player, int val) {
        int newVal = player.getData(DUAttachmentTypes.MANA) + val;
        setMana(player, newVal);
    }

    public static void decrementMana(ServerPlayer player, int val) {
        int newVal = player.getData(DUAttachmentTypes.MANA) - val;
        setMana(player, newVal);
    }

    public static int getMana(Player player) {
        return player.getData(DUAttachmentTypes.MANA);
    }
}
