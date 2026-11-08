package com.draxer.dexrider.network;

import com.draxer.dexrider.DexRider;
import com.draxer.dexrider.rider.Moveset;
import com.draxer.dexrider.rider.Stamina;
import com.draxer.dexrider.rider.Transformation;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Servidor -> cliente: aguante y el ultimo movimiento del moveset (nombre, paso de la cadena y largo). */
public record CombatSyncPayload(int stamina, int max, String move, int step, int chain) implements CustomPacketPayload {

    public static final CombatSyncPayload EMPTY = new CombatSyncPayload(-1, 100, "", 0, 0);

    public static final CustomPacketPayload.Type<CombatSyncPayload> TYPE = new CustomPacketPayload.Type<>(DexRider.id("combat_sync"));

    public static final StreamCodec<ByteBuf, CombatSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CombatSyncPayload::stamina, ByteBufCodecs.VAR_INT, CombatSyncPayload::max,
            ByteBufCodecs.STRING_UTF8, CombatSyncPayload::move, ByteBufCodecs.VAR_INT, CombatSyncPayload::step,
            ByteBufCodecs.VAR_INT, CombatSyncPayload::chain, CombatSyncPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static volatile CombatSyncPayload last = EMPTY;
    public static volatile long receivedAt = 0;

    public static void handleOnClient(CombatSyncPayload payload, IPayloadContext context) {
        last = payload;
        receivedAt = System.currentTimeMillis();
    }

    private static final Map<UUID, CombatSyncPayload> SENT = new HashMap<>();

    /** Cada 2 ticks: solo si cambio algo. */
    public static void sync(ServerPlayer p) {
        if (p.connection == null || !p.connection.hasChannel(TYPE)) return;
        CombatSyncPayload now = Transformation.isTransformed(p)
                ? new CombatSyncPayload(Stamina.get(p), Stamina.max(p), nz(Moveset.shownMove(p)), Moveset.shownStep(p), Moveset.chainLength(p))
                : EMPTY;
        if (now.equals(SENT.get(p.getUUID()))) return;
        SENT.put(p.getUUID(), now);
        PacketDistributor.sendToPlayer(p, now);
    }

    private static String nz(String s) { return s == null ? "" : s; }

    public static void forget(UUID id) { SENT.remove(id); }
}
