package com.draxer.dexrider.network;

import com.draxer.dexrider.DexRider;
import com.draxer.dexrider.menu.RideBookerMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Cliente -> servidor: tecla rapida del Ride Booker (carta anterior/siguiente, mazo, insertar). */
public record QuickBookerPayload(int action) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<QuickBookerPayload> TYPE =
            new CustomPacketPayload.Type<>(DexRider.id("quick_booker"));

    public static final StreamCodec<ByteBuf, QuickBookerPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, QuickBookerPayload::action, QuickBookerPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(QuickBookerPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) RideBookerMenu.quick(player, payload.action());
    }
}
