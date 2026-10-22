package com.draxer.dexrider.network;

import com.draxer.dexrider.DexRider;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Servidor -> clientes: "este jugador hace la pose X" (henshin, card, kick, finisher, complete). */
public record PosePayload(int entityId, String pose) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PosePayload> TYPE = new CustomPacketPayload.Type<>(DexRider.id("pose"));
    public static final StreamCodec<ByteBuf, PosePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PosePayload::entityId, ByteBufCodecs.STRING_UTF8, PosePayload::pose, PosePayload::new);

    public static final String PAL = "player_animation_library";

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnClient(PosePayload payload, IPayloadContext context) {
        if (!ModList.get().isLoaded(PAL)) return;
        context.enqueueWork(() -> com.draxer.dexrider.client.PoseClient.play(payload.entityId(), payload.pose()));
    }

    /** Pose para el jugador y todos los que lo ven. */
    public static void send(ServerPlayer p, String pose) {
        try {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new PosePayload(p.getId(), pose));
        } catch (RuntimeException e) {
            DexRider.LOGGER.debug("No se pudo enviar la pose {}", pose, e);
        }
    }
}
