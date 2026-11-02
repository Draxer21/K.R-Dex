package com.draxer.dexrider.rider;

import com.draxer.dexrider.registry.DexItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * La primera vez que un jugador entra a un mundo: la carta de Genzo (en el chat) y la brujula de los Kagami, que apunta
 * a la casa de la montania, donde guardan el Dex Driver de repuesto con sus tres primeras cartas. Mas la guia.
 */
public final class Starter {
    private Starter() {}

    /** Logro oculto "starter" (sin aviso): marca que ya se entrego, aunque el jugador muera. */
    public static boolean onLogin(ServerPlayer p) {
        if (DexAdvancements.has(p, "starter")) return false;
        DexAdvancements.award(p, "starter");
        give(p, new ItemStack(DexItems.KAGAMI_COMPASS.get()));
        give(p, new ItemStack(DexItems.RIDER_GUIDE.get()));
        p.sendSystemMessage(Component.translatable("message.dexrider.starter_title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        for (int i = 1; i <= 3; i++)
            p.sendSystemMessage(Component.translatable("message.dexrider.starter_" + i).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        p.sendSystemMessage(Component.translatable("message.dexrider.starter_hint").withStyle(ChatFormatting.YELLOW));
        return true;
    }

    private static void give(ServerPlayer p, ItemStack stack) {
        if (!p.getInventory().add(stack)) p.drop(stack, false);
    }
}
