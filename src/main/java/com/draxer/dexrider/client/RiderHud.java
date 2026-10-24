package com.draxer.dexrider.client;

import com.draxer.dexrider.DexRider;
import com.draxer.dexrider.config.DexConfig;
import com.draxer.dexrider.network.HudState;
import com.draxer.dexrider.network.HudSyncPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * HUD del Rider, a la izquierda de la barra de objetos:
 *   carta elegida del Ride Booker (mazo y ranura) · recarga de R · Accel de Cipher · Violent / Heroic Emotion.
 * Solo se ve transformado. Se puede apagar en la configuracion (dex.hud).
 */
@EventBusSubscriber(modid = DexRider.MODID, value = Dist.CLIENT)
public final class RiderHud {
    private RiderHud() {}

    private static final String[] DECKS = {"rescue", "crowd", "duel", "chase", "everything_wrong"};

    @SubscribeEvent
    static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, DexRider.id("rider_hud"), RiderHud::draw);
        event.registerAbove(VanillaGuiLayers.HOTBAR, DexRider.id("target_read"), RiderHud::drawTarget);
    }

    private static void draw(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        HudSyncPayload s = HudState.last;
        if (mc.player == null || !s.active() || !DexConfig.hud()) return;
        if (System.currentTimeMillis() - HudState.receivedAt > 3000) return;   // datos viejos: no mostrar
        Font font = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        int w = 112;
        int x = sw / 2 - 91 - w - 8;
        int rows = 2 + (s.accelLeft() > 0 ? 1 : 0) + (!s.emotion().isEmpty() || s.emotionCooldown() > 0 ? 1 : 0);
        int h = 6 + rows * 11;
        int y = sh - h - 2;
        if (x < 2) { x = 2; y = sh - 22 - h - 14; }

        // marco
        g.fill(x, y, x + w, y + h, 0xB0101018);
        g.fill(x, y, x + 2, y + h, 0xFFFF2BD6);
        g.fill(x, y, x + w, y + 1, 0x60FFFFFF);
        int ty = y + 4;

        // carta elegida
        Component cardName;
        if (s.card().isEmpty()) {
            cardName = Component.translatable("hud.dexrider.no_card");
        } else {
            var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(s.card()));
            cardName = new ItemStack(item).getHoverName();
        }
        int deck = Math.floorMod(s.slot() / 18, DECKS.length);
        g.fill(x + 5, ty, x + 11, ty + 8, deckColor(deck));
        g.text(font, Component.literal(clip(font, cardName.getString(), w - 18)), x + 14, ty, 0xFFFFFFFF, true);
        ty += 11;

        // R
        boolean ready = s.abilityLeft() <= 0;
        g.text(font, Component.literal("R"), x + 5, ty, ready ? 0xFF7CFF8A : 0xFFAAAAAA, true);
        bar(g, x + 14, ty + 2, w - 18, ready ? 1.0F : 1.0F - s.abilityLeft() / (float) s.abilityTotal(),
                ready ? 0xFF3FD27A : 0xFF8A8A9A);
        if (!ready) g.text(font, Component.literal(String.format("%.1f", s.abilityLeft() / 20.0)), x + w - 24, ty, 0xFFFFFFFF, true);
        ty += 11;

        // Accel
        if (s.accelLeft() > 0) {
            boolean warn = s.accelLeft() <= 60;
            g.text(font, Component.literal("A"), x + 5, ty, 0xFFFF463C, true);
            bar(g, x + 14, ty + 2, w - 18, s.accelLeft() / 200.0F, warn && (mc.player.tickCount / 4) % 2 == 0 ? 0xFFFFFFFF : 0xFFFF463C);
            g.text(font, Component.literal(String.valueOf((s.accelLeft() + 19) / 20)), x + w - 14, ty, 0xFFFFFFFF, true);
            ty += 11;
        }

        // Emotion
        if (!s.emotion().isEmpty()) {
            boolean violent = "violent".equals(s.emotion());
            int c = violent ? 0xFFB44CFF : 0xFF7FB8FF;
            g.text(font, Component.literal("E"), x + 5, ty, c, true);
            bar(g, x + 14, ty + 2, w - 18, s.emotionLeft() / 400.0F, c);
        } else if (s.emotionCooldown() > 0) {
            g.text(font, Component.literal("E"), x + 5, ty, 0xFF666670, true);
            bar(g, x + 14, ty + 2, w - 18, 1.0F - s.emotionCooldown() / 1800.0F, 0xFF55555F);
        }
    }

    /**
     * Lectura del Driver, a la derecha de la barra de objetos: nombre y vida del objetivo, tu golpe (y cuantos faltan)
     * y su golpe (y cuantos aguantas). Valores en corazones.
     */
    private static void drawTarget(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        var s = com.draxer.dexrider.network.TargetSyncPayload.last;
        if (mc.player == null || mc.level == null || !s.active() || !DexConfig.hud() || !DexConfig.targetRead()) return;
        if (System.currentTimeMillis() - com.draxer.dexrider.network.TargetSyncPayload.receivedAt > 3000) return;
        var entity = mc.level.getEntity(s.entityId());
        Font font = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        int w = 136;
        int rows = 2 + (s.dealt() > 0 ? 1 : 0) + (s.taken() > 0 ? 1 : 0);
        int h = 6 + rows * 11;
        int x = sw / 2 + 91 + 8, y = sh - h - 2;
        if (x + w > sw - 2) { x = sw - w - 2; y = sh - 22 - h - 14; }
        g.fill(x, y, x + w, y + h, 0xB0101018);
        g.fill(x + w - 2, y, x + w, y + h, 0xFFD4202C);
        g.fill(x, y, x + w, y + 1, 0x60FFFFFF);
        int ty = y + 4;
        Component name = entity != null ? entity.getDisplayName() : Component.translatable("hud.dexrider.target");
        g.text(font, Component.literal(clip(font, name.getString(), w - 10)), x + 4, ty, 0xFFFFFFFF, true);
        ty += 11;
        float t = s.maxHp() > 0 ? s.hp() / s.maxHp() : 0;
        int col = t > 0.5F ? 0xFF3FD27A : t > 0.25F ? 0xFFF2C230 : 0xFFE8343C;
        bar(g, x + 4, ty + 2, w - 52, t, col);
        g.text(font, Component.literal(hearts(s.hp()) + "/" + hearts(s.maxHp()) + "\u2764"), x + w - 46, ty, 0xFFFFFFFF, true);
        ty += 11;
        if (s.dealt() > 0) {
            Component line = s.hits() > 0
                    ? Component.translatable("hud.dexrider.you_hit_left", hearts(s.dealt()), s.hits())
                    : Component.translatable("hud.dexrider.you_hit", hearts(s.dealt()));
            g.text(font, Component.literal(clip(font, line.getString(), w - 8)), x + 4, ty, 0xFF9FE8FF, true);
            ty += 11;
        }
        if (s.taken() > 0) {
            Component line = s.survive() > 0
                    ? Component.translatable("hud.dexrider.it_hits_survive", hearts(s.taken()), s.survive())
                    : Component.translatable("hud.dexrider.it_hits", hearts(s.taken()));
            g.text(font, Component.literal(clip(font, line.getString(), w - 8)), x + 4, ty, s.survive() > 0 && s.survive() <= 2 ? 0xFFFF6A6A : 0xFFFFC8A0, true);
        }
    }

    /** Puntos de vida a corazones, con medio corazon de precision ("3.5"). */
    private static String hearts(float hp) {
        float h = Math.round(hp) / 2.0F;
        return h == Math.floor(h) ? String.valueOf((int) h) : String.format(java.util.Locale.ROOT, "%.1f", h);
    }

    private static void bar(GuiGraphicsExtractor g, int x, int y, int w, float t, int color) {
        t = Math.max(0, Math.min(1, t));
        g.fill(x, y, x + w, y + 4, 0xFF26262E);
        g.fill(x, y, x + Math.round(w * t), y + 4, color);
        g.fill(x, y, x + Math.round(w * t), y + 1, 0x50FFFFFF);
    }

    private static int deckColor(int deck) {
        return switch (deck) {
            case 0 -> 0xFF3FD27A;
            case 1 -> 0xFFF2C230;
            case 2 -> 0xFFD4202C;
            case 3 -> 0xFF4FA8FF;
            default -> 0xFFFF2BD6;
        };
    }

    private static String clip(Font font, String s, int max) {
        if (font.width(s) <= max) return s;
        while (s.length() > 1 && font.width(s + "…") > max) s = s.substring(0, s.length() - 1);
        return s + "…";
    }
}
