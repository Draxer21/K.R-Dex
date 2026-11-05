package com.draxer.dexrider.client;

import com.draxer.dexrider.DexRider;
import com.draxer.dexrider.item.DexCard;
import com.draxer.dexrider.item.DexDriverItem;
import com.draxer.dexrider.item.DriverDeck;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Archivo del Driver (tecla K, o el boton del Ride Booker): todas las cartas del juego como un album.
 * Las que tienes, a color y con su nivel; las que no, en silueta, con las muestras que llevas y una pista
 * de donde escanearlas o conseguirlas.
 */
public class DriverArchiveScreen extends Screen {

    private static final int W = 400, H = 232, COLS = 7, CELL = 32;

    private DexCard.Family family = DexCard.Family.KAMEN_RIDE;
    private DexCard selected;
    private DriverDeck deck = DriverDeck.EMPTY;
    private boolean hasDriver;
    private int left, top;
    private final List<Button> cells = new ArrayList<>();
    private final List<DexCard> cellCards = new ArrayList<>();

    public DriverArchiveScreen() {
        this(null);
    }

    /** Abre directamente en la familia de una carta (y la deja elegida). */
    public DriverArchiveScreen(DexCard focus) {
        super(Component.translatable("screen.dexrider.archive"));
        if (focus != null) { family = focus.family(); selected = focus; }
    }

    private void readDeck() {
        var p = Minecraft.getInstance().player;
        ItemStack driver = p == null ? ItemStack.EMPTY : DexDriverItem.findDriver(p);
        hasDriver = !driver.isEmpty();
        deck = hasDriver ? DexDriverItem.getDeck(driver) : DriverDeck.EMPTY;
    }

    private static List<DexCard> cardsOf(DexCard.Family f) {
        List<DexCard> out = new ArrayList<>();
        for (DexCard c : DexCard.values()) if (c.family() == f) out.add(c);
        return out;
    }

    private int owned(DexCard.Family f) {
        int n = 0;
        for (DexCard c : cardsOf(f)) if (deck.has(c)) n++;
        return n;
    }

    @Override
    protected void init() {
        readDeck();
        left = (width - W) / 2;
        top = (height - H) / 2;
        clearWidgets();
        cells.clear(); cellCards.clear();
        DexCard.Family[] fams = DexCard.Family.values();
        for (int i = 0; i < fams.length; i++) {
            final DexCard.Family f = fams[i];
            String key = f.name().toLowerCase(Locale.ROOT);
            Button b = addRenderableWidget(Button.builder(
                    Component.translatable("screen.dexrider.archive.tab." + key),
                    x -> { family = f; selected = null; init(); }).bounds(left + 4 + i * 66, top + 20, 64, 16)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("family.dexrider." + key)
                            .append(": " + owned(f) + "/" + cardsOf(f).size()))).build());
            b.active = f != family;
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.dexrider.archive.missions"),
                x -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(new MissionsScreen()))
                .bounds(left + 150, top + 4, 70, 14).build());
        List<DexCard> list = cardsOf(family);
        if (selected == null || selected.family() != family) selected = list.get(0);
        for (int i = 0; i < list.size(); i++) {
            final DexCard c = list.get(i);
            Button b = addRenderableWidget(Button.builder(Component.empty(), x -> { selected = c; init(); })
                    .bounds(left + 6 + (i % COLS) * (CELL + 2), top + 42 + (i / COLS) * (CELL + 12), CELL, CELL).build());
            b.active = c != selected;
            cells.add(b); cellCards.add(c);
        }
    }

    private static Identifier texture(DexCard c) {
        return DexRider.id("textures/item/" + c.itemName() + ".png");
    }

    private static boolean levels(DexCard c) {
        return c.samplesToScan() > 0 && c.family() != DexCard.Family.KAMEN_RIDE && c.family() != DexCard.Family.FINAL_ATTACK_RIDE;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(left - 2, top - 2, left + W + 2, top + H + 2, 0xFF0E0E14);
        g.fill(left, top, left + W, top + H, 0xFF1C1C26);
        g.fill(left, top, left + W, top + 2, 0xFFFF2BD6);
        g.text(font, title, left + 8, top + 7, 0xFFF2C230, true);
        int total = DexCard.values().length, have = 0;
        for (DexCard c : DexCard.values()) if (deck.has(c)) have++;
        Component count = Component.translatable("screen.dexrider.archive.count", have, total);
        g.text(font, count, left + W - 8 - font.width(count), top + 7, 0xFFE8E8EE, true);
        g.fill(left + 248, top + 40, left + W - 4, top + H - 4, 0xFF15151C);

        super.extractRenderState(g, mouseX, mouseY, partialTick);

        // cartas de la familia
        for (int i = 0; i < cells.size(); i++) {
            Button b = cells.get(i);
            DexCard c = cellCards.get(i);
            boolean own = deck.has(c);
            int x = b.getX() + 3, y = b.getY() + 3, s = CELL - 6;
            g.blit(RenderPipelines.GUI_TEXTURED, texture(c), x, y, 0, 0, s, s, s, s, own ? 0xFFFFFFFF : 0xFF14141C);
            int by = b.getY() + CELL + 2;
            if (own) {
                if (levels(c)) for (int l = 0; l < DriverDeck.MAX_LEVEL; l++)
                    g.fill(b.getX() + 4 + l * 9, by, b.getX() + 11 + l * 9, by + 4, l < deck.level(c) ? 0xFFF2C230 : 0xFF3A3A48);
                else g.fill(b.getX() + 4, by, b.getX() + CELL - 4, by + 4, 0xFF3FD27A);
            } else if (deck.samples(c) > 0 && c.samplesToScan() > 0) {
                bar(g, b.getX() + 2, by, CELL - 4, deck.samples(c) / (float) c.samplesToScan(), 0xFFFF2BD6);
            }
        }

        // detalle de la carta elegida
        if (selected == null) return;
        DexCard c = selected;
        boolean own = deck.has(c);
        int px = left + 254, pw = W - 4 - 254 - 6, y = top + 46;
        g.blit(RenderPipelines.GUI_TEXTURED, texture(c), px + pw / 2 - 28, y, 0, 0, 56, 56, 56, 56, own ? 0xFFFFFFFF : 0xFF14141C);
        y += 60;
        for (FormattedCharSequence line : font.split(Component.translatable(c.translationKey()), pw)) {
            g.text(font, line, px, y, own ? 0xFFFFFFFF : 0xFF9A9AAA, true); y += 10;
        }
        g.text(font, Component.translatable("family.dexrider." + c.family().name().toLowerCase(Locale.ROOT)), px, y, 0xFFB8BCC8, false);
        y += 12;
        if (!hasDriver) {
            g.text(font, Component.translatable("screen.dexrider.archive.no_driver"), px, y, 0xFFFF6060, false); y += 12;
        } else if (own) {
            if (levels(c)) {
                int lv = deck.level(c);
                g.text(font, Component.translatable("screen.dexrider.archive.level", lv, DriverDeck.MAX_LEVEL), px, y, 0xFFF2C230, false); y += 11;
                if (lv < DriverDeck.MAX_LEVEL) {
                    int need = deck.samplesForNextLevel(c), have2 = deck.samples(c);
                    g.text(font, Component.translatable("screen.dexrider.archive.next", have2, need), px, y, 0xFFE8E8EE, false); y += 10;
                    bar(g, px, y, pw, have2 / (float) need, 0xFFF2C230); y += 7;
                } else {
                    g.text(font, Component.translatable("screen.dexrider.archive.max"), px, y, 0xFF3FD27A, false); y += 11;
                }
            } else {
                g.text(font, Component.translatable("screen.dexrider.archive.owned"), px, y, 0xFF3FD27A, false); y += 11;
            }
        } else if (c.samplesToScan() > 0) {
            int s = deck.samples(c);
            g.text(font, Component.translatable("screen.dexrider.archive.progress", s, c.samplesToScan()), px, y, 0xFFFF8AE6, false); y += 10;
            bar(g, px, y, pw, s / (float) c.samplesToScan(), 0xFFFF2BD6); y += 7;
        } else {
            g.text(font, Component.translatable("screen.dexrider.archive.unknown"), px, y, 0xFF9A9AAA, false); y += 11;
        }
        y += 3;
        g.text(font, Component.translatable("screen.dexrider.archive.how"), px, y, 0xFFB8BCC8, false);
        y += 10;
        for (FormattedCharSequence line : font.split(Component.translatable("hint.dexrider." + c.id()), pw)) {
            if (y > top + H - 12) break;
            g.text(font, line, px, y, 0xFFE8E8EE, false); y += 9;
        }
    }

    private static void bar(GuiGraphicsExtractor g, int x, int y, int w, float f, int color) {
        g.fill(x, y, x + w, y + 4, 0xFF2A2A36);
        g.fill(x, y, x + Math.round(w * Math.max(0, Math.min(1, f))), y + 4, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
