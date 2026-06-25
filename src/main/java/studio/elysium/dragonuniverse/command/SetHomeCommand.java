package studio.elysium.dragonuniverse.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import studio.elysium.dragonuniverse.AttachmentType.DUAttachmentTypes;

public class SetHomeCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("home").then(Commands.literal("set").executes(SetHomeCommand::run)));
    }

    private static int run(CommandContext<CommandSourceStack> context) {
        Player player = context.getSource().getPlayer();
        if  (player == null) {
            context.getSource().sendFailure(Component.literal("Command wasn't send by a player!"));
            return -1;
        }

        BlockPos pos = player.blockPosition() != BlockPos.ZERO ? player.blockPosition() : BlockPos.ZERO.above(1);
        String posStr = "(" + pos.toShortString() + ")";
        player.setData(DUAttachmentTypes.HOME_POS, pos);

        context.getSource().sendSuccess(() -> Component.literal("Set home at " +  posStr), false);
        return 1;
    }
}