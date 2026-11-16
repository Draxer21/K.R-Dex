package com.draxer.dexrider.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/** Llamadas que solo existen en el cliente (se usan desde los items solo cuando el mundo es del cliente). */
public final class ClientHooks {
    private ClientHooks() {}

    public static void openSuitScreen(ItemStack driver) {
        Minecraft.getInstance().setScreen(new SuitScreen(com.draxer.dexrider.item.SuitSlots.of(driver)));
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

    /** Diario de Yusuke: portada y las entradas del Driver, juntando dos o tres cortas por pagina. */
    public static void openDiary() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        java.util.List<net.minecraft.network.chat.Component> pages = new java.util.ArrayList<>();
        var driver = com.draxer.dexrider.item.DexDriverItem.findDriver(mc.player);
        var entries = com.draxer.dexrider.rider.Diary.entries(driver);
        pages.add(net.minecraft.network.chat.Component.translatable(driver.isEmpty() ? "diary.dexrider.no_driver"
                : entries.isEmpty() ? "diary.dexrider.empty" : "diary.dexrider.cover", entries.size()));
        net.minecraft.network.chat.MutableComponent page = null;
        int used = 0, lastDay = -1;
        for (String e : entries) {
            net.minecraft.network.chat.Component text = diaryLine(e);
            int day = diaryDay(e);
            String plain = text.getString();
            int cost = plain.length() + 24 + plain.length() / 19 * 2;   // titulo del dia y saltos de linea
            if (page != null && used + cost > 250) { pages.add(page); page = null; used = 0; lastDay = -1; }
            if (page == null) page = net.minecraft.network.chat.Component.empty();
            else page.append("\n\n");
            if (day != lastDay) {
                page.append(net.minecraft.network.chat.Component.translatable("diary.dexrider.day", day)
                        .withStyle(net.minecraft.ChatFormatting.DARK_RED, net.minecraft.ChatFormatting.BOLD)).append("\n");
                lastDay = day;
            }
            page.append(text);
            used += cost;
        }
        if (page != null) pages.add(page);
        mc.setScreen(new net.minecraft.client.gui.screens.inventory.BookViewScreen(
                new net.minecraft.client.gui.screens.inventory.BookViewScreen.BookAccess(pages)));
    }

    static int diaryDay(String entry) {
        try { return Integer.parseInt(entry.substring(0, entry.indexOf('|'))); } catch (RuntimeException ex) { return 1; }
    }

    static net.minecraft.network.chat.Component diaryLine(String entry) {
        String[] parts = entry.split("\\|", -1);
        if (parts.length < 2) return net.minecraft.network.chat.Component.literal(entry);
        Object[] args = new Object[parts.length - 2];
        for (int i = 2; i < parts.length; i++) {
            args[i - 2] = parts[i].startsWith("#") ? net.minecraft.network.chat.Component.translatable(parts[i].substring(1)) : parts[i];
        }
        return net.minecraft.network.chat.Component.translatable(parts[1], args);
    }
}
