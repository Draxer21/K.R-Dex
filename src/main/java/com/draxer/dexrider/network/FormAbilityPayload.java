package com.draxer.dexrider.network;

import com.draxer.dexrider.DexRider;
import com.draxer.dexrider.rider.FormAbilities;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Cliente -> servidor: "el jugador pulso la tecla de habilidad de forma (R)". Shift se lee en el servidor. */
public record FormAbilityPayload() implements CustomPacketPayload {

    public static final FormAbilityPayload INSTANCE = new FormAbilityPayload();

    public static final CustomPacketPayload.Type<FormAbilityPayload> TYPE =
            new CustomPacketPayload.Type<>(DexRider.id("form_ability"));

    public static final StreamCodec<ByteBuf, FormAbilityPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(FormAbilityPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            FormAbilities.use(player);
        }
    }
}
