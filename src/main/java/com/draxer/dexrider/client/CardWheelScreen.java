package com.draxer.dexrider.client;

import com.draxer.dexrider.DexRider;
import com.draxer.dexrider.item.DexDriverItem;
import com.draxer.dexrider.menu.Booker;
import com.draxer.dexrider.menu.RideBookerMenu;
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
 * Rueda de cartas (tecla C): hasta 12 cartas del mazo elegido en circulo. Clic en una = se usa al instante.
 * Las flechas (o la rueda del raton) cambian de mazo. No pausa el juego.
 */
public class CardWheelScreen extends Screen {
    private static final String[] DECKS = RideBookerMenu.DECK_KEYS;
    private static final int MAX = 12, R = 78, SIZE = 30;

    private int deck;
    private final List<int[]> shown = new ArrayList<>();   // {slot, x, y}
    private NonNullList<ItemStack> items = NonNullList.withSize(RideBookerMenu.SIZE, ItemStack.EMPTY);

    public CardWheelScreen() {
        super(Component.translatable("screen.dexrider.card_wheel"));
        var p = Minecraft.getInstance().player;
        if (p != null) {
            ItemStack driver = DexDriverItem.findDriver(p);
            items = Booker.view(driver);
            Integer sel = driver.isEmpty() ? null : driver.get(DexComponents.BOOKER_SEL.get());
            deck = sel == null ? 0 : Math.floorMod(sel, RideBookerMenu.SIZE) / RideBookerMenu.PER_DECK;
        }
    }

    @Override
    protected void init() {
        clearWidgets();
        shown.clear();
        int cx = width / 2, cy = height / 2;
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < RideBookerMenu.PER_DECK && slots.size() < MAX; i++) {
            int s = deck * RideBookerMenu.PER_DECK + i;
            if (!items.get(s).isEmpty()) slots.add(s);
        }
        for (int k = 0; k < slots.size(); k++) {
            double a = -Math.PI / 2 + k * 2 * Math.PI / Math.max(1, slots.size());
            int x = cx + (int) Math.round(Math.cos(a) * R) - SIZE / 2;
            int y = cy + (int) Math.round(Math.sin(a) * R) - SIZE / 2;
            final int slot = slots.get(k);
            addRenderableWidget(Button.builder(Component.empty(), b -> use(slot)).bounds(x, y, SIZE, SIZE)
                    .tooltip(Tooltip.create(items.get(slot).getHoverName())).build());
            shown.add(new int[]{slot, x, y});
        }
        addRenderableWidget(Button.builder(Component.literal("<"), b -> deck(-1)).bounds(cx - 40, cy + 6, 16, 16).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> deck(1)).bounds(cx + 24, cy + 6, 16, 16).build());
    }

    private void deck(int step) {
        deck = Math.floorMod(deck + step, RideBookerMenu.DECKS);
        init();
    }

    private void use(int slot) {
        ClientPacketDistributor.sendToServer(new QuickBookerPayload(RideBookerMenu.QUICK_SLOT + slot));
        onClose();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (scrollY != 0) { deck(scrollY > 0 ? -1 : 1); return true; }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int cx = width / 2, cy = height / 2;
        g.fill(cx - R - 26, cy - R - 26, cx + R + 26, cy + R + 26, 0x90101018);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        for (int[] s : shown) {
            Identifier tex = texture(items.get(s[0]));
            if (tex != null) g.blit(RenderPipelines.GUI_TEXTURED, tex, s[1] + 1, s[2] + 1, 0, 0, SIZE - 2, SIZE - 2, SIZE - 2, SIZE - 2);
        }
        Component name = Component.translatable("booker.dexrider.deck." + DECKS[deck]);
        g.text(font, name, cx - font.width(name) / 2, cy - 8, 0xFFF2C230, true);
        Component hint = shown.isEmpty() ? Component.translatable("screen.dexrider.card_wheel.empty")
                : Component.translatable("screen.dexrider.card_wheel.hint");
        g.text(font, hint, cx - font.width(hint) / 2, cy + R + 30, 0xFFE8E8EE, true);
    }

    /** dexrider:card_x -> dexrider:textures/item/card_x.png (las cartas son texturas planas). */
    private static Identifier texture(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!DexRider.MODID.equals(id.getNamespace())) return null;
        return DexRider.id("textures/item/" + id.getPath() + ".png");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
