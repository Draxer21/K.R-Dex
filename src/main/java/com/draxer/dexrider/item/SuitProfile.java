package com.draxer.dexrider.item;

import com.draxer.dexrider.rider.FormRide;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Un diseno completo del traje: su nombre y una apariencia por estado (lo que no se cambia, se hereda).
 * Estados: base, violent, heroic, upgrade y form_<forma>.
 */
public record SuitProfile(String name, Map<String, SuitLook> looks) {

    public static final String BASE = "base", VIOLENT = "violent", HEROIC = "heroic", UPGRADE = "upgrade";
    public static final int MAX_NAME = 24;

    public static List<String> states() {
        List<String> s = new ArrayList<>(List.of(BASE, VIOLENT, HEROIC, UPGRADE));
        for (FormRide f : FormRide.values()) s.add(form(f));
        return s;
    }

    public static String form(FormRide f) { return "form_" + f.id(); }

    public static final SuitProfile DEFAULT = new SuitProfile("", Map.of());

    public static final Codec<SuitProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("name", "").forGetter(SuitProfile::name),
            Codec.unboundedMap(Codec.STRING, SuitLook.CODEC).optionalFieldOf("looks", Map.of()).forGetter(SuitProfile::looks)
    ).apply(i, SuitProfile::new));

    public static final StreamCodec<ByteBuf, SuitProfile> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), SuitProfile::name,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.stringUtf8(32), SuitLook.STREAM_CODEC, 24), SuitProfile::looks,
            SuitProfile::new);

    public SuitLook look(String state) {
        SuitLook l = looks.get(state);
        return l == null ? SuitLook.EMPTY : l;
    }

    public SuitProfile withLook(String state, SuitLook look) {
        Map<String, SuitLook> m = new HashMap<>(looks);
        if (look == null || look.isEmpty()) m.remove(state); else m.put(state, look);
        return new SuitProfile(name, Map.copyOf(m));
    }

    public SuitProfile withName(String n) {
        String s = n == null ? "" : n.strip();
        return new SuitProfile(s.length() > MAX_NAME ? s.substring(0, MAX_NAME) : s, looks);
    }

    /** Sin estados desconocidos, zonas o piezas invalidas, ni nombres larguisimos (lo que llega de un cliente o un codigo). */
    public SuitProfile sanitized() {
        List<String> ok = states();
        Map<String, SuitLook> m = new HashMap<>();
        looks.forEach((k, v) -> { if (ok.contains(k) && v != null) { SuitLook s = v.sanitized(); if (!s.isEmpty()) m.put(k, s); } });
        return new SuitProfile("", Map.copyOf(m)).withName(name);
    }

    /** El traje que se guardaba antes de 0.33 (tres colores, cuernos, franjas y Booker). */
    public static SuitProfile fromLegacy(SuitConfig c) {
        if (c == null) return DEFAULT;
        SuitLook l = SuitLook.EMPTY;
        for (String z : List.of(SuitLook.HELMET, SuitLook.CHEST, SuitLook.ARMS, SuitLook.LEGS, SuitLook.BOOTS)) l = l.withColor(z, c.mainColor());
        l = l.withColor(SuitLook.EYES, c.eyeColor()).withColor(SuitLook.ACCENT, c.accentColor())
                .withPart("horns", c.horns().id()).withPart("stripes", c.eyeStripes() ? "on" : "off")
                .withPart("booker", c.showBooker() ? "on" : "off");
        return DEFAULT.withLook(BASE, l);
    }

    // ------------------------------------------------------------------ codigo para compartir
    private static final String PREFIX = "DEX1-";

    public String toCode() {
        String json = CODEC.encodeStart(JsonOps.INSTANCE, this).getOrThrow().toString();
        byte[] raw = json.getBytes(StandardCharsets.UTF_8);
        Deflater d = new Deflater(Deflater.BEST_COMPRESSION);
        d.setInput(raw); d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        while (!d.finished()) out.write(buf, 0, d.deflate(buf));
        d.end();
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
    }

    /** null si el texto no es un codigo valido. */
    public static SuitProfile fromCode(String code) {
        if (code == null) return null;
        String c = code.strip();
        if (!c.startsWith(PREFIX) || c.length() > 8192) return null;
        try {
            byte[] zipped = Base64.getUrlDecoder().decode(c.substring(PREFIX.length()));
            Inflater inf = new Inflater();
            inf.setInput(zipped);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                out.write(buf, 0, n);
                if (out.size() > 65536) { inf.end(); return null; }
            }
            inf.end();
            String json = out.toString(StandardCharsets.UTF_8);
            return CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().map(SuitProfile::sanitized).orElse(null);
        } catch (IllegalArgumentException | DataFormatException | com.google.gson.JsonParseException e) {
            return null;
        }
    }
}
