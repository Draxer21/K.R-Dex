package com.draxer.dexrider.rider;

import com.draxer.dexrider.registry.DexItems;
import com.draxer.dexrider.registry.DexSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Como consigue Dex el equipo de Cipher (como en el fanfic). */
public final class CipherGifts {
    private CipherGifts() {}

    /** Takumi te presta el reloj Accel (al vencerlo despues de que lo usara, o tras usarlo a tu lado). */
    public static void giveAccel(ServerLevel level, Entity takumi, ServerPlayer player) {
        give(level, takumi, player, KamenRide.CIPHER, DexItems.CIPHER_ACCEL.get(), "message.dexrider.got_accel");
    }

    /** Genzo saca el equipo Blaster de la casa de la montania (cuando Takumi y el son tus amigos). */
    public static void giveBlaster(ServerLevel level, Entity speaker, ServerPlayer player) {
        give(level, speaker, player, KamenRide.ORGA, DexItems.CIPHER_BLASTER.get(), "message.dexrider.got_blaster");
    }

    private static void give(ServerLevel level, Entity speaker, ServerPlayer player, KamenRide who, Item item, String msg) {
        if (KTouch.has(player, item)) return;
        ItemStack stack = new ItemStack(item);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        RiderDialogue.force(level, speaker, who, RiderDialogue.Line.GIFT);
        DexSounds.play(level, player, DexSounds.SCAN_COMPLETE, 1.0F, 1.2F);
        player.sendSystemMessage(Component.translatable(msg).withStyle(ChatFormatting.GOLD));
    }
}
