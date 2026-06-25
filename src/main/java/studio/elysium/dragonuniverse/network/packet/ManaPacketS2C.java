package studio.elysium.dragonuniverse.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import studio.elysium.dragonuniverse.DragonUniverse;

public record ManaPacketS2C(int oldValue, int newValue) implements CustomPacketPayload {
    public static final Type<ManaPacketS2C> TYPE = new Type<>(Identifier.fromNamespaceAndPath(DragonUniverse.MODID, "mana_packet"));

    public static final StreamCodec<ByteBuf, ManaPacketS2C> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            ManaPacketS2C::oldValue,
            ByteBufCodecs.VAR_INT,
            ManaPacketS2C::newValue,
            ManaPacketS2C::new
    );
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
