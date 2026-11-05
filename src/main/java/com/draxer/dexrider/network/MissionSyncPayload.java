package com.draxer.dexrider.network;

import com.draxer.dexrider.DexRider;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Servidor -> cliente: cuantas misiones de los Kagami lleva cumplidas (van en orden). */
public record MissionSyncPayload(int done) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MissionSyncPayload> TYPE = new CustomPacketPayload.Type<>(DexRider.id("missions"));
    public static final StreamCodec<ByteBuf, MissionSyncPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(MissionSyncPayload::new, MissionSyncPayload::done);

    /** Lo que sabe el cliente (lo lee la pantalla de misiones). */
    public static int clientDone = 0;

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnClient(MissionSyncPayload payload, IPayloadContext context) {
        clientDone = payload.done();
    }

    public static void send(ServerPlayer p, int done) {
        if (p.connection == null || !p.connection.hasChannel(TYPE)) return;
        PacketDistributor.sendToPlayer(p, new MissionSyncPayload(done));
    }
}
