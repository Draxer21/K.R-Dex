package com.draxer.dexrider.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;
import java.util.Locale;

/**
 * Personalizacion del traje de Dex. Se guarda en el Driver y se copia al peto al transformarse,
 * para que todos los jugadores cercanos vean el traje con los mismos colores y piezas.
 *
 * Colores en 0xRRGGBB. Las Form Ride mantienen su propio color principal mientras estan activas.
 */
public record SuitConfig(int mainColor, int eyeColor, int accentColor, Horns horns, boolean eyeStripes, boolean showBooker) {

    public enum Horns {
        STRAIGHT, CURVED, SHORT, NONE;

        public String id() { return name().toLowerCase(Locale.ROOT); }

        public Horns next() { return values()[(ordinal() + 1) % values().length]; }

        public static Horns byId(String id) {
            for (Horns h : values()) if (h.id().equals(id)) return h;
            return STRAIGHT;
        }
    }

    /** Traje original de Dex: rojo, ojos verdes, acentos dorados, cuernos rectos. */
    public static final SuitConfig DEFAULT = new SuitConfig(0xCD1C28, 0x28D250, 0xE8B630, Horns.STRAIGHT, true, true);

    /** Colores rapidos de la pantalla de personalizacion. */
    public static final List<Integer> PALETTE = List.of(
            0xCD1C28, 0xF05A14, 0xE8B630, 0xF5E642,
            0x28D250, 0x1EA0A0, 0x1E69EB, 0x3C2896,
            0x9632C8, 0xE650A0, 0xF0F0F4, 0x1C1C24);

    private static final Codec<Horns> HORNS_CODEC = Codec.STRING.xmap(Horns::byId, Horns::id);

    public static final Codec<SuitConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("main", DEFAULT.mainColor()).forGetter(SuitConfig::mainColor),
            Codec.INT.optionalFieldOf("eyes", DEFAULT.eyeColor()).forGetter(SuitConfig::eyeColor),
            Codec.INT.optionalFieldOf("accent", DEFAULT.accentColor()).forGetter(SuitConfig::accentColor),
            HORNS_CODEC.optionalFieldOf("horns", Horns.STRAIGHT).forGetter(SuitConfig::horns),
            Codec.BOOL.optionalFieldOf("eye_stripes", true).forGetter(SuitConfig::eyeStripes),
            Codec.BOOL.optionalFieldOf("show_booker", true).forGetter(SuitConfig::showBooker)
    ).apply(i, SuitConfig::new));

    public static final StreamCodec<ByteBuf, SuitConfig> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, SuitConfig::mainColor,
            ByteBufCodecs.INT, SuitConfig::eyeColor,
            ByteBufCodecs.INT, SuitConfig::accentColor,
            ByteBufCodecs.STRING_UTF8.map(Horns::byId, Horns::id), SuitConfig::horns,
            ByteBufCodecs.BOOL, SuitConfig::eyeStripes,
            ByteBufCodecs.BOOL, SuitConfig::showBooker,
            SuitConfig::new);

    public SuitConfig withMain(int c) { return new SuitConfig(c & 0xFFFFFF, eyeColor, accentColor, horns, eyeStripes, showBooker); }
    public SuitConfig withEyes(int c) { return new SuitConfig(mainColor, c & 0xFFFFFF, accentColor, horns, eyeStripes, showBooker); }
    public SuitConfig withAccent(int c) { return new SuitConfig(mainColor, eyeColor, c & 0xFFFFFF, horns, eyeStripes, showBooker); }
    public SuitConfig withHorns(Horns h) { return new SuitConfig(mainColor, eyeColor, accentColor, h, eyeStripes, showBooker); }
    public SuitConfig withEyeStripes(boolean b) { return new SuitConfig(mainColor, eyeColor, accentColor, horns, b, showBooker); }
    public SuitConfig withBooker(boolean b) { return new SuitConfig(mainColor, eyeColor, accentColor, horns, eyeStripes, b); }
}
