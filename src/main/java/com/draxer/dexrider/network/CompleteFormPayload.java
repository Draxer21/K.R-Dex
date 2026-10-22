package com.draxer.dexrider.network;

import com.draxer.dexrider.DexRider;
import com.draxer.dexrider.rider.KTouch;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Cliente -> servidor: se pulsaron los diez pads del K-Touch. El servidor vuelve a comprobarlo todo. */
public record CompleteFormPayload() implements CustomPacketPayload {

    public static final CompleteFormPayload INSTANCE = new CompleteFormPayload();

    public static final CustomPacketPayload.Type<CompleteFormPayload> TYPE =
            new CustomPacketPayload.Type<>(DexRider.id("complete_form"));

    public static final StreamCodec<ByteBuf, CompleteFormPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(CompleteFormPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        // desde la pantalla de amigos, el blason de Dex tambien sirve para salir de la Complete Form
        if (com.draxer.dexrider.rider.KamenRide.current(player) == com.draxer.dexrider.rider.KamenRide.COMPLETE
                && player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            KTouch.exit(level, player, false);
        } else {
            KTouch.enter(player);
        }
    }
}
