package com.draxer.dexrider.client;

import com.draxer.dexrider.network.AllyActionPayload;
import com.draxer.dexrider.network.AllyTalkPayload;
import com.draxer.dexrider.rider.AllyTrust;
import com.draxer.dexrider.rider.KamenRide;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Conversacion con un amigo Rider: lo que acaba de decir, la confianza y los temas. Con Buen amigo se abre un tema de
 * su historia y con Confianza total, algo personal. Tambien se le puede regalar lo que tienes en la mano.
 */
public class AllyTalkScreen extends Screen {

    private static final int W = 320, H = 196;
    private static final String[] TOPICS = {"how", "about", "advice", "deep", "secret"};
    private AllyTalkPayload data;
    private int left, top;

    public AllyTalkScreen(AllyTalkPayload data) {
        super(Component.translatable("screen.dexrider.ally_talk"));
        this.data = data;
    }

    public static void receive(AllyTalkPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof AllyTalkScreen s && s.data.entityId() == p.entityId()) {
            s.data = p;
            s.init();
        } else {
            mc.gui.setScreen(new AllyTalkScreen(p));
        }
    }

    private KamenRide rider() {
        KamenRide r = KamenRide.byId(data.rider());
        return r == null ? KamenRide.DEX : r;
    }

    private void send(int action) {
        ClientPacketDistributor.sendToServer(new AllyActionPayload(data.entityId(), action));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        clearWidgets();
        int bw = 148, x0 = left + 8, x1 = left + 8 + bw + 8, y = top + 104;
        for (int i = 0; i < TOPICS.length; i++) {
            final int topic = i;
            boolean open = (data.topics() & (1 << i)) != 0;
            Component label = open ? Component.translatable("screen.dexrider.ally_talk.topic." + TOPICS[i])
                    : Component.translatable("screen.dexrider.ally_talk.locked");
            Button b = addRenderableWidget(Button.builder(label, c -> send(AllyActionPayload.TOPIC_BASE + topic))
                    .bounds(i % 2 == 0 ? x0 : x1, y + (i / 2) * 20, bw, 16).build());
            b.active = open;
            if (!open) b.setTooltip(Tooltip.create(Component.translatable("screen.dexrider.ally_talk.needs",
                    Component.translatable("trust.dexrider.level" + (i == 3 ? 2 : 3)))));
        }
        Button gift = addRenderableWidget(Button.builder(Component.translatable("screen.dexrider.ally_talk.gift"), c -> send(AllyActionPayload.GIFT))
                .bounds(x1, y + 40, bw, 16).build());
        gift.setTooltip(Tooltip.create(Component.translatable("screen.dexrider.ally_talk.gift.tip")));
        addRenderableWidget(Button.builder(Component.translatable("screen.dexrider.ally_talk.back"), c -> send(AllyActionPayload.BACK))
                .bounds(x0, y + 64, bw * 2 + 8, 16).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int c = rider().color;
        int lum = ((c >> 16) & 0xFF) * 3 + ((c >> 8) & 0xFF) * 6 + (c & 0xFF);
        int accent = lum < 900 ? 0xFFF2C230 : 0xFF000000 | c;
        g.fill(left - 2, top - 2, left + W + 2, top + H + 2, 0xFF0E0E14);
        g.fill(left, top, left + W, top + H, 0xFF1A1A22);
        g.fill(left, top, left + W, top + 2, accent);
        g.text(font, rider().displayName(), left + 8, top + 7, accent, true);

        // confianza
        int trust = data.trust();
        Component lv = Component.translatable("screen.dexrider.ally_talk.trust", AllyTrust.levelName(trust));
        int bx = left + W - 8 - 90;
        g.text(font, lv, bx - 6 - font.width(lv), top + 7, 0xFFE8E8EE, false);
        g.fill(bx, top + 8, bx + 90, top + 15, 0xFF2A2A34);
        g.fill(bx, top + 8, bx + Math.round(90 * trust / 100.0F), top + 15, 0xFFE8608A);
        for (int t : AllyTrust.THRESHOLDS) if (t > 0) g.fill(bx + Math.round(90 * t / 100.0F), top + 8, bx + Math.round(90 * t / 100.0F) + 1, top + 15, 0xFF0E0E14);

        // lo que dice
        g.fill(left + 6, top + 22, left + W - 6, top + 98, 0xFF13131A);
        int y = top + 28;
        for (FormattedCharSequence line : font.split(Component.translatable(data.lineKey()), W - 24)) {
            if (y > top + 90) break;
            g.text(font, line, left + 12, y, 0xFFFFFFFF, false);
            y += 10;
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
