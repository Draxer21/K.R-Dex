package com.draxer.dexrider.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Ajustes de la interfaz del visor (desde el Archivo del Driver). El fondo no se oscurece: lo que cambias se ve detras.
 */
public class VisorScreen extends Screen {

    private static final int W = 300, H = 226;
    private static final List<Integer> COLORS = List.of(0x28D250, 0x3CE0D0, 0x4FA8FF, 0xB44CFF, 0xFF2BD6, 0xFF3040, 0xF2C230, 0xFFFFFF);
    private int left, top;

    public VisorScreen() {
        super(Component.translatable("screen.dexrider.visor"));
    }

    private static VisorSettings s() { return VisorSettings.get(); }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        clearWidgets();
        int x = left + 8, y = top + 22;
        addRenderableWidget(Button.builder(Component.translatable("screen.dexrider.visor.mode",
                Component.translatable("screen.dexrider.visor.mode." + s().mode.name().toLowerCase(Locale.ROOT))), b -> {
            VisorSettings.Mode[] m = VisorSettings.Mode.values();
            s().mode = m[(s().mode.ordinal() + 1) % m.length];
            changed();
        }).bounds(x, y, 140, 16).tooltip(Tooltip.create(Component.translatable("screen.dexrider.visor.mode.tip"))).build());
        addRenderableWidget(new Slider(x + 144, y, 140, "screen.dexrider.visor.opacity", 0.2, 1.0, s().opacity, v -> s().opacity = v.floatValue(),
                v -> Math.round(v * 100) + "%"));
        y += 20;
        addRenderableWidget(new Slider(x, y, 140, "screen.dexrider.visor.scale", 0.5, 1.5, s().scale, v -> s().scale = v.floatValue(),
                v -> Math.round(v * 100) + "%"));
        addRenderableWidget(Button.builder(Component.translatable("screen.dexrider.visor.color",
                Component.translatable("screen.dexrider.visor.color." + s().colorMode.name().toLowerCase(Locale.ROOT))), b -> {
            s().colorMode = s().colorMode == VisorSettings.ColorMode.SUIT ? VisorSettings.ColorMode.CUSTOM : VisorSettings.ColorMode.SUIT;
            changed();
        }).bounds(x + 144, y, 140, 16).build());
        y += 20;
        if (s().colorMode == VisorSettings.ColorMode.CUSTOM) {
            for (int i = 0; i < COLORS.size(); i++) {
                final int c = COLORS.get(i);
                addRenderableWidget(Button.builder(Component.empty(), b -> { s().customColor = c; changed(); })
                        .bounds(x + 144 + i * 17 + 2, y, 15, 15).build());
            }
        }
        y += 22;
        toggle(x, y, "boot", s().boot, v -> s().boot = v);
        toggle(x + 144, y, "frame", s().frame, v -> s().frame = v);
        y += 20;
        toggle(x, y, "scanlines", s().scanlines, v -> s().scanlines = v);
        toggle(x + 144, y, "reticle", s().reticle, v -> s().reticle = v);
        y += 20;
        toggle(x, y, "alerts", s().alerts, v -> s().alerts = v);
        toggle(x + 144, y, "target", s().target, v -> s().target = v);
        y += 20;
        toggle(x, y, "filter", s().filter, v -> s().filter = v);
        toggle(x + 144, y, "vignette", s().vignette, v -> s().vignette = v);
        y += 20;
        addRenderableWidget(new Slider(x, y, 284, "screen.dexrider.visor.filter_strength", 0.0, 0.5, s().filterStrength,
                v -> s().filterStrength = v.floatValue(), v -> Math.round(v * 200) + "%"));
        y += 26;
        addRenderableWidget(Button.builder(Component.translatable("screen.dexrider.visor.test_boot"), b -> { VisorSettings.save(); VisorHud.replayBoot(); onClose(); })
                .bounds(x, y, 92, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.dexrider.visor.reset"), b -> { VisorSettings.reset(); init(); })
                .bounds(x + 96, y, 92, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x + 192, y, 92, 18).build());
    }

    private void toggle(int x, int y, String key, boolean on, Consumer<Boolean> set) {
        addRenderableWidget(Button.builder(Component.translatable("screen.dexrider.visor." + key)
                .append(": ").append(Component.translatable(on ? "options.on" : "options.off")), b -> { set.accept(!on); changed(); })
                .bounds(x, y, 140, 16).build());
    }

    private void changed() {
        VisorSettings.save();
        init();
    }

    @Override
    public void onClose() {
        VisorSettings.save();
        super.onClose();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // sin desenfoque: el visor se ve detras mientras lo ajustas
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(left - 2, top - 2, left + W + 2, top + H + 2, 0xE00E0E14);
        g.fill(left, top, left + W, top + H, 0xE01C1C26);
        g.fill(left, top, left + W, top + 2, 0xFF000000 | VisorHud.visorColor(minecraft.player, s()));
        g.text(font, title, left + 8, top + 7, 0xFFF2C230, true);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        if (s().colorMode == VisorSettings.ColorMode.CUSTOM) {
            for (int i = 0; i < COLORS.size(); i++) {
                int bx = left + 8 + 144 + i * 17 + 2, by = top + 62;
                g.fill(bx + 2, by + 2, bx + 13, by + 13, 0xFF000000 | COLORS.get(i));
                if (COLORS.get(i) == s().customColor) g.fill(bx + 5, by + 15, bx + 10, by + 16, 0xFFFFFFFF);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static class Slider extends AbstractSliderButton {
        private final String key;
        private final double min, max;
        private final Consumer<Double> set;
        private final Function<Double, String> fmt;

        Slider(int x, int y, int w, String key, double min, double max, double value, Consumer<Double> set, Function<Double, String> fmt) {
            super(x, y, w, 16, Component.empty(), (value - min) / (max - min));
            this.key = key; this.min = min; this.max = max; this.set = set; this.fmt = fmt;
            updateMessage();
        }

        private double real() { return min + value * (max - min); }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(key).append(": " + fmt.apply(real())));
        }

        @Override
        protected void applyValue() {
            set.accept(real());
            VisorSettings.save();
        }
    }
}
