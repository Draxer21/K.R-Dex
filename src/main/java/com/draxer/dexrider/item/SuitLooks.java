package com.draxer.dexrider.item;

import com.draxer.dexrider.rider.DexEmotions;
import com.draxer.dexrider.rider.FormRide;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resuelve la apariencia final del traje para un estado: valores de fabrica, luego lo que el jugador cambio
 * en la forma base y, encima, lo que cambio en ese estado.
 *
 *  - Violent / Heroic: parten de tu forma base con los colores de la Emotion, el cristal y (Violent) las puas.
 *  - Form Rides: parten de tu forma base con el color de la forma en las zonas principales.
 *  - Upgrade Form: tiene su propio modelo y su paleta original (solo hereda el brillo y el acabado de la base).
 */
public final class SuitLooks {
    private SuitLooks() {}

    public static final List<String> MAIN_ZONES = List.of(SuitLook.HELMET, SuitLook.CHEST, SuitLook.ARMS, SuitLook.LEGS, SuitLook.BOOTS);

    public record Resolved(Map<String, Integer> colors, Map<String, String> parts) {
        public int color(String key) { Integer c = colors.get(key); return c == null ? 0xFFFFFF : c; }
        public String part(String key) { String v = parts.get(key); return v == null ? SuitLook.PART_VALUES.get(key).get(0) : v; }
        public boolean on(String key) { return "on".equals(part(key)); }
    }

    private static final Map<String, Integer> BASE_COLORS = Map.ofEntries(
            Map.entry(SuitLook.HELMET, 0xCD1C28), Map.entry(SuitLook.CHEST, 0xCD1C28), Map.entry(SuitLook.ARMS, 0xCD1C28),
            Map.entry(SuitLook.LEGS, 0xCD1C28), Map.entry(SuitLook.BOOTS, 0xCD1C28),
            Map.entry(SuitLook.ACCENT, 0xE8B630), Map.entry(SuitLook.EYES, 0x28D250),
            Map.entry(SuitLook.SILVER, 0xFFFFFF), Map.entry(SuitLook.INNER, 0x51545D), Map.entry(SuitLook.SCARF, 0xFFFFFF),
            Map.entry(SuitLook.CRYSTAL, 0x28D250), Map.entry(SuitLook.VISOR, 0x1C1C24),
            Map.entry(SuitLook.HALF_RIGHT, FormRide.HALF.suitColor()), Map.entry(SuitLook.HALF_LEFT, FormRide.HALF_FIRE_COLOR));
    private static final Map<String, Integer> UPGRADE_COLORS = Map.of(
            SuitLook.SILVER, 0xF4F4FA, SuitLook.INNER, 0x504F5E);

    /** Estado a dibujar: la Upgrade Form manda; luego la Emotion; luego la Form Ride. */
    public static String stateFor(boolean upgrade, String emotion, FormRide form) {
        if (upgrade) return SuitProfile.UPGRADE;
        if (DexEmotions.VIOLENT.equals(emotion)) return SuitProfile.VIOLENT;
        if (DexEmotions.HEROIC.equals(emotion)) return SuitProfile.HEROIC;
        if (form != null) return SuitProfile.form(form);
        return SuitProfile.BASE;
    }

    /** Valores de fabrica de un estado (sin nada del jugador), para el boton "por defecto" del editor. */
    public static Resolved factory(String state) {
        return resolve(SuitProfile.DEFAULT, state);
    }

    public static Resolved resolve(SuitProfile profile, String state) {
        if (profile == null) profile = SuitProfile.DEFAULT;
        Map<String, Integer> colors = new HashMap<>(BASE_COLORS);
        Map<String, String> parts = new HashMap<>();
        SuitLook.PART_VALUES.forEach((k, v) -> parts.put(k, v.get(0)));
        SuitLook base = profile.look(SuitProfile.BASE);
        boolean crystalSet;

        if (SuitProfile.UPGRADE.equals(state)) {
            parts.putAll(base.parts());
            colors.putAll(UPGRADE_COLORS);
            crystalSet = false;
        } else {
            colors.putAll(base.colors());
            parts.putAll(base.parts());
            crystalSet = base.colors().containsKey(SuitLook.CRYSTAL);
            if (!SuitProfile.BASE.equals(state)) {
                int[] emo = DexEmotions.colors(state);
                if (emo != null) {
                    for (String z : MAIN_ZONES) colors.put(z, emo[0]);
                    colors.put(SuitLook.EYES, emo[1]);
                    colors.put(SuitLook.ACCENT, emo[2]);
                    parts.put("crystal", "on");
                    if (SuitProfile.VIOLENT.equals(state))
                        for (String s : List.of("spikes_head", "spikes_body", "spikes_arms")) parts.put(s, "on");
                } else if (state.startsWith("form_")) {
                    FormRide f = FormRide.byId(state.substring(5));
                    if (f != null && f != FormRide.HALF) for (String z : MAIN_ZONES) colors.put(z, f.suitColor());
                    // la pieza propia de la forma viene puesta (se puede quitar en el editor)
                    if (f != null) SuitLook.PART_VALUES.keySet().forEach(k -> { if (f.id().equals(SuitLook.formOfPart(k)) && !"wings".equals(k)) parts.put(k, "on"); });
                }
            }
        }
        if (!SuitProfile.BASE.equals(state)) {
            SuitLook own = profile.look(state);
            colors.putAll(own.colors());
            parts.putAll(own.parts());
            crystalSet |= own.colors().containsKey(SuitLook.CRYSTAL);
        }
        if (!crystalSet) colors.put(SuitLook.CRYSTAL, colors.get(SuitLook.EYES));
        return new Resolved(Map.copyOf(colors), Map.copyOf(parts));
    }
}
