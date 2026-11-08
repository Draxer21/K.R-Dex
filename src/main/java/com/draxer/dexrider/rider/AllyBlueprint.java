package com.draxer.dexrider.rider;

import com.draxer.dexrider.item.AllySkills;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Construir copiando: el amigo memoriza lo que hay dentro de su zona marcada (un "plano") y luego levanta una copia
 * donde le digas, bloque a bloque, de abajo hacia arriba, con los materiales del cofre de su casa.
 */
public final class AllyBlueprint {
    private AllyBlueprint() {}

    public static final int MAX_SIDE = 12;
    public static final int MAX_BLOCKS = 600;

    /** Resultado de copiar: el plano nuevo o la clave del error. */
    public record Capture(@Nullable AllySkills.Entry entry, String message, int count) {}

    /** Bloques que no se copian: aire, liquidos, irrompibles y lo que no tiene objeto. */
    public static boolean copyable(ServerLevel level, BlockPos p, BlockState s) {
        if (s.isAir() || s.liquid() || s.getDestroySpeed(level, p) < 0) return false;
        Item it = s.getBlock().asItem();
        return it != Items.AIR || isFreeHalf(s);
    }

    /** La otra mitad de una puerta, una cama o una planta alta: sale gratis (la primera mitad ya gasto el objeto). */
    public static boolean isFreeHalf(BlockState s) {
        if (s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) return true;
        return s.hasProperty(BlockStateProperties.BED_PART) && s.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD;
    }

    public static Capture capture(ServerLevel level, AllySkills.Entry e) {
        if (!e.hasZone()) return new Capture(null, "message.dexrider.ally_plan_nozone", 0);
        if (!level.dimension().identifier().toString().equals(e.zoneDim())) return new Capture(null, "message.dexrider.ally_plan_nozone", 0);
        BlockPos min = AllyWork.zoneMin(e), max = AllyWork.zoneMax(e);
        int w = max.getX() - min.getX() + 1, h = max.getY() - min.getY() + 1, d = max.getZ() - min.getZ() + 1;
        if (w > MAX_SIDE || h > MAX_SIDE || d > MAX_SIDE) return new Capture(null, "message.dexrider.ally_plan_big", 0);
        List<BlockState> palette = new ArrayList<>();
        Map<BlockState, Integer> index = new HashMap<>();
        List<Integer> cells = new ArrayList<>(List.of(w, h, d));
        int count = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) {
            BlockPos p = min.offset(x, y, z);
            BlockState s = level.getBlockState(p);
            if (!copyable(level, p, s)) { cells.add(0); continue; }
            Integer i = index.get(s);
            if (i == null) { palette.add(s); i = palette.size(); index.put(s, i); }
            cells.add(i);
            count++;
        }
        if (count == 0) return new Capture(null, "message.dexrider.ally_plan_empty", 0);
        if (count > MAX_BLOCKS) return new Capture(null, "message.dexrider.ally_plan_big", 0);
        return new Capture(e.withPlan(palette, cells).withZone(List.of(), ""), "message.dexrider.ally_plan_ok", count);
    }

    public static int width(AllySkills.Entry e) { return e.plan().get(0); }
    public static int height(AllySkills.Entry e) { return e.plan().get(1); }
    public static int depth(AllySkills.Entry e) { return e.plan().get(2); }

    public static int blockCount(AllySkills.Entry e) {
        if (!e.hasPlan()) return 0;
        int n = 0;
        for (int i = 3; i < e.plan().size(); i++) if (e.plan().get(i) > 0) n++;
        return n;
    }

    @Nullable
    public static BlockPos origin(AllySkills.Entry e) {
        return e.hasBuild() ? BlockPos.of(e.build().get(0)) : null;
    }

    /** Un bloque del plano, ya puesto en el mundo. */
    public record Cell(BlockPos pos, BlockState state) {}

    /** Las celdas en orden de construccion (de abajo hacia arriba). */
    public static List<Cell> cells(AllySkills.Entry e) {
        List<Cell> out = new ArrayList<>();
        BlockPos o = origin(e);
        if (o == null || !e.hasPlan()) return out;
        int w = width(e), h = height(e), d = depth(e), i = 3;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) {
            int v = e.plan().get(i++);
            if (v > 0 && v <= e.planPalette().size()) out.add(new Cell(o.offset(x, y, z), e.planPalette().get(v - 1)));
        }
        return out;
    }

    /** Lo que gasta un bloque del plano (vacio si es gratis). */
    public static ItemStack cost(BlockState s) {
        if (isFreeHalf(s)) return ItemStack.EMPTY;
        Item it = s.getBlock().asItem();
        return it == Items.AIR ? ItemStack.EMPTY : new ItemStack(it);
    }

    // ------------------------------------------------------------------ elegir donde construir

    private record BuildPick(KamenRide rider, long until) {}
    private static final Map<UUID, BuildPick> PICKS = new HashMap<>();

    public static void pickBuildSpot(ServerPlayer owner, KamenRide rider) {
        PICKS.put(owner.getUUID(), new BuildPick(rider, owner.level().getGameTime() + 2400));
    }

    /** Shift + clic derecho en un bloque mientras eliges: la copia empieza encima de ese bloque. */
    public static boolean markBuild(ServerPlayer owner, BlockPos clicked) {
        BuildPick pick = PICKS.get(owner.getUUID());
        if (pick == null || owner.level().getGameTime() > pick.until()) { PICKS.remove(owner.getUUID()); return false; }
        if (!(owner.level() instanceof ServerLevel level)) return false;
        PICKS.remove(owner.getUUID());
        AllySkills.Entry e = AllyWork.entry(owner, pick.rider());
        if (!e.hasPlan()) return true;
        BlockPos o = clicked.above();
        e = e.withBuild(List.of(o.asLong()), level.dimension().identifier().toString()).withJob(AllyWork.Job.BUILD.id());
        AllyWork.save(owner, pick.rider(), e);
        AllyWork.outline(level, o, o.offset(width(e) - 1, height(e) - 1, depth(e) - 1));
        owner.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.dexrider.ally_build_start",
                pick.rider().displayName(), blockCount(e)), false);
        return true;
    }

    public static boolean picking(ServerPlayer owner) {
        BuildPick pick = PICKS.get(owner.getUUID());
        return pick != null && owner.level().getGameTime() <= pick.until();
    }
}
