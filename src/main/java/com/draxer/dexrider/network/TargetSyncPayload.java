package com.draxer.dexrider.network;

import com.draxer.dexrider.DexRider;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Servidor -> cliente: la Lectura del Driver (vida del objetivo, tu golpe, su golpe y los golpes que faltan).
 * dealt / taken = -1 si todavia no hay dato. hits / survive = 0 si no se puede estimar.
 */
public record TargetSyncPayload(boolean active, int entityId, float hp, float maxHp, float dealt, int hits, float taken, int survive)
        implements CustomPacketPayload {

    public static final TargetSyncPayload EMPTY = new TargetSyncPayload(false, -1, 0, 1, -1, 0, -1, 0);

    public static final CustomPacketPayload.Type<TargetSyncPayload> TYPE = new CustomPacketPayload.Type<>(DexRider.id("target_sync"));

    public static final StreamCodec<ByteBuf, TargetSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                ByteBufCodecs.BOOL.encode(buf, p.active);
                ByteBufCodecs.VAR_INT.encode(buf, p.entityId);
                ByteBufCodecs.FLOAT.encode(buf, p.hp);
                ByteBufCodecs.FLOAT.encode(buf, p.maxHp);
                ByteBufCodecs.FLOAT.encode(buf, p.dealt);
                ByteBufCodecs.VAR_INT.encode(buf, p.hits);
                ByteBufCodecs.FLOAT.encode(buf, p.taken);
                ByteBufCodecs.VAR_INT.encode(buf, p.survive);
            },
            buf -> new TargetSyncPayload(ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static volatile TargetSyncPayload last = EMPTY;
    public static volatile long receivedAt = 0;

    public static void handleOnClient(TargetSyncPayload payload, IPayloadContext context) {
        last = payload;
        receivedAt = System.currentTimeMillis();
    }

    public static void send(ServerPlayer p, TargetSyncPayload payload) {
        if (p.connection == null || !p.connection.hasChannel(TYPE)) return;   // cliente sin el mod (o jugador simulado)
        PacketDistributor.sendToPlayer(p, payload);
    }
}
