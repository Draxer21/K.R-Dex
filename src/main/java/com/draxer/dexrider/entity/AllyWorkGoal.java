package com.draxer.dexrider.entity;

import com.draxer.dexrider.item.AllySkills;
import com.draxer.dexrider.rider.AllyWork;
import com.draxer.dexrider.rider.AllyWork.Job;
import com.draxer.dexrider.rider.RiderDialogue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * El amigo trabaja: ayuda a tu lado (lo que te vio hacer) o hace su encargo junto a su casa.
 * Picar / talar / cultivar: busca un bloque que pueda tocar, va, lo rompe con su ritmo y guarda lo que suelta.
 * Fabricar: con las recetas que te vio hacer y lo que haya en el cofre de su casa.
 */
public class AllyWorkGoal extends Goal {
    private final RiderAllyEntity ally;
    @Nullable private BlockPos target;
    private int progress, needed, stuck, scanCooldown, craftCooldown, fullCooldown;
    private boolean placing;      // aplanar: el objetivo es un hueco que rellenar
    private final Set<Long> blacklist = new HashSet<>();
    private final AllyChores.State chore = new AllyChores.State();

    public AllyWorkGoal(RiderAllyEntity ally) {
        this.ally = ally;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return ally.getTarget() == null && ally.workJob() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        clearTarget();
        chore.reset();
        ally.getNavigation().stop();
    }

    private void clearTarget() {
        if (target != null && !placing && ally.level() instanceof ServerLevel level) level.destroyBlockProgress(ally.getId(), target, -1);
        target = null;
        placing = false;
        progress = 0;
        stuck = 0;
    }

    @Override
    public void tick() {
        if (!(ally.level() instanceof ServerLevel level)) return;
        Job job = ally.workJob();
        BlockPos center = ally.workCenter();
        if (job == null || center == null) return;
        int xp = ally.workXp(job);
        if (ally.tickCount % 1200 == 0) blacklist.clear();
        if (fullCooldown > 0) fullCooldown--;

        // entregar lo que lleva encima
        if (ally.bagFull() || (target == null && !ally.bagEmpty() && scanCooldown > 0)) {
            if (deliver(level)) return;
        }

        if (job == Job.CRAFT) {
            craft(level, center, xp);
            return;
        }
        if (job.chore()) {
            AllyChores.tick(ally, level, job, center, xp, chore);
            return;
        }

        if (target == null || !stillValid(level, job, target)) {
            clearTarget();
            if (scanCooldown-- > 0) return;
            scanCooldown = 20;
            target = find(level, job, center);
            if (target == null) {
                if (ally.distanceToSqr(Vec3.atCenterOf(center)) > 25 && ally.tickCount % 20 == 0)
                    ally.getNavigation().moveTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 0.9);
                return;
            }
            needed = placing ? Math.max(4, Math.round(8 / Math.max(1.0F, AllyWork.speed(xp))))
                    : AllyWork.breakTicks(level, target, level.getBlockState(target), xp);
            needed = Math.max(3, Math.round(needed / com.draxer.dexrider.rider.AllyTrust.workBonus(ally.trust())));
        }

        Vec3 c = Vec3.atCenterOf(target);
        ally.getLookControl().setLookAt(c.x, c.y, c.z);
        double hx = c.x - ally.getX(), hz = c.z - ally.getZ(), dy = target.getY() - ally.getY();
        boolean reach = job == Job.CHOP ? hx * hx + hz * hz <= 9.0 && dy >= -2 && dy <= 9   // trepa por el tronco
                : ally.getEyePosition().distanceToSqr(c) <= 3.6 * 3.6;
        if (!reach) {
            if (ally.tickCount % 10 == 0 && !ally.getNavigation().moveTo(c.x, target.getY(), c.z, 1.0)) stuck += 10;
            if (++stuck > 160) {
                blacklist.add(target.asLong());
                clearTarget();
            }
            return;
        }
        ally.getNavigation().stop();
        if (progress % 6 == 0) ally.swing(InteractionHand.MAIN_HAND);
        progress++;
        if (placing) {
            if (progress >= needed) placeFiller(level);
            return;
        }
        level.destroyBlockProgress(ally.getId(), target, Math.min(9, progress * 10 / Math.max(1, needed)));
        if (progress >= needed) breakTarget(level, job);
    }

    // ------------------------------------------------------------------ buscar

    private boolean stillValid(ServerLevel level, Job job, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return switch (job) {
            case MINE -> ally.workZone() != null ? AllyWork.zoneMineable(level, p, s) : AllyWork.isNaturalMineable(s);
            case CHOP -> s.is(net.minecraft.tags.BlockTags.LOGS);
            case FARM -> AllyWork.isRipeCrop(s);
            case CRAFT, FISH, BREED, SMELT, GUARD -> false;
            case FLATTEN -> placing ? AllyWork.needsFill(level, p, s) : AllyWork.zoneMineable(level, p, s);
        };
    }

    /** Picar dentro de la zona marcada: de arriba hacia abajo, todo (o solo el filtro). */
    @Nullable
    private BlockPos findInZone(ServerLevel level, BlockPos min, BlockPos max) {
        List<String> filter = ally.workFilter();
        BlockPos best = null, stuckOne = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            BlockState s = level.getBlockState(p);
            if (!AllyWork.zoneMineable(level, p, s)) continue;
            if (!filter.isEmpty() && !filter.contains(AllyWork.blockId(s))) continue;
            if (!AllyWork.exposed(level, p)) continue;
            if (blacklist.contains(p.asLong())) { if (stuckOne == null || p.getY() > stuckOne.getY()) stuckOne = p.immutable(); continue; }
            double score = -p.getY() * 1000.0 + p.distToCenterSqr(ally.position());
            if (score < bestScore) { bestScore = score; best = p.immutable(); }
        }
        if (best == null && stuckOne != null) {
            // no llega a ninguno: salta encima del que quedo para seguir excavando
            BlockPos up = stuckOne.above();
            if (level.getBlockState(up).isAir() && level.getBlockState(up.above()).isAir()) {
                ally.teleportTo(up.getX() + 0.5, up.getY(), up.getZ() + 0.5);
                blacklist.clear();
                return stuckOne;
            }
        }
        return best;
    }

    /**
     * Aplanar: el suelo queda a la altura de la esquina mas baja. Primero quita lo que sobresale (de arriba hacia
     * abajo), luego rellena hoyos (de abajo hacia arriba).
     */
    @Nullable
    private BlockPos findFlatten(ServerLevel level, BlockPos min, BlockPos max) {
        int ground = min.getY();
        int top = Math.min(ground + AllyWork.FLATTEN_UP, Math.max(max.getY(), ground + AllyWork.FLATTEN_UP));
        placing = false;
        BlockPos best = null, stuckOne = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(new BlockPos(min.getX(), ground + 1, min.getZ()), new BlockPos(max.getX(), top, max.getZ()))) {
            BlockState s = level.getBlockState(p);
            if (!AllyWork.zoneMineable(level, p, s) || s.canBeReplaced()) continue;
            if (!AllyWork.exposed(level, p)) continue;
            if (blacklist.contains(p.asLong())) { if (stuckOne == null || p.getY() > stuckOne.getY()) stuckOne = p.immutable(); continue; }
            double score = -p.getY() * 1000.0 + p.distToCenterSqr(ally.position());
            if (score < bestScore) { bestScore = score; best = p.immutable(); }
        }
        // las plantas y la hierba alta se quitan sin mas
        for (BlockPos p : BlockPos.betweenClosed(new BlockPos(min.getX(), ground + 1, min.getZ()), new BlockPos(max.getX(), Math.min(top, ground + 3), max.getZ()))) {
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && s.canBeReplaced() && s.getFluidState().isEmpty()) level.destroyBlock(p, true, ally);
        }
        if (best != null) return best;
        if (stuckOne != null) {
            BlockPos up = stuckOne.above();
            if (level.getBlockState(up).isAir() && level.getBlockState(up.above()).isAir()) {
                ally.teleportTo(up.getX() + 0.5, up.getY(), up.getZ() + 0.5);
                blacklist.clear();
                return stuckOne;
            }
        }
        // rellenar: el hueco mas bajo que tenga algo solido debajo
        if (!hasFiller(level)) return null;
        BlockPos occupied = null;
        for (BlockPos p : BlockPos.betweenClosed(new BlockPos(min.getX(), ground - AllyWork.FLATTEN_DOWN, min.getZ()), new BlockPos(max.getX(), ground, max.getZ()))) {
            if (blacklist.contains(p.asLong())) continue;
            BlockState s = level.getBlockState(p);
            if (!AllyWork.needsFill(level, p, s)) continue;
            BlockState below = level.getBlockState(p.below());
            if (AllyWork.needsFill(level, p.below(), below) && p.getY() > ground - AllyWork.FLATTEN_DOWN) continue;
            // solo hoyos abiertos hacia arriba (no cuevas bajo el suelo)
            boolean open = true;
            for (int y = p.getY() + 1; y <= ground && open; y++) {
                BlockPos u = new BlockPos(p.getX(), y, p.getZ());
                open = AllyWork.needsFill(level, u, level.getBlockState(u));
            }
            if (!open) continue;
            if (!level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(p)).isEmpty()) {
                if (ally.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(p))) occupied = p.immutable();
                continue;
            }
            double score = p.getY() * 1000.0 + p.distToCenterSqr(ally.position());
            if (score < bestScore) { bestScore = score; best = p.immutable(); }
        }
        if (best == null && occupied != null) {
            // esta parado justo en el hoyo: se sube al suelo de al lado para poder rellenarlo
            for (BlockPos q : BlockPos.betweenClosed(new BlockPos(min.getX() - 1, ground + 1, min.getZ() - 1), new BlockPos(max.getX() + 1, ground + 1, max.getZ() + 1))) {
                if (q.getX() == occupied.getX() && q.getZ() == occupied.getZ()) continue;
                if (level.getBlockState(q).isAir() && level.getBlockState(q.above()).isAir()
                        && !AllyWork.needsFill(level, q.below(), level.getBlockState(q.below()))) {
                    ally.teleportTo(q.getX() + 0.5, q.getY(), q.getZ() + 0.5);
                    break;
                }
            }
            best = occupied;
        }
        placing = best != null;
        return best;
    }

    private boolean hasFiller(ServerLevel level) {
        for (ItemStack s : ally.bagView()) if (AllyWork.isFiller(s)) return true;
        Container chest = AllyWork.chestNear(level, ally.home());
        if (chest == null) return false;
        for (int i = 0; i < chest.getContainerSize(); i++) if (AllyWork.isFiller(chest.getItem(i))) return true;
        return false;
    }

    private void placeFiller(ServerLevel level) {
        BlockPos p = target;
        BlockState fill = ally.bagTakeFiller();
        if (fill == null) fill = AllyWork.takeFiller(AllyWork.chestNear(level, ally.home()));
        if (fill != null && AllyWork.needsFill(level, p, level.getBlockState(p))
                && level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(p)).isEmpty()) {
            level.setBlockAndUpdate(p, fill);
            level.playSound(null, p, fill.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.8F, 1.0F);
            ally.gainWorkXp(Job.FLATTEN, 1);
        } else if (fill != null) {
            ally.bagAdd(new ItemStack(fill.getBlock()));
            blacklist.add(p.asLong());
        }
        clearTarget();
    }

    @Nullable
    private BlockPos find(ServerLevel level, Job job, BlockPos center) {
        if (job == Job.FLATTEN) {
            BlockPos[] z = ally.workZone();
            return z == null ? null : findFlatten(level, z[0], z[1]);
        }
        placing = false;
        BlockPos[] zone = job == Job.MINE ? ally.workZone() : null;
        if (zone != null) return findInZone(level, zone[0], zone[1]);
        boolean helping = ally.isHelping();
        List<String> filter = job == Job.MINE ? ally.workFilter() : List.of();
        int r = helping ? 6 : AllyWork.WORK_RADIUS;
        int down = job == Job.MINE ? (helping ? 4 : 3) : 2;
        int up = job == Job.CHOP ? 9 : 3;
        List<String> learned = ally.workBlocks();
        String preferred = ally.helpBlock();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -down, -r), center.offset(r, up, r))) {
            if (blacklist.contains(p.asLong())) continue;
            BlockState s = level.getBlockState(p);
            if (s.isAir()) continue;
            double score = p.distToCenterSqr(ally.position());
            switch (job) {
                case MINE -> {
                    if (!AllyWork.isNaturalMineable(s) || !AllyWork.exposed(level, p)) continue;
                    String id = AllyWork.blockId(s);
                    boolean ore = AllyWork.isOre(s);
                    if (!filter.isEmpty()) {
                        if (!filter.contains(id)) continue;      // solo lo que le pediste
                    } else if (!learned.contains(id) && !ore) continue;
                    // no cavar donde vive ni bajo sus pies
                    if (!helping && Math.abs(p.getX() - center.getX()) <= 1 && Math.abs(p.getZ() - center.getZ()) <= 1) continue;
                    if (p.getX() == ally.blockPosition().getX() && p.getZ() == ally.blockPosition().getZ() && p.getY() < ally.getY()) continue;
                    if (ore) score -= 400;
                    if (id.equals(preferred)) score -= 200;
                }
                case CHOP -> {
                    if (!AllyWork.isTreeLog(level, p, s)) continue;
                    score += p.getY() * 4;          // de abajo hacia arriba
                }
                case FARM -> {
                    if (!AllyWork.isRipeCrop(s)) continue;
                }
                default -> { continue; }
            }
            if (score < bestScore) { bestScore = score; best = p.immutable(); }
        }
        return best;
    }

    // ------------------------------------------------------------------ romper

    private void breakTarget(ServerLevel level, Job job) {
        BlockPos p = target;
        BlockState s = level.getBlockState(p);
        List<ItemStack> drops = AllyWork.drops(level, p, s, ally);
        level.destroyBlockProgress(ally.getId(), p, -1);
        level.destroyBlock(p, false, ally);
        if (job == Job.FARM && s.getBlock() instanceof CropBlock crop) {
            // resembrar con una de sus semillas
            ItemStack seed = new ItemStack(s.getBlock().asItem());
            for (ItemStack d : drops) {
                if (!seed.isEmpty() && d.is(seed.getItem())) { d.shrink(1); break; }
            }
            level.setBlockAndUpdate(p, crop.getStateForAge(0));
        }
        if (job == Job.CHOP) {
            BlockState below = level.getBlockState(p.below());
            if (AllyWork.canPlantOn(below) && level.getBlockState(p).isAir()) {
                BlockState sap = AllyWork.saplingFor(s);
                if (sap != null && sap.canSurvive(level, p)) level.setBlockAndUpdate(p, sap);
            }
        }
        if (job == Job.MINE && AllyWork.isRareFind(s)) AllyWork.react(level, ally, RiderDialogue.Line.FIND, 600, 1.0F);
        for (ItemStack d : drops) if (!d.isEmpty()) ally.bagAdd(d);
        ally.gainWorkXp(job, 1);
        clearTarget();
    }

    // ------------------------------------------------------------------ fabricar

    private void craft(ServerLevel level, BlockPos home, int xp) {
        if (ally.isHelping()) return;
        if (ally.distanceToSqr(Vec3.atCenterOf(home)) > 16) {
            if (ally.tickCount % 20 == 0) ally.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.9);
            return;
        }
        if (craftCooldown-- > 0) return;
        craftCooldown = Math.round(100 / Math.max(1.0F, AllyWork.speed(xp)));
        Container chest = AllyWork.chestNear(level, home);
        if (chest == null) {
            if (fullCooldown == 0) {
                AllyWork.react(level, ally, RiderDialogue.Line.FULL, 3000, 1.0F);
                fullCooldown = 3000;
            }
            return;
        }
        for (AllySkills.Pattern pattern : ally.workPatterns()) {
            if (AllyWork.craftOnce(level, pattern, chest)) {
                ally.swing(InteractionHand.MAIN_HAND);
                level.playSound(null, ally.getX(), ally.getY(), ally.getZ(), SoundEvents.VILLAGER_WORK_TOOLSMITH, SoundSource.NEUTRAL, 0.6F, 1.1F);
                ally.gainWorkXp(Job.CRAFT, 1);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ entregar

    /** Devuelve true si esta ocupado entregando (caminando). */
    private boolean deliver(ServerLevel level) {
        if (ally.isHelping()) {
            ServerPlayer o = ally.ownerPlayer();
            if (o == null) return false;
            if (ally.distanceTo(o) > 3.5) {
                if (ally.tickCount % 10 == 0) ally.getNavigation().moveTo(o, 1.1);
                return true;
            }
            for (ItemStack s : ally.bagTakeAll()) AllyWork.give(o, s);
            return false;
        }
        BlockPos home = ally.home();
        Container chest = AllyWork.chestNear(level, home);
        if (chest == null) {
            if (ally.bagFull()) {
                if (fullCooldown == 0) {
                    AllyWork.react(level, ally, RiderDialogue.Line.FULL, 3000, 1.0F);
                    fullCooldown = 3000;
                }
                return true;    // espera con las manos llenas
            }
            return false;
        }
        if (ally.distanceToSqr(Vec3.atCenterOf(home)) > 25) {
            if (ally.tickCount % 20 == 0) ally.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1.0);
            return true;
        }
        for (ItemStack s : ally.bagTakeAll()) {
            ItemStack rest = AllyWork.insert(chest, s);
            if (!rest.isEmpty()) ally.bagAdd(rest);
        }
        return false;
    }
}
