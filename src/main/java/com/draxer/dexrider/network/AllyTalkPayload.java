package com.draxer.dexrider.network;

import com.draxer.dexrider.DexRider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Servidor -> cliente: la ventana de conversacion con un amigo (confianza, temas abiertos y lo que acaba de decir). */
public record AllyTalkPayload(int entityId, String rider, int trust, int topics, String lineKey) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AllyTalkPayload> TYPE = new CustomPacketPayload.Type<>(DexRider.id("ally_talk"));

    public static final StreamCodec<FriendlyByteBuf, AllyTalkPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entityId);
                buf.writeUtf(p.rider);
                buf.writeVarInt(p.trust);
                buf.writeVarInt(p.topics);
                buf.writeUtf(p.lineKey);
            },
            buf -> new AllyTalkPayload(buf.readVarInt(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readUtf()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnClient(AllyTalkPayload payload, IPayloadContext context) {
        com.draxer.dexrider.client.AllyTalkScreen.receive(payload);
    }

    public static void send(ServerPlayer p, AllyTalkPayload payload) {
        if (p.connection == null || !p.connection.hasChannel(TYPE)) return;
        PacketDistributor.sendToPlayer(p, payload);
    }
}
