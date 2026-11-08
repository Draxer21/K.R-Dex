package com.draxer.dexrider.client;

import com.draxer.dexrider.item.SuitLook;
import com.draxer.dexrider.item.SuitLooks;
import com.draxer.dexrider.item.SuitProfile;
import com.draxer.dexrider.item.SuitSlots;
import com.draxer.dexrider.network.SuitPayload;
import com.draxer.dexrider.registry.DexComponents;
import com.draxer.dexrider.registry.DexItems;
import com.draxer.dexrider.rider.DexEmotions;
import com.draxer.dexrider.rider.FormRide;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Editor del traje de Dex (Shift + clic derecho con el Dex Driver en la mano).
 *
 *  - Estados: Base, Violent, Heroic, Upgrade y cada Form Ride. Cada uno hereda de la base lo que no cambies.
 *  - Colores: casco, pecho, brazos, piernas, botas, acentos, ojos, plata, negro, cristal y bufanda
 *    (en Half-Cold Half-Hot, las dos mitades). Paleta, barras R G B y codigo hexadecimal.
 *  - Piezas: cuernos, visor, hombreras, bufanda, cresta, Booker, cristal, puas, alas, brillo y acabado.
 *  - Disenos: 5 ranuras con nombre, y un codigo para copiar y pegar (compartir con otros jugadores).
 *  - Maniqui 3D que gira (rueda del raton encima, o las flechas).
 */
public class SuitScreen extends Screen {

    private enum Section { COLORS, PARTS, DESIGNS }

    private static final int W = 400, H = 230;
    private static final List<Integer> PALETTE = List.of(
            0xCD1C28, 0xF05A14, 0xE8B630, 0xF5E642,
            0x28D250, 0x1EA0A0, 0x1E69EB, 0x3C2896,
            0x9632C8, 0xE650A0, 0xFFFFFF, 0xB8BCC8,
            0x51545D, 0x1C1C24, 0x6B3A1E, 0x5A1430);

    private SuitSlots slots;
    private String state = SuitProfile.BASE;
    private FormRide form = FormRide.SPEED;
    private Section section = Section.COLORS;
    private String zone = SuitLook.HELMET;
    private int left, top;
    private float yaw = 200F;
    private boolean spin = true;
    private Component status = Component.empty();

    private ChannelSlider red, green, blue;
    private EditBox hex, nameBox;
    private final List<Button> zoneButtons = new ArrayList<>();
    private final List<String> zoneKeys = new ArrayList<>();
    private final List<Button> swatches = new ArrayList<>();
    private ArmorStand stand;

    public SuitScreen(SuitSlots initial) {
        super(Component.translatable("screen.dexrider.suit"));
        this.slots = (initial == null ? SuitSlots.DEFAULT : initial).normalized();
    }

    private SuitProfile profile() { return slots.current(); }

    private void setProfile(SuitProfile p) { slots = slots.with(slots.active(), p); }

    private SuitLook own() { return profile().look(state); }

    private void setOwn(SuitLook l) { setProfile(profile().withLook(state, l)); }

    private SuitLooks.Resolved resolved() { return SuitLooks.resolve(profile(), state); }

    // ------------------------------------------------------------------ armado de la pantalla
    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        clearWidgets();
        zoneButtons.clear(); zoneKeys.clear(); swatches.clear();
        red = green = blue = null; hex = nameBox = null;

        // estados
        String[] st = {SuitProfile.BASE, SuitProfile.VIOLENT, SuitProfile.HEROIC, SuitProfile.UPGRADE};
        for (int i = 0; i < st.length; i++) {
            final String s = st[i];
            Button b = addRenderableWidget(Button.builder(Component.translatable("screen.dexrider.suit.state." + s), x -> { state = s; fixZone(); init(); })
                    .bounds(left + 104 + i * 59, top + 18, 57, 16).build());
            b.active = !state.equals(s);
        }
        boolean onForm = state.startsWith("form_");
        addRenderableWidget(Button.builder(Component.translatable("form.dexrider." + form.id()).append(onForm ? " >" : ""), x -> {
            if (state.startsWith("form_")) form = FormRide.values()[(form.ordinal() + 1) % FormRide.values().length];
            state = SuitProfile.form(form);
            fixZone();
            init();
        }).bounds(left + 340, top + 18, 56, 16).tooltip(Tooltip.create(Component.translatable("screen.dexrider.suit.state.form.tip"))).build());

        // secciones
        Section[] secs = Section.values();
        for (int i = 0; i < secs.length; i++) {
            final Section sc = secs[i];
            Button b = addRenderableWidget(Button.builder(Component.translatable("screen.dexrider.suit.section." + sc.name().toLowerCase(Locale.ROOT)),
                    x -> { section = sc; init(); }).bounds(left + 104 + i * 66, top + 38, 64, 16).build());
            b.active = section != sc;
        }

        switch (section) {
            case COLORS -> initColors();
            case PARTS -> initParts();
            case DESIGNS -> initDesigns();
        }

        // maniqui: girar
        addRenderableWidget(Button.builder(Component.literal("<"), b -> { spin = false; yaw -= 45; }).bounds(left + 6, top + 206, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal("||"), b -> spin = !spin).bounds(left + 40, top + 206, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { spin = false; yaw += 45; }).bounds(left + 76, top + 206, 20, 16).build());

        // guardar / cancelar / restablecer estado
        addRenderableWidget(Button.builder(Component.translatable("screen.dexrider.suit.save"), b -> {
            ClientPacketDistributor.sendToServer(new SuitPayload(slots));
            onClose();
        }).bounds(left + 104, top + 208, 94, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(left + 202, top + 208, 94, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.dexrider.suit.reset_state"), b -> { setOwn(SuitLook.EMPTY); init(); })
                .bounds(left + 300, top + 208, 96, 18)
                .tooltip(Tooltip.create(Component.translatable("screen.dexrider.suit.reset_state.tip"))).build());
    }

    private List<String> zonesFor(String st) {
        List<String> z = new ArrayList<>();
        if (st.equals(SuitProfile.form(FormRide.HALF))) z.addAll(List.of(SuitLook.HALF_RIGHT, SuitLook.HALF_LEFT));
        else z.addAll(SuitLooks.MAIN_ZONES);
        z.addAll(List.of(SuitLook.ACCENT, SuitLook.EYES, SuitLook.SILVER, SuitLook.INNER, SuitLook.CRYSTAL, SuitLook.SCARF));
        if (!st.equals(SuitProfile.UPGRADE)) z.add(SuitLook.VISOR);
        return z;
    }

    private void fixZone() {
        if (!zonesFor(state).contains(zone)) zone = zonesFor(state).get(0);
    }

    private void initColors() {
        fixZone();
        List<String> zones = zonesFor(state);
        for (int i = 0; i < zones.size(); i++) {
            final String z = zones.get(i);
            Button b = addRenderableWidget(Button.builder(Component.translatable("screen.dexrider.suit.zone." + z), x -> { zone = z; init(); })
                    .bounds(left + 104 + (i % 2) * 72, top + 58 + (i / 2) * 16, 70, 14).build());
            b.active = !z.equals(zone);
            zoneButtons.add(b); zoneKeys.add(z);
        }
        for (int i = 0; i < PALETTE.size(); i++) {
            final int c = PALETTE.get(i);
            swatches.add(addRenderableWidget(Button.builder(Component.empty(), x -> setColor(c))
                    .bounds(left + 252 + (i % 4) * 18, top + 58 + (i / 4) * 18, 16, 16).build()));
        }
        hex = addRenderableWidget(new EditBox(font, left + 330, top + 94, 66, 16, Component.literal("hex")));
        hex.setMaxLength(7);
        hex.setValue(String.format("#%06X", currentColor()));
        hex.setResponder(v -> {
            String t = v.startsWith("#") ? v.substring(1) : v;
            if (t.length() == 6) try { setColor(Integer.parseInt(t, 16), false); } catch (NumberFormatException ignored) {}
        });
        Button def = addRenderableWidget(Button.builder(Component.translatable("screen.dexrider.suit.zone_default"), x -> {
            setOwn(own().withoutColor(zone)); init();
        }).bounds(left + 330, top + 114, 66, 16).tooltip(Tooltip.create(Component.translatable("screen.dexrider.suit.zone_default.tip"))).build());
        def.active = own().colors().containsKey(zone);
        red = addRenderableWidget(new ChannelSlider(left + 104, top + 154, 16));
        green = addRenderableWidget(new ChannelSlider(left + 104, top + 171, 8));
        blue = addRenderableWidget(new ChannelSlider(left + 104, top + 188, 0));
        syncSliders();
    }

    private void initParts() {
        boolean upgrade = state.equals(SuitProfile.UPGRADE);
        List<String> keys = new ArrayList<>();
        for (String k : SuitLook.PART_VALUES.keySet()) {
            if (upgrade) {
                if (!(k.startsWith("glow_") || k.equals("finish") || SuitLook.upgradePart(k))) continue;
            } else {
                if (SuitLook.upgradePart(k) || k.equals("stripes")) continue;      // las rayas ahora son "lineas del visor"
                String f = SuitLook.formOfPart(k);
                if (f != null && !state.equals("form_" + f)) continue;
                if ((k.equals("visor_thick") || k.equals("visor_gap")) && !patterned()) continue;
            }
            keys.add(k);
        }
        SuitLooks.Resolved r = resolved();
        for (int i = 0; i < keys.size(); i++) {
            final String k = keys.get(i);
            List<String> vals = SuitLook.PART_VALUES.get(k);
            String cur = r.part(k);
            boolean changed = own().parts().containsKey(k);
            Component label = Component.translatable("screen.dexrider.suit.part." + k)
                    .append(": ").append(Component.translatable("screen.dexrider.suit.val." + cur)).append(changed ? " *" : "");
            addRenderableWidget(Button.builder(label, x -> {
                String next = vals.get((vals.indexOf(r.part(k)) + 1) % vals.size());
                setOwn(own().withPart(k, next));
                init();
            }).bounds(left + 104 + (i % 2) * 148, top + 58 + (i / 2) * 16, 144, 14).build());
        }
        if (upgrade) status = Component.translatable("screen.dexrider.suit.upgrade_note");
    }

    /** Grosor y separacion solo valen para los patrones nuevos del visor. */
    private boolean patterned() {
        String v = resolved().part("visor_lines");
        return !v.equals("classic") && !v.equals("none");
    }

    private void initDesigns() {
        for (int i = 0; i < SuitSlots.COUNT; i++) {
            final int idx = i;
            Button b = addRenderableWidget(Button.builder(Component.literal((i == slots.active() ? "> " : "") + slotName(i)), x -> {
                slots = slots.withActive(idx); status = Component.empty(); init();
            }).bounds(left + 104, top + 58 + i * 18, 140, 16).build());
            b.active = i != slots.active();
        }
        nameBox = addRenderableWidget(new EditBox(font, left + 252, top + 58, 144, 16, Component.translatable("screen.dexrider.suit.name")));
        nameBox.setMaxLength(SuitProfile.MAX_NAME);
        nameBox.setValue(profile().name());
        nameBox.setHint(Component.literal(slotName(slots.active())));
        nameBox.setResponder(v -> setProfile(profile().withName(v)));
        addRenderableWidget(Button.builder(Component.translatable("screen.dexrider.suit.copy_code"), x -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(profile().toCode());
            status = Component.translatable("screen.dexrider.suit.copied");
        }).bounds(left + 252, top + 80, 144, 16).tooltip(Tooltip.create(Component.translatable("screen.dexrider.suit.copy_code.tip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.dexrider.suit.paste_code"), x -> {
            SuitProfile p = SuitProfile.fromCode(Minecraft.getInstance().keyboardHandler.getClipboard());
            if (p == null) { status = Component.translatable("screen.dexrider.suit.bad_code"); return; }
            setProfile(p);
            status = Component.translatable("screen.dexrider.suit.pasted");
            init();
        }).bounds(left + 252, top + 98, 144, 16).tooltip(Tooltip.create(Component.translatable("screen.dexrider.suit.paste_code.tip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.dexrider.suit.reset_design"), x -> {
            setProfile(SuitProfile.DEFAULT.withName(profile().name()));
            status = Component.translatable("screen.dexrider.suit.reset_done");
            init();
        }).bounds(left + 252, top + 116, 144, 16).build());
    }

    private String slotName(int i) {
        String n = slots.normalized().slots().get(i).name();
        return n.isEmpty() ? Component.translatable("screen.dexrider.suit.slot", i + 1).getString() : n;
    }

    // ------------------------------------------------------------------ colores
    private int currentColor() { return resolved().color(zone); }

    private void setColor(int c) { setColor(c, true); }

    private void setColor(int c, boolean updateHex) {
        setOwn(own().withColor(zone, c));
        syncSliders();
        if (updateHex && hex != null) hex.setValue(String.format("#%06X", c & 0xFFFFFF));
    }

    private void syncSliders() {
        int c = currentColor();
        if (red != null) { red.setFromColor(c); green.setFromColor(c); blue.setFromColor(c); }
    }

    // ------------------------------------------------------------------ dibujo
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(left - 2, top - 2, left + W + 2, top + H + 2, 0xFF0E0E14);
        g.fill(left, top, left + W, top + H, 0xFF22222C);
        g.fill(left, top, left + W, top + 2, 0xFFCD1C28);
        g.text(font, title.copy().append(" - ").append(Component.literal(slotName(slots.active()))), left + 8, top + 6, 0xFFF2C230, true);
        g.fill(left + 4, top + 18, left + 98, top + 202, 0xFF15151C);
        drawMannequin(g, partialTick);

        super.extractRenderState(g, mouseX, mouseY, partialTick);

        if (section == Section.COLORS) {
            SuitLooks.Resolved r = resolved();
            for (int i = 0; i < zoneButtons.size(); i++) {
                Button b = zoneButtons.get(i);
                int x = b.getX() + b.getWidth() - 10, y = b.getY() + 3;
                g.fill(x - 1, y - 1, x + 8, y + 8, own().colors().containsKey(zoneKeys.get(i)) ? 0xFFF2C230 : 0xFF000000);
                g.fill(x, y, x + 7, y + 7, 0xFF000000 | r.color(zoneKeys.get(i)));
            }
            for (int i = 0; i < swatches.size(); i++) {
                Button b = swatches.get(i);
                g.fill(b.getX() + 2, b.getY() + 2, b.getX() + b.getWidth() - 2, b.getY() + b.getHeight() - 2, 0xFF000000 | PALETTE.get(i));
            }
            int c = currentColor();
            g.fill(left + 329, top + 57, left + 397, top + 91, 0xFFFFFFFF);
            g.fill(left + 330, top + 58, left + 396, top + 90, 0xFF000000 | c);
            Component zn = Component.translatable("screen.dexrider.suit.zone." + zone);
            g.text(font, zn, left + 330, top + 136, 0xFFE8E8EE, true);
        } else if (!status.getString().isEmpty()) {
            int y = section == Section.DESIGNS ? top + 140 : top + 190;
            g.text(font, status, left + (section == Section.DESIGNS ? 252 : 104), y, 0xFFB8F0C0, true);
        }
        Component st = Component.translatable("screen.dexrider.suit.editing",
                state.startsWith("form_") ? Component.translatable("form.dexrider." + form.id())
                        : Component.translatable("screen.dexrider.suit.state." + state));
        g.text(font, st, left + 8, top + 194, 0xFFB8BCC8, false);
    }

    private ArmorStand stand() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        if (stand == null || stand.level() != mc.level) {
            stand = new ArmorStand(mc.level, 0, 0, 0);
            stand.setId(-0x5D3);   // no esta en el mundo: en 26.2 toda entidad necesita un id para dibujarse
            stand.setInvisible(true);
            stand.setNoBasePlate(true);
            stand.setShowArms(true);
            stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(DexItems.DEX_HELMET.get()));
            stand.setItemSlot(EquipmentSlot.CHEST, new ItemStack(DexItems.DEX_CHESTPLATE.get()));
            stand.setItemSlot(EquipmentSlot.LEGS, new ItemStack(DexItems.DEX_LEGGINGS.get()));
            stand.setItemSlot(EquipmentSlot.FEET, new ItemStack(DexItems.DEX_BOOTS.get()));
        }
        ItemStack chest = stand.getItemBySlot(EquipmentSlot.CHEST);
        chest.set(DexComponents.SUIT_PROFILE.get(), profile());
        chest.remove(DexComponents.FORM.get()); chest.remove(DexComponents.EMOTION.get()); chest.remove(DexComponents.UPGRADE.get());
        switch (state) {
            case SuitProfile.VIOLENT -> chest.set(DexComponents.EMOTION.get(), DexEmotions.VIOLENT);
            case SuitProfile.HEROIC -> chest.set(DexComponents.EMOTION.get(), DexEmotions.HEROIC);
            case SuitProfile.UPGRADE -> chest.set(DexComponents.UPGRADE.get(), true);
            default -> {
                if (state.startsWith("form_")) {
                    chest.set(DexComponents.FORM.get(), form.id());
                    chest.set(DexComponents.FORM_LEVEL.get(), 2);
                }
            }
        }
        return stand;
    }

    private long lastSpin = System.nanoTime();

    /**
     * El giro va en la rotacion del dibujo (no en el cuerpo del maniqui: el juego lo ignora para un soporte sin mundo).
     * Gira solo a 30 grados por segundo; la rueda del raton encima o las flechas lo giran a mano.
     */
    private void drawMannequin(GuiGraphicsExtractor g, float partialTick) {
        ArmorStand s = stand();
        if (s == null) return;
        long now = System.nanoTime();
        if (spin) yaw += (now - lastSpin) / 1.0e9F * 30F;
        lastSpin = now;
        yaw %= 360F;
        s.setYRot(0); s.yRotO = 0; s.setYBodyRot(0); s.yBodyRotO = 0; s.setYHeadRot(0); s.yHeadRotO = 0;
        s.setXRot(0);
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        var renderer = dispatcher.getRenderer(s);
        EntityRenderState rs = renderer.createRenderState(s, 1.0F);
        rs.lightCoords = 0xF000F0;
        rs.shadowPieces.clear();
        rs.outlineColor = 0;
        Quaternionf cam = new Quaternionf().rotateX((float) Math.toRadians(8));
        Quaternionf rot = new Quaternionf().rotateZ((float) Math.PI).mul(cam).rotateY((float) Math.toRadians(yaw));
        Vector3f off = new Vector3f(0, rs.boundingBoxHeight / 2.0F + 0.05F, 0);
        g.entity(rs, 70F, off, rot, cam, left + 4, top + 18, left + 98, top + 202);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (x >= left + 4 && x < left + 98 && y >= top + 18 && y < top + 202) {
            spin = false;
            yaw += (float) scrollY * 20F;
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Barra de un canal de color (R, G o B), de 0 a 255. */
    private class ChannelSlider extends AbstractSliderButton {
        private final int shift;   // 16 = rojo, 8 = verde, 0 = azul

        ChannelSlider(int x, int y, int shift) {
            super(x, y, 140, 16, Component.empty(), 0);
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
            setOwn(own().withColor(zone, updated));
            if (hex != null) hex.setValue(String.format("#%06X", updated & 0xFFFFFF));
        }
    }
}
