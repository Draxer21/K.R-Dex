package com.draxer.dexrider.network;

import com.draxer.dexrider.DexRider;
import com.draxer.dexrider.rider.Dodge;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Cliente -> servidor: esquivar, con las teclas de movimiento pulsadas (adelante/atras, izquierda/derecha). */
public record DodgePayload(int forward, int strafe) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DodgePayload> TYPE = new CustomPacketPayload.Type<>(DexRider.id("dodge"));

    public static final StreamCodec<ByteBuf, DodgePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DodgePayload::forward, ByteBufCodecs.VAR_INT, DodgePayload::strafe, DodgePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleOnServer(DodgePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer p) {
                Dodge.dodge(p, Integer.signum(payload.forward()), Integer.signum(payload.strafe()));
            }
        });
    }
}
