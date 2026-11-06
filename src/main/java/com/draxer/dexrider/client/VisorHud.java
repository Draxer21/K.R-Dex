package com.draxer.dexrider.client;

import com.draxer.dexrider.DexRider;
import com.draxer.dexrider.config.DexConfig;
import com.draxer.dexrider.item.DexCard;
import com.draxer.dexrider.item.DexDriverItem;
import com.draxer.dexrider.item.SuitLook;
import com.draxer.dexrider.item.SuitLooks;
import com.draxer.dexrider.menu.Booker;
import com.draxer.dexrider.network.HudState;
import com.draxer.dexrider.network.HudSyncPayload;
import com.draxer.dexrider.network.TargetSyncPayload;
import com.draxer.dexrider.registry.DexComponents;
import com.draxer.dexrider.rider.DexEmotions;
import com.draxer.dexrider.rider.FormRide;
import com.draxer.dexrider.rider.KamenRide;
import com.draxer.dexrider.rider.Suits;
import com.draxer.dexrider.rider.Transformation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Interfaz del visor (transformado): lo que ve Dex desde dentro del casco. El Rider System es tecnologia.
 *
 *  - Arranque al hacer Henshin: lineas de sistema que se escriben y se recogen.
 *  - Marco del visor: esquinas, la forma del lente arriba y lineas de barrido, del color de los ojos del traje.
 *  - Lecturas: integridad del traje (vida), energia (recarga de R), forma, Accel y Emotion; la carta elegida.
 *  - Reticula de escaneo alrededor de la mira mientras el Driver toma muestras.
 *  - Lectura del objetivo (arriba a la derecha) y alertas: integridad critica, correa activa, salida heroica.
 *
 * Ajustable (VisorSettings / boton "Visor" del Archivo): completo, minimo o clasico; opacidad, tamano, color y piezas.
 */
@EventBusSubscriber(modid = DexRider.MODID, value = Dist.CLIENT)
public final class VisorHud {
    private VisorHud() {}

    private static final int BOOT_TICKS = 56;
    private static boolean wasTransformed;
    private static int bootTick = -1, ticks;
    private static int bootCards;

    @SubscribeEvent
    static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, DexRider.id("visor"), VisorHud::draw);
        event.registerBelowAll(DexRider.id("visor_filter"), VisorHud::drawFilter);
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        ticks++;
        Player p = Minecraft.getInstance().player;
        boolean now = p != null && Transformation.isTransformed(p);
        if (now && !wasTransformed) {
            bootTick = 0;
            ItemStack driver = DexDriverItem.findDriver(p);
            int n = 0;
            if (!driver.isEmpty()) for (ItemStack s : Booker.view(driver)) if (!s.isEmpty()) n++;
            bootCards = n;
        } else if (bootTick >= 0 && ++bootTick > BOOT_TICKS) {
            bootTick = -1;
        }
        wasTransformed = now;
    }

    /** Para la vista previa de los ajustes: repetir el arranque. */
    public static void replayBoot() {
        bootTick = 0;
    }

    // ------------------------------------------------------------------ color y opacidad

    private static float opacity = 1F;

    /** argb con la opacidad del visor aplicada. */
    private static int a(int argb) {
        int alpha = (argb >>> 24);
        return ((int) (alpha * opacity) << 24) | (argb & 0xFFFFFF);
    }

    private static int rgb(int rgb, int alpha) {
        return a((alpha << 24) | (rgb & 0xFFFFFF));
    }

    /** Color del visor: el de los ojos del traje en su estado actual (o el elegido a mano). */
    static int visorColor(Player p, VisorSettings s) {
        if (s.colorMode == VisorSettings.ColorMode.CUSTOM) return s.customColor;
        KamenRide k = KamenRide.current(p);
        if (k != KamenRide.DEX) return k.color & 0xFFFFFF;
        ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
        String formId = chest.get(DexComponents.FORM.get());
        FormRide form = formId == null ? null : FormRide.byId(formId);
        String state = SuitLooks.stateFor(Boolean.TRUE.equals(chest.get(DexComponents.UPGRADE.get())), chest.get(DexComponents.EMOTION.get()), form);
        return SuitLooks.resolve(Suits.worn(chest), state).color(SuitLook.EYES);
    }

    // ------------------------------------------------------------------ dibujo

    private static void draw(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        VisorSettings s = VisorSettings.get();
        if (p == null || mc.options.hideGui || !DexConfig.hud() || s.mode == VisorSettings.Mode.CLASSIC) return;
        if (!Transformation.isTransformed(p)) return;
        opacity = s.opacity;
        int color = visorColor(p, s);
        String emotion = p.getItemBySlot(EquipmentSlot.CHEST).get(DexComponents.EMOTION.get());
        boolean violent = DexEmotions.VIOLENT.equals(emotion);

        float sc = s.scale;
        g.pose().pushMatrix();
        g.pose().scale(sc, sc);
        int sw = (int) (mc.getWindow().getGuiScaledWidth() / sc), sh = (int) (mc.getWindow().getGuiScaledHeight() / sc);
        Font font = mc.font;
        boolean full = s.mode == VisorSettings.Mode.FULL;

        if (s.frame) frame(g, sw, sh, color, violent);
        if (s.scanlines) scanlines(g, sw, sh, color, violent);
        if (bootTick >= 0 && s.boot) boot(g, font, sw, sh, color);
        else {
            if (full) status(g, font, p, color);
            card(g, mc, font, sw, sh, color);
            if (full && s.target && DexConfig.targetRead()) target(g, mc, font, sw, color);
        }
        if (s.reticle) reticle(g, font, sw, sh, color);
        if (s.alerts) alerts(g, font, p, sw, emotion);
        g.pose().popMatrix();
    }

    /**
     * Filtro de la vision (debajo de todo el HUD): un velo del color del lente sobre el mundo y una vineta en los bordes.
     * Con la Violent Emotion el velo late en rojo.
     */
    private static void drawFilter(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        VisorSettings s = VisorSettings.get();
        if (p == null || mc.options.hideGui || !DexConfig.hud() || s.mode == VisorSettings.Mode.CLASSIC || !Transformation.isTransformed(p)) return;
        if (!s.filter && !s.vignette) return;
        if (!mc.options.getCameraType().isFirstPerson()) return;
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        int color = visorColor(p, s);
        boolean violent = DexEmotions.VIOLENT.equals(p.getItemBySlot(EquipmentSlot.CHEST).get(DexComponents.EMOTION.get()));
        float boot = bootTick >= 0 ? Math.min(1F, bootTick / 20F) : 1F;          // el filtro "enciende" con el arranque
        if (s.filter && s.filterStrength > 0) {
            float k = s.filterStrength * boot;
            if (violent) k += 0.06F * (float) (0.5 + 0.5 * Math.sin(ticks * 0.35));
            g.fill(0, 0, sw, sh, ((int) (255 * Math.min(0.6F, k)) << 24) | (violent ? 0xC01830 : color));
        }
        if (s.vignette) {
            int edge = Math.max(sw, sh) / 9, steps = Math.max(8, edge);       // una franja por pixel: sin escalones
            for (int i = 0; i < steps; i++) {
                float t = 1F - i / (float) steps;
                int a = (int) ((0x50 * boot) * t * t);
                int c = (a << 24) | (shade(color, 0.25F));
                int d = i * edge / steps, e = d + edge / steps;
                g.fill(0, d, sw, e, c); g.fill(0, sh - e, sw, sh - d, c);            // arriba / abajo
                g.fill(d, e, e, sh - e, c); g.fill(sw - e, e, sw - d, sh - e, c);  // izquierda / derecha
            }
        }
    }

    private static int shade(int rgb, float f) {
        int r = (int) (((rgb >> 16) & 0xFF) * f), gg = (int) (((rgb >> 8) & 0xFF) * f), b = (int) ((rgb & 0xFF) * f);
        return (r << 16) | (gg << 8) | b;
    }

    /** Esquinas y la forma del lente arriba. */
    private static void frame(GuiGraphicsExtractor g, int sw, int sh, int color, boolean violent) {
        int c = rgb(color, 0xB0), L = 26, m = 5;
        int jx = violent && ticks % 7 < 2 ? (ticks * 13 % 5) - 2 : 0;
        // esquinas
        g.fill(m + jx, m, m + L + jx, m + 2, c); g.fill(m + jx, m, m + 2 + jx, m + L, c);
        g.fill(sw - m - L, m, sw - m, m + 2, c); g.fill(sw - m - 2, m, sw - m, m + L, c);
        g.fill(m, sh - m - 2, m + L, sh - m, c); g.fill(m, sh - m - L, m + 2, sh - m, c);
        g.fill(sw - m - L, sh - m - 2, sw - m, sh - m, c); g.fill(sw - m - 2, sh - m - L, sw - m, sh - m, c);
        // el lente: dos diagonales que bajan hacia el centro, como los ojos del casco
        int cx = sw / 2, c2 = rgb(color, 0x70);
        for (int i = 0; i < 46; i++) {
            int y = 3 + i / 5;
            g.fill(cx - 120 + i * 2, y, cx - 118 + i * 2, y + 1, c2);
            g.fill(cx + 118 - i * 2, y, cx + 120 - i * 2, y + 1, c2);
        }
        g.fill(cx - 26, 12, cx + 26, 13, c2);
        g.fill(cx - 2, 13, cx + 2, 16, rgb(color, 0xC0));
    }

    private static void scanlines(GuiGraphicsExtractor g, int sw, int sh, int color, boolean violent) {
        int c = rgb(color, violent ? 0x14 : 0x0A);
        for (int y = (ticks % 3); y < sh; y += 3) g.fill(0, y, sw, y + 1, c);
        if (violent) {   // interferencia de la correa
            for (int k = 0; k < 3; k++) {
                int y = (ticks * (37 + k * 11) + k * 97) % Math.max(1, sh);
                g.fill(0, y, sw, y + 1 + (k == 0 ? 1 : 0), rgb(0xFF2040, 0x40));
            }
        }
    }

    private static void boot(GuiGraphicsExtractor g, Font font, int sw, int sh, int color) {
        String[] lines = {
                Component.translatable("visor.dexrider.boot1").getString(),
                Component.translatable("visor.dexrider.boot2").getString(),
                Component.translatable("visor.dexrider.boot3", bootCards).getString(),
                Component.translatable("visor.dexrider.boot4").getString(),
                Component.translatable("visor.dexrider.boot5").getString()};
        float fade = bootTick > BOOT_TICKS - 10 ? (BOOT_TICKS - bootTick) / 10F : 1F;
        int x = 24, y = sh / 2 - 34;
        for (int i = 0; i < lines.length; i++) {
            int start = i * 8;
            if (bootTick < start) break;
            int chars = Math.min(lines[i].length(), (bootTick - start) * 3);
            String shown = lines[i].substring(0, chars) + (chars < lines[i].length() && ticks % 4 < 2 ? "_" : "");
            int col = i == lines.length - 1 ? 0xFFFFFF : color;
            g.text(font, shown, x, y + i * 12, rgb(col, (int) (0xF0 * fade)), true);
        }
        g.fill(x - 6, y - 4, x - 4, y + lines.length * 12, rgb(color, (int) (0xA0 * fade)));
    }

    private static void status(GuiGraphicsExtractor g, Font font, Player p, int color) {
        HudSyncPayload hs = HudState.last;
        int x = 12, y = 14, w = 118;
        ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
        KamenRide k = KamenRide.current(p);
        String formId = chest.get(DexComponents.FORM.get());
        String head = k.displayName().getString().toUpperCase(Locale.ROOT);
        if (k == KamenRide.DEX && Boolean.TRUE.equals(chest.get(DexComponents.UPGRADE.get()))) head += " · UPGRADE";
        else if (k == KamenRide.DEX && formId != null) {
            Integer lv = chest.get(DexComponents.FORM_LEVEL.get());
            head += " · " + Component.translatable("form.dexrider." + formId).getString() + " Nv." + (lv == null ? 1 : lv);
        }
        g.text(font, head, x, y, rgb(color, 0xFF), true);
        y += 12;
        float hp = p.getHealth() / Math.max(1F, p.getMaxHealth());
        readout(g, font, x, y, w, "visor.dexrider.integrity", hp, hp > 0.5F ? color : hp > 0.25F ? 0xF2C230 : 0xFF3040,
                Math.round(hp * 100) + "%");
        y += 14;
        boolean live = System.currentTimeMillis() - HudState.receivedAt < 3000 && hs.active();
        if (live) {
            float energy = hs.abilityLeft() <= 0 ? 1F : 1F - hs.abilityLeft() / (float) Math.max(1, hs.abilityTotal());
            readout(g, font, x, y, w, "visor.dexrider.energy", energy, energy >= 1F ? color : 0x8A8AA0,
                    energy >= 1F ? Component.translatable("visor.dexrider.ready").getString() : String.format(Locale.ROOT, "%.1fs", hs.abilityLeft() / 20.0));
            y += 14;
            if (hs.accelLeft() > 0) {
                readout(g, font, x, y, w, "visor.dexrider.accel", hs.accelLeft() / 200F, 0xFF463C, (hs.accelLeft() + 19) / 20 + "s");
                y += 14;
            }
            if (!hs.emotion().isEmpty()) {
                boolean v = DexEmotions.VIOLENT.equals(hs.emotion());
                readout(g, font, x, y, w, v ? "visor.dexrider.violent" : "visor.dexrider.heroic", hs.emotionLeft() / 400F,
                        v ? 0xB44CFF : 0x7FB8FF, (hs.emotionLeft() + 19) / 20 + "s");
            }
        }
    }

    private static void readout(GuiGraphicsExtractor g, Font font, int x, int y, int w, String key, float t, int color, String value) {
        g.text(font, Component.translatable(key).getString(), x, y, rgb(0xE8E8EE, 0xE0), true);
        g.text(font, value, x + w - font.width(value), y, rgb(color, 0xFF), true);
        t = Math.max(0, Math.min(1, t));
        g.fill(x, y + 9, x + w, y + 11, rgb(0x202028, 0xC0));
        g.fill(x, y + 9, x + Math.round(w * t), y + 11, rgb(color, 0xF0));
        for (int i = 1; i < 10; i++) g.fill(x + w * i / 10, y + 9, x + w * i / 10 + 1, y + 11, rgb(0x000000, 0x80));
    }

    /** Carta elegida del Booker, a la izquierda de la barra de objetos (y la tira de Alt). */
    private static void card(GuiGraphicsExtractor g, Minecraft mc, Font font, int sw, int sh, int color) {
        HudSyncPayload hs = HudState.last;
        if (!hs.active() || System.currentTimeMillis() - HudState.receivedAt > 3000) return;
        String name;
        if (hs.card().isEmpty()) name = Component.translatable("hud.dexrider.no_card").getString();
        else {
            var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(hs.card()));
            name = new ItemStack(item).getHoverName().getString();
            int dash = name.lastIndexOf(" - ");
            if (dash >= 0) name = name.substring(dash + 3);
        }
        String line = "▸ " + name.toUpperCase(Locale.ROOT);
        int x = sw / 2 - 91 - 10 - font.width(line), y = sh - 12;
        if (x < 4) x = 4;
        g.fill(x - 4, y - 3, sw / 2 - 91 - 6, y + 10, rgb(0x08080C, 0x90));
        g.fill(x - 4, y - 3, x - 2, y + 10, rgb(color, 0xF0));
        g.text(font, line, x, y, rgb(0xFFFFFF, 0xFF), true);
        if (DexRiderClient.BOOKER_USE_KEY.get().isDown()) RiderHud.strip(g, mc, x - 4, y - 30, hs.slot());
    }

    /** Lectura del objetivo, arriba a la derecha. */
    private static void target(GuiGraphicsExtractor g, Minecraft mc, Font font, int sw, int color) {
        var s = TargetSyncPayload.last;
        if (!s.active() || mc.level == null || System.currentTimeMillis() - TargetSyncPayload.receivedAt > 3000) return;
        var e = mc.level.getEntity(s.entityId());
        int w = 128, x = sw - 12 - w, y = 14;
        String name = e != null ? e.getDisplayName().getString() : Component.translatable("hud.dexrider.target").getString();
        g.text(font, RiderHud.clip(font, name.toUpperCase(Locale.ROOT), w), x, y, rgb(color, 0xFF), true);
        y += 12;
        float t = s.maxHp() > 0 ? s.hp() / s.maxHp() : 0;
        readout(g, font, x, y, w, "visor.dexrider.target_hp", t, t > 0.5F ? 0x3FD27A : t > 0.25F ? 0xF2C230 : 0xFF3040,
                RiderHud.hearts(s.hp()) + "/" + RiderHud.hearts(s.maxHp()) + "❤");
        y += 15;
        if (s.dealt() > 0) {
            String l = s.hits() > 0 ? Component.translatable("hud.dexrider.you_hit_left", RiderHud.hearts(s.dealt()), s.hits()).getString()
                    : Component.translatable("hud.dexrider.you_hit", RiderHud.hearts(s.dealt())).getString();
            g.text(font, RiderHud.clip(font, l, w), x, y, rgb(0x9FE8FF, 0xFF), true); y += 10;
        }
        if (s.taken() > 0) {
            String l = s.survive() > 0 ? Component.translatable("hud.dexrider.it_hits_survive", RiderHud.hearts(s.taken()), s.survive()).getString()
                    : Component.translatable("hud.dexrider.it_hits", RiderHud.hearts(s.taken())).getString();
            g.text(font, RiderHud.clip(font, l, w), x, y, rgb(s.survive() > 0 && s.survive() <= 2 ? 0xFF6A6A : 0xFFC8A0, 0xFF), true);
        }
    }

    /** Reticula de escaneo alrededor de la mira mientras el Driver toma muestras. */
    private static void reticle(GuiGraphicsExtractor g, Font font, int sw, int sh, int color) {
        DexCard c = ScanToast.scanning();
        boolean done = ScanToast.justCompleted();
        if (c == null && !done) return;
        int cx = sw / 2, cy = sh / 2, r = 14 + (int) (3 * Math.sin(ticks * 0.5)), L = 6;
        int col = rgb(done ? 0x3FD27A : 0xFF2BD6, 0xE0);
        g.fill(cx - r, cy - r, cx - r + L, cy - r + 1, col); g.fill(cx - r, cy - r, cx - r + 1, cy - r + L, col);
        g.fill(cx + r - L, cy - r, cx + r, cy - r + 1, col); g.fill(cx + r - 1, cy - r, cx + r, cy - r + L, col);
        g.fill(cx - r, cy + r - 1, cx - r + L, cy + r, col); g.fill(cx - r, cy + r - L, cx - r + 1, cy + r, col);
        g.fill(cx + r - L, cy + r - 1, cx + r, cy + r, col); g.fill(cx + r - 1, cy + r - L, cx + r, cy + r, col);
        String label = Component.translatable(done ? "visor.dexrider.scan_done" : "visor.dexrider.scanning").getString();
        g.text(font, label, cx - font.width(label) / 2, cy + r + 4, col, true);
        if (!done) {
            int bw = 40;
            g.fill(cx - bw / 2, cy + r + 14, cx + bw / 2, cy + r + 16, rgb(0x202028, 0xC0));
            g.fill(cx - bw / 2, cy + r + 14, cx - bw / 2 + Math.round(bw * ScanToast.scanProgress()), cy + r + 16, col);
        }
    }

    private static void alerts(GuiGraphicsExtractor g, Font font, Player p, int sw, String emotion) {
        List<Component> out = new ArrayList<>();
        List<Integer> cols = new ArrayList<>();
        if (p.getHealth() / Math.max(1F, p.getMaxHealth()) <= 0.25F) { out.add(Component.translatable("visor.dexrider.alert_integrity")); cols.add(0xFF3040); }
        if (DexEmotions.VIOLENT.equals(emotion)) { out.add(Component.translatable("visor.dexrider.alert_leash")); cols.add(0xFF2040); }
        if (DexEmotions.HEROIC.equals(emotion)) { out.add(Component.translatable("visor.dexrider.alert_heroic")); cols.add(0x7FB8FF); }
        if (p.getAirSupply() < p.getMaxAirSupply() / 3) { out.add(Component.translatable("visor.dexrider.alert_air")); cols.add(0x4FA8FF); }
        if (out.isEmpty() || ticks % 20 >= 14) return;
        int y = 60;
        for (int i = 0; i < out.size(); i++) {
            String t = "⚠ " + out.get(i).getString() + " ⚠";
            int w = font.width(t);
            g.fill(sw / 2 - w / 2 - 6, y - 3, sw / 2 + w / 2 + 6, y + 10, rgb(0x08080C, 0xA0));
            g.text(font, t, sw / 2 - w / 2, y, rgb(cols.get(i), 0xFF), true);
            y += 15;
        }
    }
}
