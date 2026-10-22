package com.draxer.dexrider.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * El mazo guardado dentro del Dex Driver: cartas generadas, muestras parciales de escaneo y nivel de cada carta.
 * Es inmutable: cada cambio devuelve un mazo nuevo que se vuelve a guardar en el Driver.
 */
public record DriverDeck(List<String> cards, Map<String, Integer> progress, Map<String, Integer> levels) {

    public static final DriverDeck EMPTY = new DriverDeck(List.of(), Map.of(), Map.of());

    /** Escalado del fanfic: +15 % por cada carta de la misma familia. */
    public static final float BONUS_PER_CARD = 0.15F;
    public static final int MAX_LEVEL = 3;

    public DriverDeck {
        cards = List.copyOf(cards);
        progress = Map.copyOf(progress);
        levels = Map.copyOf(levels);
    }

    public static final Codec<DriverDeck> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().fieldOf("cards").forGetter(DriverDeck::cards),
            Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("progress").forGetter(DriverDeck::progress),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("levels", Map.of()).forGetter(DriverDeck::levels)
    ).apply(i, DriverDeck::new));

    private static final StreamCodec<ByteBuf, Map<String, Integer>> INT_MAP_STREAM_CODEC =
            ByteBufCodecs.<ByteBuf, String, Integer, Map<String, Integer>>map(
                    HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT);

    public static final StreamCodec<ByteBuf, DriverDeck> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), DriverDeck::cards,
            INT_MAP_STREAM_CODEC, DriverDeck::progress,
            INT_MAP_STREAM_CODEC, DriverDeck::levels,
            DriverDeck::new);

    public boolean has(DexCard card) {
        return cards.contains(card.id());
    }

    public DriverDeck withCard(DexCard card) {
        if (has(card)) return this;
        List<String> newCards = new ArrayList<>(cards);
        newCards.add(card.id());
        Map<String, Integer> newProgress = new HashMap<>(progress);
        newProgress.remove(card.id());
        return new DriverDeck(newCards, newProgress, levels);
    }

    public int samples(DexCard card) {
        return progress.getOrDefault(card.id(), 0);
    }

    public DriverDeck withSamples(DexCard card, int samples) {
        Map<String, Integer> newProgress = new HashMap<>(progress);
        newProgress.put(card.id(), samples);
        return new DriverDeck(cards, newProgress, levels);
    }

    /** Nivel de la carta (1 si la tiene sin subir, 0 si no la tiene). */
    public int level(DexCard card) {
        if (!has(card)) return 0;
        return Math.max(1, levels.getOrDefault(card.id(), 1));
    }

    /** Sube la carta un nivel (sustitucion por la version mejorada) y reinicia sus muestras. */
    public DriverDeck levelUp(DexCard card) {
        Map<String, Integer> newLevels = new HashMap<>(levels);
        newLevels.put(card.id(), Math.min(MAX_LEVEL, level(card) + 1));
        Map<String, Integer> newProgress = new HashMap<>(progress);
        newProgress.remove(card.id());
        return new DriverDeck(cards, newProgress, newLevels);
    }

    /** Muestras que hacen falta para la siguiente mejora: el doble para nivel 2, el cuadruple para nivel 3. */
    public int samplesForNextLevel(DexCard card) {
        return card.samplesToScan() * (level(card) == 1 ? 2 : 4);
    }

    public int count(DexCard.Family family) {
        int n = 0;
        for (String id : cards) {
            DexCard c = DexCard.byId(id);
            if (c != null && c.family() == family) n++;
        }
        return n;
    }

    /** 1 carta -> x1.15, 2 -> x1.30, 6 -> x1.90 ... */
    public float multiplier(DexCard.Family family) {
        return 1.0F + BONUS_PER_CARD * Math.max(1, count(family));
    }
}
