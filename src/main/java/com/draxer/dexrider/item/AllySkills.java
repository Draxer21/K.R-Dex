package com.draxer.dexrider.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lo que cada amigo Rider aprendio mirandote trabajar (guardado en el Driver, por id del Rider).
 *  xp       : experiencia por trabajo (mine, chop, farm, craft). Se aprende al llegar a {@link #LEARNED}.
 *  blocks   : bloques que te vio picar (solo naturales), los que buscara al minar
 *  patterns : recetas que te vio fabricar, con la forma exacta de la mesa
 *  job      : trabajo encargado junto a su casa ("" = ninguno)
 */
public record AllySkills(Map<String, Entry> riders) {

    public static final int LEARNED = 100;
    public static final int MASTER = 400;
    public static final int MAX_BLOCKS = 24;
    public static final int MAX_PATTERNS = 24;

    public static final AllySkills EMPTY = new AllySkills(Map.of());

    public AllySkills {
        riders = Map.copyOf(riders);
    }

    /** Una receta tal como la pusiste en la mesa (ancho, alto e ids de objeto; "" = hueco). */
    public record Pattern(int width, int height, List<String> items) {
        public Pattern {
            items = List.copyOf(items);
        }

        public static final Codec<Pattern> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("w").forGetter(Pattern::width),
                Codec.INT.fieldOf("h").forGetter(Pattern::height),
                Codec.STRING.listOf().fieldOf("items").forGetter(Pattern::items)
        ).apply(i, Pattern::new));
    }

    public record Entry(Map<String, Integer> xp, List<String> blocks, List<Pattern> patterns, String job) {
        public static final Entry EMPTY = new Entry(Map.of(), List.of(), List.of(), "");

        public Entry {
            xp = Map.copyOf(xp);
            blocks = List.copyOf(blocks);
            patterns = List.copyOf(patterns);
        }

        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("xp", Map.of()).forGetter(Entry::xp),
                Codec.STRING.listOf().optionalFieldOf("blocks", List.of()).forGetter(Entry::blocks),
                Pattern.CODEC.listOf().optionalFieldOf("patterns", List.of()).forGetter(Entry::patterns),
                Codec.STRING.optionalFieldOf("job", "").forGetter(Entry::job)
        ).apply(i, Entry::new));

        public int xp(String skill) { return xp.getOrDefault(skill, 0); }
        public boolean knows(String skill) { return xp(skill) >= LEARNED; }

        public Entry withXp(String skill, int value) {
            Map<String, Integer> m = new HashMap<>(xp);
            m.put(skill, Math.min(MASTER, Math.max(0, value)));
            return new Entry(m, blocks, patterns, job);
        }

        public Entry withBlock(String id) {
            if (blocks.contains(id)) return this;
            List<String> l = new ArrayList<>(blocks);
            l.add(id);
            while (l.size() > MAX_BLOCKS) l.remove(0);
            return new Entry(xp, l, patterns, job);
        }

        public Entry withPattern(Pattern p) {
            if (patterns.contains(p)) return this;
            List<Pattern> l = new ArrayList<>(patterns);
            l.add(p);
            while (l.size() > MAX_PATTERNS) l.remove(0);
            return new Entry(xp, blocks, l, job);
        }

        public Entry withJob(String j) { return new Entry(xp, blocks, patterns, j == null ? "" : j); }
    }

    public static final Codec<AllySkills> CODEC = Codec.unboundedMap(Codec.STRING, Entry.CODEC)
            .xmap(AllySkills::new, AllySkills::riders);

    public Entry get(String rider) { return riders.getOrDefault(rider, Entry.EMPTY); }

    public AllySkills with(String rider, Entry e) {
        Map<String, Entry> m = new HashMap<>(riders);
        m.put(rider, e);
        return new AllySkills(m);
    }
}
