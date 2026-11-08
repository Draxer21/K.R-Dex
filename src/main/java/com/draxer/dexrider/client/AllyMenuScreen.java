package com.draxer.dexrider.client;

import com.draxer.dexrider.item.AllySkills;
import com.draxer.dexrider.network.AllyActionPayload;
import com.draxer.dexrider.network.AllyMenuPayload;
import com.draxer.dexrider.rider.KamenRide;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Menu de un amigo Rider (clic derecho con la mano vacia): charlar, acompaniarte o volver a casa, vivir aqui,
 * encargarle un trabajo, zona y filtro, y recoger lo que lleva. A la izquierda, lo que sabe y lo que hace.
 */
public class AllyMenuScreen extends Screen {

    private static final int W = 320, H = 254;
    private static final String[] SKILLS = {"mine", "chop", "farm", "craft"};
    private AllyMenuPayload data;
    private int left, top;

    public AllyMenuScreen(AllyMenuPayload data) {
        super(Component.translatable("screen.dexrider.ally_menu"));
        this.data = data;
    }

    /** Llega del servidor: abre el menu o lo refresca si ya esta abierto para el mismo amigo. */
    public static void receive(AllyMenuPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof AllyMenuScreen s && s.data.entityId() == p.entityId()) {
            s.data = p;
            s.init();
        } else {
            mc.gui.setScreen(new AllyMenuScreen(p));
        }
    }

    private KamenRide rider() {
        KamenRide r = KamenRide.byId(data.rider());
        return r == null ? KamenRide.DEX : r;
    }

    private int xp(String skill) {
        return switch (skill) {
            case "mine" -> data.mine();
            case "chop" -> data.chop();
            case "farm" -> data.farm();
            default -> data.craft();
        };
    }

    private void send(int action) {
        ClientPacketDistributor.sendToServer(new AllyActionPayload(data.entityId(), action));
        if (action == AllyActionPayload.TOGGLE || action == AllyActionPayload.ZONE_MARK) onClose();
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        clearWidgets();
        int bx = left + 168, bw = 70, gap = 4, y = top + 24;
        addRenderableWidget(Button.builder(Component.translatable("screen.dexrider.ally_menu.chat"), b -> send(AllyActionPayload.CHAT))
                .bounds(bx, y, bw, 16).build());
        addRenderableWidget(Button.builder(Component.translatable(data.summoned() ? "screen.dexrider.ally_menu.go_home" : "screen.dexrider.ally_menu.follow"),
                b -> send(AllyActionPayload.TOGGLE)).bounds(bx + bw + gap, y, bw, 16).build());
        y += 20;
        addRenderableWidget(Button.builder(Component.translatable("screen.dexrider.ally_menu.live_here"), b -> send(AllyActionPayload.LIVE_HERE))
                .bounds(bx, y, bw * 2 + gap, 16).build());

        // trabajos
        y += 26;
        String[] jobs = {"mine", "chop", "farm", "craft", "flatten", "fish", "breed", "smelt", "guard"};
        int[] acts = {AllyActionPayload.JOB_MINE, AllyActionPayload.JOB_CHOP, AllyActionPayload.JOB_FARM, AllyActionPayload.JOB_CRAFT, AllyActionPayload.JOB_FLATTEN,
                AllyActionPayload.JOB_FISH, AllyActionPayload.JOB_BREED, AllyActionPayload.JOB_SMELT, AllyActionPayload.JOB_GUARD};
        for (int i = 0; i < jobs.length; i++) {
            String skill = jobs[i].equals("flatten") ? "mine" : jobs[i];
            int x = bx + (i % 2) * (bw + gap), yy = y + (i / 2) * 20;
            final int act = acts[i];
            Button b = addRenderableWidget(Button.builder(cap(Component.translatable("job.dexrider." + jobs[i])), c -> send(act))
                    .bounds(x, yy, bw, 16).build());
            boolean chore = i >= 5;      // quehaceres: no se aprenden
            boolean knows = chore || xp(skill) >= AllySkills.LEARNED;
            boolean current = jobs[i].equals(data.job());
            b.active = knows && !current;
            if (!knows) {
                int pct = Math.min(99, xp(skill) * 100 / AllySkills.LEARNED);
                b.setTooltip(Tooltip.create(Component.translatable("screen.dexrider.ally_menu.not_yet", pct)));
            } else if (chore) {
                b.setTooltip(Tooltip.create(Component.translatable("screen.dexrider.ally_menu.chore." + jobs[i])));
            } else if (jobs[i].equals("flatten") && data.zoneX() == 0) {
                b.setTooltip(Tooltip.create(Component.translatable("screen.dexrider.ally_menu.needs_zone")));
            }
        }
        Button stop = addRenderableWidget(Button.builder(Component.translatable("screen.dexrider.ally_menu.rest"), c -> send(AllyActionPayload.JOB_STOP))
                .bounds(bx + bw + gap, y + 80, bw, 16).build());
        stop.active = !data.job().isEmpty();

        // zona y filtro
        y += 106;
        addRenderableWidget(Button.builder(Component.translatable("screen.dexrider.ally_menu.zone_mark"), c -> send(AllyActionPayload.ZONE_MARK))
                .bounds(bx, y, bw, 16).build());
        Button zc = addRenderableWidget(Button.builder(Component.translatable("screen.dexrider.ally_menu.zone_clear"), c -> send(AllyActionPayload.ZONE_CLEAR))
                .bounds(bx + bw + gap, y, bw, 16).build());
        zc.active = data.zoneX() > 0;
        y += 20;
        Button fh = addRenderableWidget(Button.builder(Component.translatable("screen.dexrider.ally_menu.filter_hand"), c -> send(AllyActionPayload.FILTER_HAND))
                .bounds(bx, y, bw, 16).build());
        fh.setTooltip(Tooltip.create(Component.translatable("screen.dexrider.ally_menu.filter_hand.tip")));
        Button fc = addRenderableWidget(Button.builder(Component.translatable("screen.dexrider.ally_menu.filter_clear"), c -> send(AllyActionPayload.FILTER_CLEAR))
                .bounds(bx + bw + gap, y, bw, 16).build());
        fc.active = !data.filter().isEmpty();
        y += 20;
        Button bag = addRenderableWidget(Button.builder(Component.translatable("screen.dexrider.ally_menu.take_bag", data.bag()), c -> send(AllyActionPayload.TAKE_BAG))
                .bounds(bx, y, bw * 2 + gap, 16).build());
        bag.active = data.bag() > 0;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int c = rider().color;
        int lum = ((c >> 16) & 0xFF) * 3 + ((c >> 8) & 0xFF) * 6 + (c & 0xFF);
        int accent = lum < 900 ? 0xFFF2C230 : 0xFF000000 | c;     // los colores muy oscuros no se leen
        g.fill(left - 2, top - 2, left + W + 2, top + H + 2, 0xFF0E0E14);
        g.fill(left, top, left + W, top + H, 0xFF1A1A22);
        g.fill(left, top, left + W, top + 2, accent);
        g.text(font, rider().displayName(), left + 8, top + 7, accent, true);
        // confianza, al lado del nombre
        Component trust = Component.translatable("screen.dexrider.ally_talk.trust", com.draxer.dexrider.rider.AllyTrust.levelName(data.trust()));
        int tx = left + 14 + font.width(rider().displayName());
        g.text(font, trust, tx, top + 7, 0xFFE8608A, false);
        Component mode = Component.translatable(data.summoned() ? "screen.dexrider.ally_menu.with_you"
                : data.hasHome() ? "screen.dexrider.ally_menu.at_home" : "screen.dexrider.ally_menu.no_home");
        g.text(font, mode, left + W - 8 - font.width(mode), top + 7, 0xFFB8BCC8, false);

        // lo que sabe
        int x = left + 8, y = top + 24;
        g.fill(left + 4, top + 20, left + 160, top + H - 4, 0xFF13131A);
        g.text(font, Component.translatable("screen.dexrider.ally_menu.skills"), x, y, 0xFFF2C230, false);
        y += 13;
        for (String s : SKILLS) {
            int v = xp(s);
            boolean knows = v >= AllySkills.LEARNED;
            g.text(font, cap(Component.translatable("job.dexrider." + s)), x, y, 0xFFE8E8EE, false);
            int bx = x + 56, bw = 92;
            g.fill(bx, y + 1, bx + bw, y + 8, 0xFF2A2A34);
            float f = knows ? (v - AllySkills.LEARNED) / (float) (AllySkills.MASTER - AllySkills.LEARNED) : v / (float) AllySkills.LEARNED;
            g.fill(bx, y + 1, bx + Math.round(bw * Math.min(1.0F, f)), y + 8, knows ? 0xFF3FD27A : 0xFF7FA8FF);
            Component tag = knows ? Component.translatable(v >= AllySkills.MASTER ? "screen.dexrider.ally_menu.master" : "screen.dexrider.ally_menu.knows")
                    : Component.literal(Math.min(99, v * 100 / AllySkills.LEARNED) + "%");
            g.text(font, tag, bx + bw / 2 - font.width(tag) / 2, y + 1, 0xFFFFFFFF, false);
            y += 13;
        }
        y += 6;
        Component job = data.job().isEmpty() ? Component.translatable("screen.dexrider.ally_menu.free") : cap(Component.translatable("job.dexrider." + data.job()));
        line(g, x, y, "screen.dexrider.ally_menu.job", job); y += 12;
        line(g, x, y, "screen.dexrider.ally_menu.filter",
                data.filter().isEmpty() ? Component.translatable("screen.dexrider.ally_menu.none") : Component.literal(data.filter())); y += 12;
        line(g, x, y, "screen.dexrider.ally_menu.zone", data.zoneX() == 0 ? Component.translatable("screen.dexrider.ally_menu.none")
                : Component.literal(data.zoneX() + "×" + data.zoneY() + "×" + data.zoneZ())); y += 12;
        line(g, x, y, "screen.dexrider.ally_menu.carrying", Component.literal(String.valueOf(data.bag()))); y += 12;
        line(g, x, y, "screen.dexrider.ally_menu.recipes", Component.literal(String.valueOf(data.recipes()))); y += 16;
        for (var l : font.split(Component.translatable("screen.dexrider.ally_menu.hint"), 148)) {
            if (y > top + H - 12) break;
            g.text(font, l, x, y, 0xFF8A8E9A, false);
            y += 9;
        }

        // titulos de la columna de botones
        g.text(font, Component.translatable("screen.dexrider.ally_menu.work"), left + 168, top + 60, 0xFFF2C230, false);
        g.text(font, Component.translatable("screen.dexrider.ally_menu.zone_filter"), left + 168, top + 166, 0xFFF2C230, false);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    /** "picar" -> "Picar". */
    private static Component cap(Component c) {
        String t = c.getString();
        return t.isEmpty() ? c : Component.literal(Character.toUpperCase(t.charAt(0)) + t.substring(1));
    }

    private void line(GuiGraphicsExtractor g, int x, int y, String key, Component value) {
        Component label = Component.translatable(key);
        g.text(font, label, x, y, 0xFFB8BCC8, false);
        g.text(font, value, x + font.width(label) + 4, y, 0xFFFFFFFF, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
