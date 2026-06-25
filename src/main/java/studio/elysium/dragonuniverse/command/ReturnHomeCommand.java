package studio.elysium.dragonuniverse.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import studio.elysium.dragonuniverse.AttachmentType.DUAttachmentTypes;

public class ReturnHomeCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("home").then(Commands.literal("return").executes(ReturnHomeCommand::run)));
    }

    private static int run(CommandContext<CommandSourceStack> context) {
        Player player = context.getSource().getPlayer();
        if  (player == null) {
            context.getSource().sendFailure(Component.literal("Command not sent by a player."));
            return -1;
        }
        BlockPos pos = player.getData(DUAttachmentTypes.HOME_POS);

        if (pos != BlockPos.ZERO) {
            String posString = "(" + pos.toShortString() + ")";
            player.teleportTo(pos.getX(), pos.getY(), pos.getZ());
            context.getSource().sendSuccess(() -> Component.literal("Teleported to " + posString), false);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("No Homes found."));
            return -1;
        }
    }
}
