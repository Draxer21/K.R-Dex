package com.draxer.dexrider.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Una apariencia del traje de Dex (para un estado: base, Violent, Heroic, Upgrade o una Form Ride).
 * Solo guarda lo que el jugador cambio; lo demas se hereda (ver {@link SuitLooks}).
 *
 *  colors: zona -> 0xRRGGBB      parts: pieza -> variante
 */
public record SuitLook(Map<String, Integer> colors, Map<String, String> parts) {

    // ---- zonas de color
    public static final String HELMET = "helmet", CHEST = "chest", ARMS = "arms", LEGS = "legs", BOOTS = "boots",
            ACCENT = "accent", EYES = "eyes", SILVER = "silver", INNER = "inner", CRYSTAL = "crystal", SCARF = "scarf",
            HALF_RIGHT = "half_right", HALF_LEFT = "half_left";
    public static final List<String> COLOR_KEYS = List.of(HELMET, CHEST, ARMS, LEGS, BOOTS, ACCENT, EYES, SILVER, INNER,
            CRYSTAL, SCARF, HALF_RIGHT, HALF_LEFT);

    // ---- piezas y opciones: clave -> variantes posibles (la primera es la de fabrica en la forma base)
    public static final Map<String, List<String>> PART_VALUES = new java.util.LinkedHashMap<>();
    static {
        PART_VALUES.put("horns", List.of("straight", "curved", "short", "none"));
        PART_VALUES.put("stripes", List.of("on", "off"));
        PART_VALUES.put("pauldrons", List.of("layered", "flat", "none"));
        PART_VALUES.put("scarf", List.of("none", "short", "long"));
        PART_VALUES.put("crest", List.of("off", "on"));
        PART_VALUES.put("booker", List.of("on", "off"));
        PART_VALUES.put("crystal", List.of("off", "on"));
        PART_VALUES.put("spikes_head", List.of("off", "on"));
        PART_VALUES.put("spikes_body", List.of("off", "on"));
        PART_VALUES.put("spikes_arms", List.of("off", "on"));
        PART_VALUES.put("wings", List.of("on", "off"));
        PART_VALUES.put("glow_eyes", List.of("on", "off"));
        PART_VALUES.put("glow_crystal", List.of("on", "off"));
        PART_VALUES.put("glow_accent", List.of("off", "on"));
        PART_VALUES.put("glow_main", List.of("off", "on"));
        PART_VALUES.put("finish", List.of("normal", "matte", "metal"));
    }

    public static final SuitLook EMPTY = new SuitLook(Map.of(), Map.of());

    public static final Codec<SuitLook> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("colors", Map.of()).forGetter(SuitLook::colors),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("parts", Map.of()).forGetter(SuitLook::parts)
    ).apply(i, SuitLook::new));

    public static final StreamCodec<ByteBuf, SuitLook> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.stringUtf8(32), ByteBufCodecs.INT, 32), SuitLook::colors,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.stringUtf8(32), ByteBufCodecs.stringUtf8(32), 32), SuitLook::parts,
            SuitLook::new);

    public boolean isEmpty() { return colors.isEmpty() && parts.isEmpty(); }

    public SuitLook withColor(String key, int rgb) {
        Map<String, Integer> c = new HashMap<>(colors); c.put(key, rgb & 0xFFFFFF);
        return new SuitLook(Map.copyOf(c), parts);
    }

    public SuitLook withoutColor(String key) {
        Map<String, Integer> c = new HashMap<>(colors); c.remove(key);
        return new SuitLook(Map.copyOf(c), parts);
    }

    public SuitLook withPart(String key, String value) {
        Map<String, String> p = new HashMap<>(parts); p.put(key, value);
        return new SuitLook(colors, Map.copyOf(p));
    }

    /** Quita lo que no es valido (zonas o piezas desconocidas, variantes que no existen). */
    public SuitLook sanitized() {
        Map<String, Integer> c = new HashMap<>();
        colors.forEach((k, v) -> { if (COLOR_KEYS.contains(k) && v != null) c.put(k, v & 0xFFFFFF); });
        Map<String, String> p = new HashMap<>();
        parts.forEach((k, v) -> { List<String> ok = PART_VALUES.get(k); if (ok != null && ok.contains(v)) p.put(k, v); });
        return new SuitLook(Map.copyOf(c), Map.copyOf(p));
    }
}
