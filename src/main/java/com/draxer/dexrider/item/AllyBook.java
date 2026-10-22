package com.draxer.dexrider.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.HashMap;
import java.util.Map;

/**
 * Libreta de amigos Rider, guardada en el Driver (se sincroniza con el cliente para la pantalla de invocar).
 *  gen    : version de la entidad viva de cada amigo (si aparece una copia vieja, se retira sola)
 *  ready  : tick del mundo a partir del cual se le puede volver a llamar (descanso)
 *  active : hasta que tick esta peleando a tu lado (0 = no invocado)
 *  home   : posicion de su casa (BlockPos empaquetado) y homeDim su dimension
 *  returned: los que ya volvieron a su mundo por el portal
 */
public record AllyBook(Map<String, Integer> gen, Map<String, Long> ready, Map<String, Long> active,
                       Map<String, Long> home, Map<String, String> homeDim, Map<String, Long> returned) {

    public static final AllyBook EMPTY = new AllyBook(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());

    public AllyBook {
        gen = Map.copyOf(gen);
        ready = Map.copyOf(ready);
        active = Map.copyOf(active);
        home = Map.copyOf(home);
        homeDim = Map.copyOf(homeDim);
        returned = Map.copyOf(returned);
    }

    public static final Codec<AllyBook> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("gen", Map.of()).forGetter(AllyBook::gen),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ready", Map.of()).forGetter(AllyBook::ready),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("active", Map.of()).forGetter(AllyBook::active),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("home", Map.of()).forGetter(AllyBook::home),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("home_dim", Map.of()).forGetter(AllyBook::homeDim),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("returned", Map.of()).forGetter(AllyBook::returned)
    ).apply(i, AllyBook::new));

    private static final StreamCodec<ByteBuf, Map<String, Integer>> INTS =
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT);
    private static final StreamCodec<ByteBuf, Map<String, Long>> LONGS =
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_LONG);
    private static final StreamCodec<ByteBuf, Map<String, String>> STRINGS =
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8);

    public static final StreamCodec<ByteBuf, AllyBook> STREAM_CODEC = StreamCodec.composite(
            INTS, AllyBook::gen, LONGS, AllyBook::ready, LONGS, AllyBook::active,
            LONGS, AllyBook::home, STRINGS, AllyBook::homeDim, LONGS, AllyBook::returned, AllyBook::new);

    public int gen(String id) { return gen.getOrDefault(id, 0); }
    public long readyAt(String id) { return ready.getOrDefault(id, 0L); }
    public long activeUntil(String id) { return active.getOrDefault(id, 0L); }

    private static <V> Map<String, V> put(Map<String, V> m, String k, V v) {
        Map<String, V> n = new HashMap<>(m);
        if (v == null) n.remove(k); else n.put(k, v);
        return n;
    }

    public AllyBook withGen(String id, int g) { return new AllyBook(put(gen, id, g), ready, active, home, homeDim, returned); }
    public AllyBook withReady(String id, long t) { return new AllyBook(gen, put(ready, id, t), active, home, homeDim, returned); }
    public AllyBook withActive(String id, long t) { return new AllyBook(gen, ready, put(active, id, t == 0 ? null : t), home, homeDim, returned); }
    /** Volvio a su mundo por el portal (ya no tiene casa aqui; si lo llamas, viene por un portal). */
    public boolean isReturned(String id) { return returned.containsKey(id); }
    public AllyBook withReturned(String id, long when) { return new AllyBook(gen, ready, active, home, homeDim, put(returned, id, when)); }

    public AllyBook withHome(String id, long pos, String dim) {
        return new AllyBook(gen, ready, active, put(home, id, pos), put(homeDim, id, dim), returned);
    }
}
