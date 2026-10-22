package com.draxer.dexrider.rider;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Bloques temporales de las habilidades (muros de hielo, pilares de piedra, rampas de tierra...).
 * Solo se colocan en aire, agua, nieve, hierba y similares (bloques reemplazables) y nunca encima de una entidad.
 * Pasado su tiempo vuelve lo que habia antes. Si alguien los rompe antes, simplemente se olvidan.
 * Al cerrar el servidor se restauran todos, asi nunca quedan en el mundo.
 */
public final class TempBlocks {
    private TempBlocks() {}

    private record Entry(BlockState placed, BlockState original, long expires) {}

    private static final Map<ResourceKey<Level>, Map<BlockPos, Entry>> BLOCKS = new HashMap<>();

    /** Intenta colocar un bloque temporal. Devuelve true si se coloco. */
    public static boolean place(ServerLevel level, BlockPos pos, BlockState state, int ticks) {
        return place(level, pos, state, ticks, true);
    }

    /** Igual, pero puede ponerse encima de alguien (telaranas que atrapan, por ejemplo). */
    public static boolean placeIgnoringEntities(ServerLevel level, BlockPos pos, BlockState state, int ticks) {
        return place(level, pos, state, ticks, false);
    }

    private static boolean place(ServerLevel level, BlockPos pos, BlockState state, int ticks, boolean checkEntities) {
        if (!level.isInWorldBounds(pos) || !level.isLoaded(pos)) return false;
        BlockState current = level.getBlockState(pos);
        Map<BlockPos, Entry> map = BLOCKS.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        Entry previous = map.get(pos);
        if (previous == null && !current.canBeReplaced()) return false;
        if (checkEntities && !level.getEntitiesOfClass(Entity.class, new AABB(pos), e -> !e.isSpectator()).isEmpty()) return false;

        BlockState original = previous != null ? previous.original() : current;
        // El agua que fluye no se guarda: al volver se pone aire y el agua vuelve a correr sola
        if (!original.getFluidState().isEmpty() && !original.getFluidState().isSource()) {
            original = Blocks.AIR.defaultBlockState();
        }
        level.setBlock(pos.immutable(), state, 3);
        map.put(pos.immutable(), new Entry(state, original, level.getGameTime() + ticks));
        return true;
    }

    /** Llamado cada tick de cada mundo del servidor. */
    public static void tick(ServerLevel level) {
        Map<BlockPos, Entry> map = BLOCKS.get(level.dimension());
        if (map == null || map.isEmpty()) return;
        long now = level.getGameTime();
        for (Iterator<Map.Entry<BlockPos, Entry>> it = map.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<BlockPos, Entry> e = it.next();
            if (e.getValue().expires() > now || !level.isLoaded(e.getKey())) continue;   // sin cargar: espera
            restore(level, e.getKey(), e.getValue());
            it.remove();
        }
    }

    /**
     * Si alguien rompe un bloque temporal, vuelve lo que habia y no suelta nada (asi no se pueden "farmear").
     * Devuelve true si el bloque era temporal.
     */
    public static boolean breakTemp(ServerLevel level, BlockPos pos) {
        Map<BlockPos, Entry> map = BLOCKS.get(level.dimension());
        if (map == null) return false;
        Entry e = map.remove(pos);
        if (e == null) return false;
        level.setBlock(pos, e.original(), 3);
        return true;
    }

    /** Restaura todo (al cerrar el servidor). */
    public static void restoreAll(Iterable<ServerLevel> levels) {
        for (ServerLevel level : levels) {
            Map<BlockPos, Entry> map = BLOCKS.remove(level.dimension());
            if (map == null) continue;
            map.forEach((pos, entry) -> restore(level, pos, entry));
        }
        BLOCKS.clear();
    }

    private static void restore(ServerLevel level, BlockPos pos, Entry entry) {
        if (!level.isLoaded(pos)) return;
        // Solo si sigue siendo nuestro bloque (si lo rompieron o cambiaron, no se toca)
        if (level.getBlockState(pos).is(entry.placed().getBlock())) {
            level.setBlock(pos, entry.original(), 3);
        }
    }
}
