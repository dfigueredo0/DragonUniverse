package studio.elysium.dragonuniverse.AttachmentType;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import studio.elysium.dragonuniverse.DragonUniverse;

import java.util.function.Supplier;

public class DUAttachmentTypes {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, DragonUniverse.MODID);

    public static final Supplier<AttachmentType<Integer>> MANA = ATTACHMENT_TYPES.register(
            "mana", () -> AttachmentType.builder(() -> 0)
                    .sync(ByteBufCodecs.INT)
                    .serialize(Codec.INT.fieldOf("mana")).build()
    );

    public static final Supplier<AttachmentType<BlockPos>> HOME_POS = ATTACHMENT_TYPES.register("home_pos",
            () -> AttachmentType.<BlockPos>builder(() -> BlockPos.ZERO)
                    .sync(BlockPos.STREAM_CODEC)
                    .serialize(BlockPos.CODEC.fieldOf("home_pos")).build());

    public static void register(IEventBus bus) {
        ATTACHMENT_TYPES.register(bus);
    }
}
