package com.draxer.dexrider.network;

import com.draxer.dexrider.DexRider;
import com.draxer.dexrider.entity.RiderAllyEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Cliente -> servidor: un boton del menu de un amigo Rider (ver {@link RiderAllyEntity#menuAction}). */
public record AllyActionPayload(int entityId, int action) implements CustomPacketPayload {

    public static final int CHAT = 0, TOGGLE = 1, LIVE_HERE = 2, JOB_MINE = 3, JOB_CHOP = 4, JOB_FARM = 5, JOB_CRAFT = 6,
            JOB_FLATTEN = 7, JOB_STOP = 8, ZONE_MARK = 9, ZONE_CLEAR = 10, FILTER_HAND = 11, FILTER_CLEAR = 12, TAKE_BAG = 13,
            TOPIC_BASE = 15, GIFT = 20, BACK = 21,   // conversacion: TOPIC_BASE + tema (0-4)
            JOB_FISH = 22, JOB_BREED = 23, JOB_SMELT = 24, JOB_GUARD = 25;

    public static final CustomPacketPayload.Type<AllyActionPayload> TYPE = new CustomPacketPayload.Type<>(DexRider.id("ally_action"));

    public static final StreamCodec<ByteBuf, AllyActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AllyActionPayload::entityId,
            ByteBufCodecs.VAR_INT, AllyActionPayload::action,
            AllyActionPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(AllyActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer p)) return;
        Entity e = p.level().getEntity(payload.entityId());
        if (e instanceof RiderAllyEntity ally && ally.distanceTo(p) < 10) ally.menuAction(p, payload.action());
    }
}
