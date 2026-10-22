package com.draxer.dexrider.network;

import com.draxer.dexrider.DexRider;
import com.draxer.dexrider.menu.RideBookerMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Cliente -> servidor: abrir el Ride Booker. */
public record OpenBookerPayload() implements CustomPacketPayload {

    public static final OpenBookerPayload INSTANCE = new OpenBookerPayload();

    public static final CustomPacketPayload.Type<OpenBookerPayload> TYPE =
            new CustomPacketPayload.Type<>(DexRider.id("open_booker"));

    public static final StreamCodec<ByteBuf, OpenBookerPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(OpenBookerPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) RideBookerMenu.open(player);
    }
}
