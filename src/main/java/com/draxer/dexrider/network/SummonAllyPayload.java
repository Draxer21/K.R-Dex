package com.draxer.dexrider.network;

import com.draxer.dexrider.DexRider;
import com.draxer.dexrider.rider.AllyManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Cliente -> servidor: invocar (o despedir) al amigo Rider del pad 'index' del anillo (0-8). */
public record SummonAllyPayload(int index) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SummonAllyPayload> TYPE =
            new CustomPacketPayload.Type<>(DexRider.id("summon_ally"));

    public static final StreamCodec<ByteBuf, SummonAllyPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SummonAllyPayload::index, SummonAllyPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(SummonAllyPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) AllyManager.summonByIndex(player, payload.index());
    }
}
