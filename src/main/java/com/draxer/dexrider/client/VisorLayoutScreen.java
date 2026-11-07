package com.draxer.dexrider.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Editar posiciones del HUD del Dex: cada pieza se ve como un recuadro con su nombre; se arrastra con el raton
 * (el ancla se elige sola por la zona de la pantalla) o se mueve fino con las flechas. Tamano propio y "se ve / no se ve".
 * El panel de controles se puede pasar arriba o abajo para que no tape lo que estas acomodando.
 */
public class VisorLayoutScreen extends Screen {

    private static final List<String> IDS = new ArrayList<>(HudLayout.DEFAULTS.keySet());
    private static final int PW = 236, PH = 92;
    private String selected = "status";
    private boolean panelTop;
    private int px, py;
    private boolean wasDown;
    private String dragging;
    private int grabX, grabY;
    private final Screen parent;

    public VisorLayoutScreen(Screen parent) {
        super(Component.translatable("screen.dexrider.visor_layout"));
        this.parent = parent;
    }

    /** Las piezas del visor se dibujan escaladas con el tamano general del visor; el aviso de escaneo, no. */
    private static float scaleOf(String id) {
        return "toast".equals(id) ? 1F : VisorSettings.get().scale;
    }

    private static Component name(String id) {
        return Component.translatable("screen.dexrider.visor_layout.elem." + id);
    }

    @Override
    protected void init() {
        clearWidgets();
        px = (width - PW) / 2;
        py = panelTop ? 6 : height - PH - 6;
        int x = px + 6, y = py + 18;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> pick(-1)).bounds(x, y, 16, 16).build());
        addRenderableWidget(Button.builder(name(selected), b -> pick(1)).bounds(x + 18, y, 112, 16).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> pick(1)).bounds(x + 132, y, 16, 16).build());
        HudLayout.Elem e = HudLayout.get(selected);
        addRenderableWidget(Button.builder(Component.translatable(e.visible ? "options.on" : "options.off"), b -> {
            e.visible = !e.visible; changed();
        }).bounds(x + 152, y, 72, 16).build());
        y += 20;
        // flechas: 1 px (con la de al lado, 10 px)
        addRenderableWidget(Button.builder(Component.literal("←"), b -> nudge(-1, 0)).bounds(x, y, 18, 16).build());
        addRenderableWidget(Button.builder(Component.literal("→"), b -> nudge(1, 0)).bounds(x + 20, y, 18, 16).build());
        addRenderableWidget(Button.builder(Component.literal("↑"), b -> nudge(0, -1)).bounds(x + 40, y, 18, 16).build());
        addRenderableWidget(Button.builder(Component.literal("↓"), b -> nudge(0, 1)).bounds(x + 60, y, 18, 16).build());
        addRenderableWidget(Button.builder(Component.literal("«"), b -> nudge(-10, 0)).bounds(x + 82, y, 18, 16).build());
        addRenderableWidget(Button.builder(Component.literal("»"), b -> nudge(10, 0)).bounds(x + 102, y, 18, 16).build());
        addRenderableWidget(Button.builder(Component.literal("⇑"), b -> nudge(0, -10)).bounds(x + 122, y, 18, 16).build());
        addRenderableWidget(Button.builder(Component.literal("⇓"), b -> nudge(0, 10)).bounds(x + 142, y, 18, 16).build());
        addRenderableWidget(new ScaleSlider(x + 164, y, 60, e));
        y += 20;
        addRenderableWidget(Button.builder(Component.translatable("screen.dexrider.visor_layout.reset_one"), b -> { HudLayout.reset(selected); changed(); })
                .bounds(x, y, 56, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.dexrider.visor_layout.reset_all"), b -> { HudLayout.resetAll(); changed(); })
                .bounds(x + 58, y, 56, 16).build());
        addRenderableWidget(Button.builder(Component.literal(panelTop ? "▼" : "▲"), b -> { panelTop = !panelTop; init(); })
                .bounds(x + 116, y, 20, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x + 138, y, 86, 16).build());
    }

    private void pick(int step) {
        int i = IDS.indexOf(selected);
        selected = IDS.get(Math.floorMod(i + step, IDS.size()));
        init();
    }

    private void nudge(int dx, int dy) {
        HudLayout.Elem e = HudLayout.get(selected);
        e.dx += dx; e.dy += dy;
        VisorSettings.save();
    }

    private void changed() {
        VisorSettings.save();
        init();
    }

    @Override
    public void onClose() {
        VisorSettings.save();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // sin desenfoque: se ve el HUD real detras
    }

    private int[] screenRect(String id) {
        float sc = scaleOf(id);
        int sw = (int) (width / sc), sh = (int) (height / sc);
        int[] r = HudLayout.rect(id, sw, sh);
        return new int[]{Math.round(r[0] * sc), Math.round(r[1] * sc), Math.round(r[2] * sc), Math.round(r[3] * sc)};
    }

    private boolean inPanel(int mx, int my) {
        return mx >= px && mx < px + PW && my >= py && my < py + PH;
    }

    /** Arrastre: el boton del raton se lee directo (vale para cualquier version del juego). */
    private void handleDrag(int mx, int my) {
        boolean down = mouseDown();
        if (down && !wasDown && !inPanel(mx, my)) {
            for (int i = IDS.size() - 1; i >= 0; i--) {
                String id = IDS.get(i);
                int[] r = screenRect(id);
                if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                    dragging = id; grabX = mx - r[0]; grabY = my - r[1];
                    if (!id.equals(selected)) { selected = id; init(); }
                    break;
                }
            }
        }
        if (down && dragging != null) {
            float sc = scaleOf(dragging);
            int[] r = screenRect(dragging);
            int sw = (int) (width / sc), sh = (int) (height / sc);
            HudLayout.moveTo(dragging, Math.round((mx - grabX) / sc), Math.round((my - grabY) / sc),
                    Math.round(r[2] / sc), Math.round(r[3] / sc), sw, sh);
        }
        if (!down && dragging != null) { dragging = null; VisorSettings.save(); }
        wasDown = down;
    }

    private static boolean mouseDown() {
        Object w = Minecraft.getInstance().getWindow();
        for (String n : new String[]{"handle", "getWindow"}) {
            try {
                Object o = w.getClass().getMethod(n).invoke(w);
                if (o instanceof Long h) return GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        handleDrag(mouseX, mouseY);
        // recuadros de las piezas
        for (String id : IDS) {
            int[] r = screenRect(id);
            boolean sel = id.equals(selected), vis = HudLayout.get(id).visible;
            int c = sel ? 0xFF28D250 : vis ? 0xFFE8E8EE : 0xFF6A6A78;
            g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], sel ? 0x3028D250 : 0x30101018);
            g.fill(r[0], r[1], r[0] + r[2], r[1] + 1, c); g.fill(r[0], r[1] + r[3] - 1, r[0] + r[2], r[1] + r[3], c);
            g.fill(r[0], r[1], r[0] + 1, r[1] + r[3], c); g.fill(r[0] + r[2] - 1, r[1], r[0] + r[2], r[1] + r[3], c);
            Component label = name(id).copy().append(vis ? "" : " ✗");
            g.text(font, label, r[0] + 3, r[1] + 3, c, true);
        }
        // panel
        g.fill(px - 1, py - 1, px + PW + 1, py + PH + 1, 0xFF0E0E14);
        g.fill(px, py, px + PW, py + PH, 0xE81C1C26);
        g.text(font, title, px + 6, py + 6, 0xFFF2C230, true);
        Component hint = Component.translatable("screen.dexrider.visor_layout.hint");
        g.text(font, hint, px + PW - 6 - font.width(hint), py + 6, 0xFF9A93A6, false);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private class ScaleSlider extends AbstractSliderButton {
        private final HudLayout.Elem e;

        ScaleSlider(int x, int y, int w, HudLayout.Elem e) {
            super(x, y, w, 16, Component.empty(), (e.scale - 0.5) / 1.5);
            this.e = e;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(Math.round((0.5 + value * 1.5) * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            e.scale = (float) (0.5 + value * 1.5);
            VisorSettings.save();
        }
    }
}
