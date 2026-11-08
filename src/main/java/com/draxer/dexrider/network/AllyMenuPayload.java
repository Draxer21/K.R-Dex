package com.draxer.dexrider.network;

import com.draxer.dexrider.DexRider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Servidor -> cliente: abrir (o refrescar) el menu de un amigo Rider con lo que sabe y lo que esta haciendo.
 * xp por trabajo en el orden mine, chop, farm, craft. zone = medidas (0 si no hay).
 */
public record AllyMenuPayload(int entityId, String rider, boolean summoned, boolean hasHome, String job,
                              int mine, int chop, int farm, int craft, String filter,
                              int zoneX, int zoneY, int zoneZ, int bag, int recipes, int blocks, int trust, int plan) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AllyMenuPayload> TYPE = new CustomPacketPayload.Type<>(DexRider.id("ally_menu"));

    public static final StreamCodec<FriendlyByteBuf, AllyMenuPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entityId);
                buf.writeUtf(p.rider);
                buf.writeBoolean(p.summoned);
                buf.writeBoolean(p.hasHome);
                buf.writeUtf(p.job);
                buf.writeVarInt(p.mine);
                buf.writeVarInt(p.chop);
                buf.writeVarInt(p.farm);
                buf.writeVarInt(p.craft);
                buf.writeUtf(p.filter);
                buf.writeVarInt(p.zoneX);
                buf.writeVarInt(p.zoneY);
                buf.writeVarInt(p.zoneZ);
                buf.writeVarInt(p.bag);
                buf.writeVarInt(p.recipes);
                buf.writeVarInt(p.blocks);
                buf.writeVarInt(p.trust);
                buf.writeVarInt(p.plan);
            },
            buf -> new AllyMenuPayload(buf.readVarInt(), buf.readUtf(), buf.readBoolean(), buf.readBoolean(), buf.readUtf(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnClient(AllyMenuPayload payload, IPayloadContext context) {
        com.draxer.dexrider.client.AllyMenuScreen.receive(payload);
    }

    public static void send(ServerPlayer p, AllyMenuPayload payload) {
        if (p.connection == null || !p.connection.hasChannel(TYPE)) return;
        PacketDistributor.sendToPlayer(p, payload);
    }
}
