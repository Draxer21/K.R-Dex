package com.draxer.dexrider.client;

import com.draxer.dexrider.item.SuitConfig;
import com.draxer.dexrider.network.SuitPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * (26.1: el dibujo de pantallas usa GuiGraphicsExtractor y extractRenderState en vez de GuiGraphics/render.)
 *
 * Pantalla de personalizacion del traje (Shift + clic derecho con el Dex Driver en la mano).
 *
 *  - Pestanas: Principal / Ojos / Acentos (que color se esta editando).
 *  - 12 colores rapidos + barras R, G y B para cualquier color.
 *  - Cuernos (rectos, curvos, cortos, ninguno), franjas de los ojos, Ride Booker visible.
 *  - Restablecer (traje original) y Guardar (se envia al servidor y queda en el Driver).
 */
public class SuitScreen extends Screen {

    private enum Target { MAIN, EYES, ACCENT }

    private SuitConfig config;
    private Target target = Target.MAIN;

    private final List<Button> tabs = new ArrayList<>();
    private final List<Button> swatches = new ArrayList<>();
    private ChannelSlider red, green, blue;
    private Button hornsButton, stripesButton, bookerButton;

    private int left, top;
    private static final int PANEL_W = 260, PANEL_H = 214;

    public SuitScreen(SuitConfig initial) {
        super(Component.translatable("screen.dexrider.suit"));
        this.config = initial == null ? SuitConfig.DEFAULT : initial;
    }

    @Override
    protected void init() {
        this.left = (this.width - PANEL_W) / 2;
        this.top = (this.height - PANEL_H) / 2;
        tabs.clear();
        swatches.clear();

        // Pestanas
        int tx = left + 8;
        for (Target t : Target.values()) {
            Button b = Button.builder(Component.translatable("screen.dexrider.suit.tab." + t.name().toLowerCase(Locale.ROOT)),
                    btn -> selectTarget(t)).bounds(tx, top + 22, 78, 18).build();
            tabs.add(addRenderableWidget(b));
            tx += 82;
        }

        // Paleta: 12 colores en 2 filas
        for (int i = 0; i < SuitConfig.PALETTE.size(); i++) {
            final int color = SuitConfig.PALETTE.get(i);
            int sx = left + 8 + (i % 6) * 22;
            int sy = top + 48 + (i / 6) * 22;
            swatches.add(addRenderableWidget(Button.builder(Component.empty(), btn -> setColor(color))
                    .bounds(sx, sy, 18, 18).build()));
        }

        // Barras R G B
        red = addRenderableWidget(new ChannelSlider(left + 8, top + 96, 16));
        green = addRenderableWidget(new ChannelSlider(left + 8, top + 118, 8));
        blue = addRenderableWidget(new ChannelSlider(left + 8, top + 140, 0));

        // Variantes de piezas
        hornsButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            config = config.withHorns(config.horns().next());
            refresh();
        }).bounds(left + 8, top + 166, 120, 18).build());
        stripesButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            config = config.withEyeStripes(!config.eyeStripes());
            refresh();
        }).bounds(left + 132, top + 166, 120, 18).build());
        bookerButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            config = config.withBooker(!config.showBooker());
            refresh();
        }).bounds(left + 8, top + 188, 120, 18).build());

        // Restablecer y guardar
        addRenderableWidget(Button.builder(Component.translatable("screen.dexrider.suit.reset"), b -> {
            config = SuitConfig.DEFAULT;
            refresh();
        }).bounds(left + 132, top + 188, 58, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.dexrider.suit.save"), b -> {
            ClientPacketDistributor.sendToServer(new SuitPayload(config));
            onClose();
        }).bounds(left + 194, top + 188, 58, 18).build());

        refresh();
    }

    private void selectTarget(Target t) {
        this.target = t;
        refresh();
    }

    private int currentColor() {
        return switch (target) {
            case MAIN -> config.mainColor();
            case EYES -> config.eyeColor();
            case ACCENT -> config.accentColor();
        };
    }

    private void setColor(int color) {
        config = switch (target) {
            case MAIN -> config.withMain(color);
            case EYES -> config.withEyes(color);
            case ACCENT -> config.withAccent(color);
        };
        refresh();
    }

    /** Actualiza textos de botones y posiciones de las barras segun la configuracion actual. */
    private void refresh() {
        for (int i = 0; i < tabs.size(); i++) tabs.get(i).active = Target.values()[i] != target;
        int c = currentColor();
        if (red != null) { red.setFromColor(c); green.setFromColor(c); blue.setFromColor(c); }
        if (hornsButton != null) {
            hornsButton.setMessage(Component.translatable("screen.dexrider.suit.horns",
                    Component.translatable("screen.dexrider.suit.horns." + config.horns().id())));
            stripesButton.setMessage(Component.translatable(config.eyeStripes()
                    ? "screen.dexrider.suit.stripes_on" : "screen.dexrider.suit.stripes_off"));
            bookerButton.setMessage(Component.translatable(config.showBooker()
                    ? "screen.dexrider.suit.booker_on" : "screen.dexrider.suit.booker_off"));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // Panel
        g.fill(left - 2, top - 2, left + PANEL_W + 2, top + PANEL_H + 2, 0xFF0E0E14);
        g.fill(left, top, left + PANEL_W, top + PANEL_H, 0xFF22222C);
        g.text(this.font, this.title, left + 8, top + 7, 0xFFF2C230, true);

        super.extractRenderState(g, mouseX, mouseY, partialTick);

        // Colores de la paleta encima de sus botones
        for (int i = 0; i < swatches.size(); i++) {
            Button b = swatches.get(i);
            int col = 0xFF000000 | SuitConfig.PALETTE.get(i);
            g.fill(b.getX() + 2, b.getY() + 2, b.getX() + b.getWidth() - 2, b.getY() + b.getHeight() - 2, col);
        }

        // Muestra grande del color actual + codigo hexadecimal
        int c = currentColor();
        int px = left + 150, py = top + 48;
        g.fill(px - 1, py - 1, px + 101, py + 41, 0xFFFFFFFF);
        g.fill(px, py, px + 100, py + 40, 0xFF000000 | c);
        g.text(this.font, Component.literal(String.format("#%06X", c)), px, py + 46, 0xFFFFFFFF, true);

        // Resumen de los tres colores
        int sy = top + 108;
        drawChip(g, left + 150, sy, config.mainColor(), "screen.dexrider.suit.tab.main");
        drawChip(g, left + 150, sy + 14, config.eyeColor(), "screen.dexrider.suit.tab.eyes");
        drawChip(g, left + 150, sy + 28, config.accentColor(), "screen.dexrider.suit.tab.accent");
    }

    private void drawChip(GuiGraphicsExtractor g, int x, int y, int color, String key) {
        g.fill(x, y, x + 10, y + 10, 0xFF000000 | color);
        g.text(this.font, Component.translatable(key), x + 14, y + 1, 0xFFDDDDDD, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Barra de un canal de color (R, G o B), de 0 a 255. */
    private class ChannelSlider extends AbstractSliderButton {
        private final int shift;   // 16 = rojo, 8 = verde, 0 = azul

        ChannelSlider(int x, int y, int shift) {
            super(x, y, 136, 18, Component.empty(), 0);
            this.shift = shift;
        }

        void setFromColor(int color) {
            this.value = ((color >> shift) & 0xFF) / 255.0;
            updateMessage();
        }

        private int channel() {
            return (int) Math.round(this.value * 255);
        }

        @Override
        protected void updateMessage() {
            String name = shift == 16 ? "R" : shift == 8 ? "G" : "B";
            setMessage(Component.literal(name + ": " + channel()));
        }

        @Override
        protected void applyValue() {
            int c = currentColor();
            int updated = (c & ~(0xFF << shift)) | (channel() << shift);
            config = switch (target) {
                case MAIN -> config.withMain(updated);
                case EYES -> config.withEyes(updated);
                case ACCENT -> config.withAccent(updated);
            };
        }
    }
}
