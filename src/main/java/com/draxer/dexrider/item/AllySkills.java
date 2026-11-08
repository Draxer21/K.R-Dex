package com.draxer.dexrider.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.level.block.state.BlockState;

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
 *  filter / zone : que picar exactamente y donde (se lo indicas tu)
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

    /**
     * filter  : si no esta vacio, al picar solo toca estos bloques (se lo das en la mano)
     * zone    : [min, max] empaquetados (BlockPos.asLong) de la zona donde pica; vacio = sin zona; zoneDim su dimension
     */
    public record Entry(Map<String, Integer> xp, List<String> blocks, List<Pattern> patterns, String job,
                        List<String> filter, List<Long> zone, String zoneDim,
                        int trust, long trustDay, int trustFlags, int fights,
                        List<BlockState> planPalette, List<Integer> plan, List<Long> build, String buildDim) {
        public static final Entry EMPTY = new Entry(Map.of(), List.of(), List.of(), "", List.of(), List.of(), "", 0, -1, 0, 0,
                List.of(), List.of(), List.of(), "");

        public Entry {
            xp = Map.copyOf(xp);
            blocks = List.copyOf(blocks);
            patterns = List.copyOf(patterns);
            filter = List.copyOf(filter);
            zone = List.copyOf(zone);
            planPalette = List.copyOf(planPalette);
            plan = List.copyOf(plan);
            build = List.copyOf(build);
        }

        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("xp", Map.of()).forGetter(Entry::xp),
                Codec.STRING.listOf().optionalFieldOf("blocks", List.of()).forGetter(Entry::blocks),
                Pattern.CODEC.listOf().optionalFieldOf("patterns", List.of()).forGetter(Entry::patterns),
                Codec.STRING.optionalFieldOf("job", "").forGetter(Entry::job),
                Codec.STRING.listOf().optionalFieldOf("filter", List.of()).forGetter(Entry::filter),
                Codec.LONG.listOf().optionalFieldOf("zone", List.of()).forGetter(Entry::zone),
                Codec.STRING.optionalFieldOf("zone_dim", "").forGetter(Entry::zoneDim),
                Codec.INT.optionalFieldOf("trust", 0).forGetter(Entry::trust),
                Codec.LONG.optionalFieldOf("trust_day", -1L).forGetter(Entry::trustDay),
                Codec.INT.optionalFieldOf("trust_flags", 0).forGetter(Entry::trustFlags),
                Codec.INT.optionalFieldOf("fights", 0).forGetter(Entry::fights),
                BlockState.CODEC.listOf().optionalFieldOf("plan_palette", List.of()).forGetter(Entry::planPalette),
                Codec.INT.listOf().optionalFieldOf("plan", List.of()).forGetter(Entry::plan),
                Codec.LONG.listOf().optionalFieldOf("build", List.of()).forGetter(Entry::build),
                Codec.STRING.optionalFieldOf("build_dim", "").forGetter(Entry::buildDim)
        ).apply(i, Entry::new));

        public int xp(String skill) { return xp.getOrDefault(skill, 0); }
        public boolean knows(String skill) { return xp(skill) >= LEARNED; }
        public boolean hasZone() { return zone.size() == 2; }
        /** Plano copiado: plan = [ancho, alto, largo, celdas...] con 0 = no tocar y n = planPalette[n-1]. */
        public boolean hasPlan() { return plan.size() > 3 && !planPalette.isEmpty(); }
        public boolean hasBuild() { return build.size() == 1; }

        public Entry withPlan(List<BlockState> palette, List<Integer> cells) {
            return new Entry(xp, blocks, patterns, job, filter, zone, zoneDim, trust, trustDay, trustFlags, fights, palette, cells, build, buildDim);
        }
        public Entry withBuild(List<Long> origin, String dim) {
            return new Entry(xp, blocks, patterns, job, filter, zone, zoneDim, trust, trustDay, trustFlags, fights, planPalette, plan, origin, dim == null ? "" : dim);
        }

        public Entry withXp(String skill, int value) {
            Map<String, Integer> m = new HashMap<>(xp);
            m.put(skill, Math.min(MASTER, Math.max(0, value)));
            return new Entry(m, blocks, patterns, job, filter, zone, zoneDim, trust, trustDay, trustFlags, fights, planPalette, plan, build, buildDim);
        }

        public Entry withBlock(String id) {
            if (blocks.contains(id)) return this;
            List<String> l = new ArrayList<>(blocks);
            l.add(id);
            while (l.size() > MAX_BLOCKS) l.remove(0);
            return new Entry(xp, l, patterns, job, filter, zone, zoneDim, trust, trustDay, trustFlags, fights, planPalette, plan, build, buildDim);
        }

        public Entry withPattern(Pattern p) {
            if (patterns.contains(p)) return this;
            List<Pattern> l = new ArrayList<>(patterns);
            l.add(p);
            while (l.size() > MAX_PATTERNS) l.remove(0);
            return new Entry(xp, blocks, l, job, filter, zone, zoneDim, trust, trustDay, trustFlags, fights, planPalette, plan, build, buildDim);
        }

        public Entry withJob(String j) { return new Entry(xp, blocks, patterns, j == null ? "" : j, filter, zone, zoneDim, trust, trustDay, trustFlags, fights, planPalette, plan, build, buildDim); }
        public Entry withFilter(List<String> f) { return new Entry(xp, blocks, patterns, job, f, zone, zoneDim, trust, trustDay, trustFlags, fights, planPalette, plan, build, buildDim); }
        public Entry withZone(List<Long> z, String dim) { return new Entry(xp, blocks, patterns, job, filter, z, dim == null ? "" : dim, trust, trustDay, trustFlags, fights, planPalette, plan, build, buildDim); }
        /** Confianza (0-100) y lo que ya conto hoy (dia de juego, banderas: 1 charlo, 2 regalo; peleas de hoy). */
        public Entry withTrust(int t, long day, int flags, int f) {
            return new Entry(xp, blocks, patterns, job, filter, zone, zoneDim, Math.max(0, Math.min(100, t)), day, flags, f, planPalette, plan, build, buildDim);
        }
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
