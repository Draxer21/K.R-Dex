package com.draxer.dexrider.network;

import com.draxer.dexrider.DexRider;
import com.draxer.dexrider.rider.Transformation;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Cliente -> servidor: "el jugador pulso la tecla de transformacion". No lleva datos. */
public record TransformPayload() implements CustomPacketPayload {

    public static final TransformPayload INSTANCE = new TransformPayload();

    public static final CustomPacketPayload.Type<TransformPayload> TYPE =
            new CustomPacketPayload.Type<>(DexRider.id("transform"));

    public static final StreamCodec<ByteBuf, TransformPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Se ejecuta en el hilo principal del servidor. */
    public static void handleOnServer(TransformPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            Transformation.toggle(player);
        }
    }
}
