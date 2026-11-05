package com.draxer.dexrider.client;

import com.draxer.dexrider.network.MissionSyncPayload;
import com.draxer.dexrider.rider.Missions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Registro de las misiones de los Kagami (tecla J, o el boton del Archivo del Driver): las cumplidas, la actual con su
 * carta, objetivo y recompensa, y las que vienen (ocultas).
 */
public class MissionsScreen extends Screen {

    private static final int W = 400, H = 232;
    private int left, top, selected = -1;
    private final List<Button> rows = new ArrayList<>();

    public MissionsScreen() {
        super(Component.translatable("screen.dexrider.missions"));
    }

    private static int done() {
        return Math.max(0, Math.min(Missions.ALL.size(), MissionSyncPayload.clientDone));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        clearWidgets();
        rows.clear();
        int d = done();
        if (selected < 0) selected = Math.min(d, Missions.ALL.size() - 1);
        for (int i = 0; i < Missions.ALL.size(); i++) {
            final int idx = i;
            Component label = i <= d
                    ? Component.literal((i < d ? "✔ " : "➤ ")).append(Component.translatable("mission.dexrider." + Missions.ALL.get(i).id() + ".title"))
                    : Component.translatable("screen.dexrider.missions.locked");
            Button b = addRenderableWidget(Button.builder(label, x -> { selected = idx; init(); })
                    .bounds(left + 6, top + 22 + i * 15, 150, 14).build());
            b.active = i <= d && i != selected;
            rows.add(b);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(left - 2, top - 2, left + W + 2, top + H + 2, 0xFF0E0E14);
        g.fill(left, top, left + W, top + H, 0xFF1E1A16);
        g.fill(left, top, left + W, top + 2, 0xFFF2C230);
        g.text(font, title, left + 8, top + 7, 0xFFF2C230, true);
        int d = done(), n = Missions.ALL.size();
        Component count = Component.translatable("screen.dexrider.missions.count", d, n);
        g.text(font, count, left + W - 8 - font.width(count), top + 7, 0xFFE8E8EE, true);
        g.fill(left + 162, top + 22, left + W - 6, top + H - 6, 0xFF15130F);
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        if (selected < 0 || selected > d || selected >= n) return;
        Missions.Mission m = Missions.ALL.get(selected);
        String k = "mission.dexrider." + m.id();
        int x = left + 168, w = W - 6 - 168 - 6, y = top + 28;
        g.text(font, Component.translatable(k + ".title"), x, y, 0xFFF2C230, true); y += 12;
        g.text(font, Component.translatable("screen.dexrider.missions.from",
                Component.translatable("mission.dexrider.giver." + m.giver())), x, y, 0xFFB8BCC8, false); y += 14;
        for (String part : new String[]{".letter1", ".letter2"}) {
            for (FormattedCharSequence line : font.split(Component.translatable(k + part), w)) {
                g.text(font, line, x, y, 0xFFD8D2C4, false); y += 9;
            }
            y += 3;
        }
        y += 4;
        boolean isDone = selected < d;
        g.text(font, isDone ? Component.translatable("screen.dexrider.missions.done") : Component.translatable("screen.dexrider.missions.goal"),
                x, y, isDone ? 0xFF3FD27A : 0xFFFFD84A, false); y += 10;
        for (FormattedCharSequence line : font.split(Component.translatable(k + ".goal"), w)) {
            g.text(font, line, x, y, 0xFFFFFFFF, false); y += 9;
        }
        y += 6;
        g.text(font, Component.translatable("screen.dexrider.missions.reward"), x, y, 0xFF7FD8FF, false); y += 10;
        for (FormattedCharSequence line : font.split(Missions.rewardText(m), w)) {
            if (y > top + H - 14) break;
            g.text(font, line, x, y, 0xFFE8E8EE, false); y += 9;
        }
        if (d >= n) g.text(font, Component.translatable("screen.dexrider.missions.all"), x, top + H - 18, 0xFF3FD27A, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
