package com.draxer.dexrider.network;

import com.draxer.dexrider.DexRider;
import com.draxer.dexrider.item.DexDriverItem;
import com.draxer.dexrider.item.SuitConfig;
import com.draxer.dexrider.registry.DexComponents;
import com.draxer.dexrider.registry.DexItems;
import com.draxer.dexrider.registry.DexSounds;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Cliente -> servidor: "guarda esta personalizacion del traje". */
public record SuitPayload(SuitConfig config) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SuitPayload> TYPE =
            new CustomPacketPayload.Type<>(DexRider.id("suit"));

    public static final StreamCodec<ByteBuf, SuitPayload> STREAM_CODEC =
            SuitConfig.STREAM_CODEC.map(SuitPayload::new, SuitPayload::config);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(SuitPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        ItemStack driver = DexDriverItem.findDriver(player);
        if (driver.isEmpty()) return;
        driver.set(DexComponents.SUIT.get(), payload.config());

        // Si esta transformado, el cambio se ve al instante (el peto lleva la copia que leen los demas)
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.is(DexItems.DEX_CHESTPLATE.get())) chest.set(DexComponents.SUIT.get(), payload.config());

        if (player.level() instanceof ServerLevel level) DexSounds.play(level, player, DexSounds.MODE_SWITCH, 0.8F, 1.1F);
        player.sendSystemMessage(Component.translatable("message.dexrider.suit_saved"), true);
    }
}
