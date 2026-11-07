package com.draxer.dexrider.client;

import com.draxer.dexrider.DexRider;
import com.draxer.dexrider.item.DexCard;
import com.draxer.dexrider.item.DexDriverItem;
import com.draxer.dexrider.item.DriverDeck;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Aviso de escaneo arriba de la pantalla: cuando el Driver suma una muestra, genera una carta o la sube de nivel,
 * se ve unos segundos la carta (en silueta hasta completarla) con su barra de muestras.
 * Lo calcula el cliente comparando el mazo del Driver de un tick al siguiente.
 */
@EventBusSubscriber(modid = DexRider.MODID, value = Dist.CLIENT)
public final class ScanToast {
    private ScanToast() {}

    private static final int SHOW = 70;
    private static DriverDeck last;
    private static DexCard card;
    private static int samples, needed, level, timer;
    private static boolean complete;

    /** Para la reticula del visor: carta que se esta escaneando (null si no hay aviso), y su avance. */
    static DexCard scanning() { return timer > 0 && !complete ? card : null; }
    static float scanProgress() { return needed <= 0 ? 0 : samples / (float) needed; }
    static boolean justCompleted() { return timer > 0 && complete; }
    static DexCard lastCard() { return card; }

    @SubscribeEvent
    static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, DexRider.id("scan_toast"), ScanToast::draw);
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        var p = Minecraft.getInstance().player;
        if (timer > 0) timer--;
        if (p == null) { last = null; return; }
        ItemStack driver = DexDriverItem.findDriver(p);
        DriverDeck now = driver.isEmpty() ? null : DexDriverItem.getDeck(driver);
        if (now == null || last == null) { last = now; return; }
        if (!now.equals(last)) {
            for (DexCard c : DexCard.values()) {
                boolean newCard = now.has(c) && !last.has(c);
                boolean newLevel = now.level(c) > last.level(c) && last.has(c);
                boolean newSample = now.samples(c) > last.samples(c);
                if (!newCard && !newLevel && !newSample) continue;
                card = c;
                complete = newCard || newLevel;
                level = now.level(c);
                samples = complete ? 1 : now.samples(c);
                needed = complete ? 1 : now.has(c) ? now.samplesForNextLevel(c) : c.samplesToScan();
                timer = SHOW;
                break;
            }
        }
        last = now;
    }

    private static void draw(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (timer <= 0 || card == null || mc.player == null) return;
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        int w = 150, h = 30;
        if (!HudLayout.begin(g, "toast", w, h, sw, sh)) return;
        float slide = Math.min(1F, (SHOW - timer) / 6F) * Math.min(1F, timer / 8F);
        int x = 0, y = (int) (-(1F - slide) * 12);
        drawBody(g, mc, x, y, w, h);
        HudLayout.end(g);
    }

    private static void drawBody(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xD0101018);
        g.fill(x, y, x + 2, y + h, 0xFFFF2BD6);
        g.blit(RenderPipelines.GUI_TEXTURED, DexRider.id("textures/item/" + card.itemName() + ".png"), x + 5, y + 3, 0, 0, 24, 24, 24, 24,
                complete ? 0xFFFFFFFF : 0xFF2A2A36);
        String name = Component.translatable(card.translationKey()).getString();
        int dash = name.lastIndexOf(" - ");                    // "Carta: Form Ride - Sky" -> "Form: Sky"
        if (dash >= 0) name = Component.translatable("screen.dexrider.archive.tab." + card.family().name().toLowerCase(java.util.Locale.ROOT))
                .getString() + ": " + name.substring(dash + 3);
        while (mc.font.width(name) > w - 40 && name.length() > 3) name = name.substring(0, name.length() - 1);
        g.text(mc.font, Component.literal(name), x + 34, y + 5, 0xFFFFFFFF, true);
        Component sub = complete
                ? (level > 1 ? Component.translatable("hud.dexrider.scan_level", level) : Component.translatable("screen.dexrider.archive.owned"))
                : Component.translatable("hud.dexrider.scan_toast").append(" " + samples + "/" + needed);
        g.text(mc.font, sub, x + 34, y + 16, complete ? 0xFF3FD27A : 0xFFFF8AE6, true);
        int bx = x + 34 + mc.font.width(sub) + 6, bw = x + w - 6 - bx;
        if (!complete && bw > 10) {
            g.fill(bx, y + 18, bx + bw, y + 22, 0xFF2A2A36);
            g.fill(bx, y + 18, bx + Math.round(bw * samples / (float) Math.max(1, needed)), y + 22, 0xFFFF2BD6);
        }
    }
}
