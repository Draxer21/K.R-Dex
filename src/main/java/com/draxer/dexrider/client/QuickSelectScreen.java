package com.draxer.dexrider.client;

import com.draxer.dexrider.DexRider;
import com.draxer.dexrider.item.DexDriverItem;
import com.draxer.dexrider.menu.Booker;
import com.draxer.dexrider.menu.RideBookerMenu;
import com.draxer.dexrider.network.QuickBookerPayload;
import com.draxer.dexrider.registry.DexComponents;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Selector rapido: mientras mantienes la rueda del raton (configurable), tus cartas favoritas en circulo.
 * Mueves el raton hacia una y al soltar se usa. Las favoritas se eligen en el libro del Ride Booker (boton estrella).
 * Si no se puede leer el boton soltado, tambien funciona con clic.
 */
public class QuickSelectScreen extends Screen {

    private static final int R = 74, S = 40;
    private final KeyMapping key;
    private final List<Integer> slots = new ArrayList<>();      // ranura del Booker de cada favorita (-1 = ya no esta)
    private final List<ItemStack> cards = new ArrayList<>();
    private int hovered = -1;
    private boolean released;
    private final boolean demo;

    public QuickSelectScreen(KeyMapping key) {
        this(key, false);
    }

    /** demo = no se cierra al soltar (para las fotos automaticas). */
    public QuickSelectScreen(KeyMapping key, boolean demo) {
        super(Component.translatable("screen.dexrider.quick_select"));
        this.key = key;
        this.demo = demo;
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        ItemStack driver = DexDriverItem.findDriver(p);
        if (driver.isEmpty()) return;
        NonNullList<ItemStack> items = Booker.view(driver);
        for (String id : driver.getOrDefault(DexComponents.QUICK_PICKS.get(), List.<String>of())) {
            int found = -1;
            for (int i = 0; i < items.size(); i++) {
                if (!items.get(i).isEmpty() && BuiltInRegistries.ITEM.getKey(items.get(i).getItem()).toString().equals(id)) { found = i; break; }
            }
            slots.add(found);
            cards.add(found >= 0 ? items.get(found) : ItemStack.EMPTY);
        }
        // sin favoritas: las primeras 8 cartas del mazo elegido
        if (slots.isEmpty()) {
            Integer sel = driver.get(DexComponents.BOOKER_SEL.get());
            deckFallback = sel == null ? 0 : Math.floorMod(sel, RideBookerMenu.SIZE) / RideBookerMenu.PER_DECK;
            for (int i = 0; i < RideBookerMenu.PER_DECK && slots.size() < RideBookerMenu.MAX_PICKS; i++) {
                int s = deckFallback * RideBookerMenu.PER_DECK + i;
                if (!items.get(s).isEmpty()) { slots.add(s); cards.add(items.get(s)); }
            }
        }
    }

    /** Mazo que se muestra cuando no hay favoritas (-1 = se muestran las favoritas). */
    private int deckFallback = -1;

    private int[] pos(int i) {
        double a = -Math.PI / 2 + i * 2 * Math.PI / Math.max(1, slots.size());
        return new int[]{width / 2 + (int) Math.round(Math.cos(a) * R) - S / 2, height / 2 + (int) Math.round(Math.sin(a) * R) - S / 2};
    }

    @Override
    protected void init() {
        clearWidgets();
        for (int i = 0; i < slots.size(); i++) {
            final int k = i;
            int[] p = pos(i);
            addRenderableWidget(Button.builder(Component.empty(), b -> choose(k)).bounds(p[0], p[1], S, S).build()).active = slots.get(i) >= 0;
        }
    }

    private void choose(int i) {
        if (i >= 0 && i < slots.size() && slots.get(i) >= 0)
            ClientPacketDistributor.sendToServer(new QuickBookerPayload(RideBookerMenu.QUICK_SLOT + slots.get(i)));
        onClose();
    }

    /** Si la tecla del selector sigue apretada (se lee directo de GLFW: con una pantalla abierta el juego no la actualiza). */
    private boolean keyHeld() {
        long h = windowHandle();
        if (h == 0) return true;
        InputConstants.Key k = key.getKey();
        if (k.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(h, k.getValue()) == GLFW.GLFW_PRESS;
        if (k.getType() == InputConstants.Type.KEYSYM) return GLFW.glfwGetKey(h, k.getValue()) == GLFW.GLFW_PRESS;
        return true;
    }

    private static long windowHandle() {
        Object w = Minecraft.getInstance().getWindow();
        for (String n : new String[]{"handle", "getWindow"}) {
            try {
                Object o = w.getClass().getMethod(n).invoke(w);
                if (o instanceof Long l) return l;
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return 0;
    }

    @Override
    public void tick() {
        if (!demo && !released && !keyHeld()) {
            released = true;
            if (hovered >= 0) choose(hovered); else onClose();
        }
    }

    private static Identifier texture(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return DexRider.MODID.equals(id.getNamespace()) ? DexRider.id("textures/item/" + id.getPath() + ".png") : null;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int cx = width / 2, cy = height / 2;
        // sector bajo el raton
        double dx = mouseX - cx, dy = mouseY - cy;
        hovered = -1;
        if (!slots.isEmpty() && dx * dx + dy * dy > 18 * 18) {
            double step = 2 * Math.PI / slots.size(), full = 2 * Math.PI;
            double a = Math.atan2(dy, dx) + Math.PI / 2 + step / 2;
            int i = (int) ((((a % full) + full) % full) / step);
            if (i >= 0 && i < slots.size() && slots.get(i) >= 0) hovered = i;
        }
        g.fill(cx - R - 34, cy - R - 34, cx + R + 34, cy + R + 34, 0x80101018);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        for (int i = 0; i < slots.size(); i++) {
            int[] p = pos(i);
            boolean on = i == hovered;
            g.fill(p[0] - 2, p[1] - 2, p[0] + S + 2, p[1] + S + 2, on ? 0xFF28D250 : 0xFF2A2633);
            g.fill(p[0], p[1], p[0] + S, p[1] + S, 0xFF141218);
            Identifier tex = cards.get(i).isEmpty() ? null : texture(cards.get(i));
            if (tex != null) {
                int grow = on ? 6 : 0;
                g.blit(RenderPipelines.GUI_TEXTURED, tex, p[0] - grow / 2, p[1] - grow / 2, 0, 0, S + grow, S + grow, S + grow, S + grow,
                        slots.get(i) >= 0 ? 0xFFFFFFFF : 0xFF303038);
            }
        }
        // linea del centro al raton y nombre en el centro
        g.fill(cx - 3, cy - 3, cx + 3, cy + 3, 0xFF28D250);
        Component center = slots.isEmpty() ? Component.translatable("screen.dexrider.quick_select.empty")
                : hovered >= 0 ? cards.get(hovered).getHoverName() : Component.translatable("screen.dexrider.quick_select.hint");
        g.text(font, center, cx - font.width(center) / 2, cy + 10, 0xFFF2C230, true);
        if (deckFallback >= 0) {
            Component d = Component.translatable("screen.dexrider.quick_select.deck",
                    Component.translatable("booker.dexrider.deck." + RideBookerMenu.DECK_KEYS[deckFallback]));
            g.text(font, d, cx - font.width(d) / 2, cy + R + 30, 0xFF9A93A6, true);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
