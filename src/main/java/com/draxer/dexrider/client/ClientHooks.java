package com.draxer.dexrider.client;

import com.draxer.dexrider.item.SuitConfig;
import com.draxer.dexrider.registry.DexComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/** Llamadas que solo existen en el cliente (se usan desde los items solo cuando el mundo es del cliente). */
public final class ClientHooks {
    private ClientHooks() {}

    public static void openSuitScreen(ItemStack driver) {
        SuitConfig config = driver.get(DexComponents.SUIT.get());
        Minecraft.getInstance().setScreen(new SuitScreen(config));
    }

    public static void openSummonScreen() {
        Minecraft.getInstance().setScreen(new SummonScreen());
    }

    public static void openGuide() {
        java.util.List<net.minecraft.network.chat.Component> pages = new java.util.ArrayList<>();
        for (int i = 1; i <= com.draxer.dexrider.item.RiderGuideItem.PAGES; i++) {
            pages.add(net.minecraft.network.chat.Component.translatable("guide.dexrider.page" + i));
        }
        Minecraft.getInstance().setScreen(new net.minecraft.client.gui.screens.inventory.BookViewScreen(
                new net.minecraft.client.gui.screens.inventory.BookViewScreen.BookAccess(pages)));
    }

    public static void openKTouchScreen() {
        Minecraft.getInstance().setScreen(new KTouchScreen());
    }
}
