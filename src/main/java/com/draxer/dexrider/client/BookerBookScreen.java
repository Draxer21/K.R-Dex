package com.draxer.dexrider.client;

import com.draxer.dexrider.DexRider;
import com.draxer.dexrider.item.DexDriverItem;
import com.draxer.dexrider.menu.Booker;
import com.draxer.dexrider.menu.RideBookerMenu;
import com.draxer.dexrider.network.OpenBookerPayload;
import com.draxer.dexrider.network.QuickBookerPayload;
import com.draxer.dexrider.registry.DexComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * El Ride Booker abierto como un libro (tecla G): un estuche negro con costuras doradas, dos paginas de bolsillos
 * con las cartas a la vista y una cinta por mazo. Clic en una carta = la sacas y se usa. Las paginas se pasan con las
 * flechas o la rueda del raton. "Ordenar" abre el Booker de siempre para meter, sacar y mover cartas.
 *
 * Diseno propio de Dex (negro, dorado, rojo y el verde de su lente). 5 mazos x 3 paginas de 9 = 135 bolsillos.
 */
public class BookerBookScreen extends Screen {

    private static final int W = 372, H = 230;
    private static final int PER_PAGE = 9, PAGES_PER_DECK = RideBookerMenu.PER_DECK / PER_PAGE;
    private static final int PAGES = RideBookerMenu.DECKS * PAGES_PER_DECK;
    private static final int[] DECK_COLOR = {0xFF3FD27A, 0xFFF2C230, 0xFFD4202C, 0xFF4FA8FF, 0xFFFF2BD6};
    private static final int CASE = 0xFF141218, CASE_L = 0xFF221E28, PAGE = 0xFF1B1A20, GOLD = 0xFFE0B23A, GOLD_D = 0xFF8A6A1C, RED = 0xFFB4141E;
    private static final int PW = 50, PH = 50;            // bolsillo

    private NonNullList<ItemStack> items = NonNullList.withSize(RideBookerMenu.SIZE, ItemStack.EMPTY);
    private int sel = -1;
    private int spread;                                    // pagina izquierda = spread * 2
    private int left, top;
    private float flip;                                    // animacion al pasar pagina (1 -> 0)
    private boolean pickMode;                              // marcar cartas para el selector rapido (rueda del raton)
    private final List<Button> pockets = new ArrayList<>();
    private final List<Integer> pocketSlots = new ArrayList<>();

    public BookerBookScreen() {
        super(Component.translatable("screen.dexrider.ride_booker"));
        var p = Minecraft.getInstance().player;
        if (p != null) {
            ItemStack driver = DexDriverItem.findDriver(p);
            items = Booker.view(driver);
            Integer s = driver.isEmpty() ? null : driver.get(DexComponents.BOOKER_SEL.get());
            sel = s == null ? -1 : Math.floorMod(s, RideBookerMenu.SIZE);
            if (sel >= 0) spread = (sel / PER_PAGE) / 2;
        }
    }

    private int spreads() { return (PAGES + 1) / 2; }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        clearWidgets();
        pockets.clear(); pocketSlots.clear();
        for (int side = 0; side < 2; side++) {
            int page = spread * 2 + side;
            if (page >= PAGES) continue;
            for (int i = 0; i < PER_PAGE; i++) {
                int slot = page * PER_PAGE + i;
                int[] xy = pocketPos(side, i);
                final int s = slot;
                ItemStack st = items.get(slot);
                Button b = Button.builder(Component.empty(), x -> { if (pickMode) togglePick(s); else use(s); }).bounds(xy[0], xy[1], PW, PH).build();
                if (!st.isEmpty()) b.setTooltip(Tooltip.create(st.getHoverName()));
                b.active = !st.isEmpty();
                addRenderableWidget(b);
                pockets.add(b); pocketSlots.add(slot);
            }
        }
        // cintas de los mazos
        for (int d = 0; d < RideBookerMenu.DECKS; d++) {
            final int deck = d;
            Button b = addRenderableWidget(Button.builder(Component.empty(), x -> turnTo((deck * PAGES_PER_DECK) / 2))
                    .bounds(left + W - 2, top + 26 + d * 34, 14, 30)
                    .tooltip(Tooltip.create(Component.translatable("booker.dexrider.deck." + RideBookerMenu.DECK_KEYS[d]))).build());
            b.active = true;
        }
        addRenderableWidget(Button.builder(Component.literal("<"), x -> turnTo(spread - 1)).bounds(left + 12, top + H - 22, 20, 16).build()).active = spread > 0;
        addRenderableWidget(Button.builder(Component.literal(">"), x -> turnTo(spread + 1)).bounds(left + W - 32, top + H - 22, 20, 16).build()).active = spread < spreads() - 1;
        addRenderableWidget(Button.builder(Component.translatable("booker.dexrider.organize"), x -> {
            onClose();
            ClientPacketDistributor.sendToServer(OpenBookerPayload.INSTANCE);
        }).bounds(left + W / 2 - 84, top + H - 22, 80, 16)
                .tooltip(Tooltip.create(Component.translatable("booker.dexrider.organize.tip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("booker.dexrider.archive"), x -> Minecraft.getInstance().setScreen(new DriverArchiveScreen()))
                .bounds(left + W / 2 + 4, top + H - 22, 80, 16).build());
        addRenderableWidget(Button.builder(Component.literal((pickMode ? "§a" : "") + "★ ").append(Component.translatable("booker.dexrider.quick_pick")),
                x -> { pickMode = !pickMode; init(); }).bounds(left + 36, top + H - 22, 64, 16)
                .tooltip(Tooltip.create(Component.translatable("booker.dexrider.quick_pick.tip"))).build());
    }

    /** Esquina del bolsillo i (0..8) de la pagina izquierda (side 0) o derecha (1). */
    private int[] pocketPos(int side, int i) {
        int px = left + (side == 0 ? 14 : W / 2 + 8);
        return new int[]{px + (i % 3) * (PW + 4), top + 34 + (i / 3) * (PH + 3)};
    }

    private void turnTo(int s) {
        int n = Math.max(0, Math.min(spreads() - 1, s));
        if (n == spread) return;
        spread = n;
        flip = 1F;
        var p = Minecraft.getInstance().player;
        if (p != null) p.playSound(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 0.8F, 1.1F);
        init();
    }

    private void togglePick(int slot) {
        if (items.get(slot).isEmpty()) return;
        ClientPacketDistributor.sendToServer(new QuickBookerPayload(RideBookerMenu.QUICK_FAV + slot));
    }

    /** Ids de las cartas marcadas para el selector (se leen del Driver cada vez: el servidor las actualiza). */
    private static List<String> picks() {
        var p = Minecraft.getInstance().player;
        ItemStack driver = p == null ? ItemStack.EMPTY : DexDriverItem.findDriver(p);
        return driver.isEmpty() ? List.of() : driver.getOrDefault(DexComponents.QUICK_PICKS.get(), List.of());
    }

    private void use(int slot) {
        if (items.get(slot).isEmpty()) return;
        ClientPacketDistributor.sendToServer(new QuickBookerPayload(RideBookerMenu.QUICK_SLOT + slot));
        onClose();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (scrollY != 0) { turnTo(spread + (scrollY > 0 ? -1 : 1)); return true; }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    private static Identifier texture(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!DexRider.MODID.equals(id.getNamespace())) return null;
        return DexRider.id("textures/item/" + id.getPath() + ".png");
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        flip = Math.max(0F, flip - 0.12F);
        int x0 = left, y0 = top, x1 = left + W, y1 = top + H, cx = left + W / 2;
        // estuche: cuero negro, costura dorada, lomo con cinta roja
        g.fill(x0 - 3, y0 - 3, x1 + 3, y1 + 3, 0xFF070609);
        g.fill(x0, y0, x1, y1, CASE);
        g.fill(x0 + 2, y0 + 2, x1 - 2, y0 + 4, CASE_L);
        for (int x = x0 + 6; x < x1 - 6; x += 6) { g.fill(x, y0 + 5, x + 3, y0 + 6, GOLD_D); g.fill(x, y1 - 6, x + 3, y1 - 5, GOLD_D); }
        for (int y = y0 + 8; y < y1 - 8; y += 6) { g.fill(x0 + 5, y, x0 + 6, y + 3, GOLD_D); g.fill(x1 - 6, y, x1 - 5, y + 3, GOLD_D); }
        // paginas
        g.fill(x0 + 9, y0 + 18, cx - 3, y1 - 26, PAGE);
        g.fill(cx + 3, y0 + 18, x1 - 9, y1 - 26, PAGE);
        g.fill(cx - 3, y0 + 14, cx + 3, y1 - 22, 0xFF0B0A0E);         // lomo
        g.fill(cx - 1, y0 + 14, cx + 1, y1 - 22, RED);
        // titulo: el visor verde de Dex como broche
        g.fill(cx - 12, y0 + 6, cx + 12, y0 + 12, 0xFF0B0A0E);
        g.fill(cx - 10, y0 + 7, cx + 10, y0 + 11, 0xFF28D250);
        g.text(font, title, x0 + 12, y0 + 7, GOLD, true);

        super.extractRenderState(g, mouseX, mouseY, partialTick);

        // cintas de los mazos (encima de sus botones)
        int curDeck = (spread * 2) / PAGES_PER_DECK;
        for (int d = 0; d < RideBookerMenu.DECKS; d++) {
            int ty = top + 26 + d * 34, tx = left + W - 2;
            boolean on = d == curDeck || d == (spread * 2 + 1) / PAGES_PER_DECK;
            g.fill(tx, ty, tx + (on ? 14 : 10), ty + 30, DECK_COLOR[d]);
            g.fill(tx, ty + 26, tx + (on ? 14 : 10), ty + 30, 0x60000000);
        }
        // paginas: cabecera y bolsillos
        int alpha = (int) (255 * (1F - flip));
        for (int side = 0; side < 2; side++) {
            int page = spread * 2 + side;
            if (page >= PAGES) continue;
            int deck = page / PAGES_PER_DECK, num = page % PAGES_PER_DECK + 1;
            int hx = left + (side == 0 ? 14 : W / 2 + 8);
            g.fill(hx, top + 22, hx + 4, top + 30, DECK_COLOR[deck]);
            g.text(font, Component.translatable("booker.dexrider.deck." + RideBookerMenu.DECK_KEYS[deck])
                    .append(" · " + num + "/" + PAGES_PER_DECK), hx + 7, top + 22, 0xFFE8E2D6, false);
        }
        List<String> favs = picks();
        for (int i = 0; i < pockets.size(); i++) {
            Button b = pockets.get(i);
            int slot = pocketSlots.get(i);
            ItemStack st = items.get(slot);
            int bx = b.getX(), by = b.getY();
            g.fill(bx, by, bx + PW, by + PH, 0xFF0E0D12);                     // fondo del bolsillo
            boolean hover = b.isHovered() && !st.isEmpty();
            Identifier tex = st.isEmpty() ? null : texture(st);
            if (tex != null) {
                int lift = hover ? 6 : 0;
                g.blit(RenderPipelines.GUI_TEXTURED, tex, bx + 1, by + 2 - lift, 0, 0, PW - 2, PW - 2, PW - 2, PW - 2,
                        (alpha << 24) | 0xFFFFFF);
            }
            // funda: plastico translucido sobre la mitad de abajo, con borde dorado
            int sy = by + PH * 2 / 3;
            g.fill(bx + 1, sy, bx + PW - 1, by + PH - 1, 0x58201C26);
            g.fill(bx + 1, sy, bx + PW - 1, sy + 1, slot == sel ? 0xFF28D250 : 0xC0E0B23A);
            g.fill(bx + 1, sy + 1, bx + PW - 1, sy + 2, 0x40FFFFFF);
            g.fill(bx, by, bx + 1, by + PH, GOLD_D); g.fill(bx + PW - 1, by, bx + PW, by + PH, GOLD_D);
            g.fill(bx, by + PH - 1, bx + PW, by + PH, GOLD_D);
            if (slot == sel) g.fill(bx + PW - 6, by + 2, bx + PW - 2, by + 6, 0xFF28D250);
            if (!st.isEmpty() && favs.contains(BuiltInRegistries.ITEM.getKey(st.getItem()).toString()))
                g.text(font, Component.literal("★"), bx + 3, by + 3, 0xFFFFD84A, true);
            if (pickMode && !st.isEmpty() && hover) g.fill(bx, by, bx + PW, by + 1, 0xFFFFD84A);
        }
        Component hl = pickMode ? Component.translatable("booker.dexrider.pick_hint", favs.size(), RideBookerMenu.MAX_PICKS)
                : Component.translatable("booker.dexrider.book_hint_left"), hr = Component.translatable("booker.dexrider.book_hint_right");
        g.text(font, hl, (x0 + 9 + cx - 3) / 2 - font.width(hl) / 2, y1 - 36, 0xFF9A93A6, false);
        g.text(font, hr, (cx + 3 + x1 - 9) / 2 - font.width(hr) / 2, y1 - 36, 0xFF9A93A6, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
