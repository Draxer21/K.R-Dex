package com.draxer.dexrider.entity;

import com.draxer.dexrider.rider.AllyWork;
import com.draxer.dexrider.rider.AllyWork.Job;
import com.draxer.dexrider.rider.FX;
import com.draxer.dexrider.rider.RiderDialogue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Los quehaceres que no hace falta aprender mirandote (se encargan desde el menu o con su objeto):
 *  - Pescar (cania de pescar): busca agua cerca de su casa, se para en la orilla y pesca.
 *  - Criar animales (rienda): junta parejas de animales de la misma especie con la comida del cofre (hasta 10 por especie).
 *  - Usar los hornos (horno): mete en hornos, ahumadores y altos hornos cercanos lo que se puede cocinar o fundir del
 *    cofre, les pone combustible y guarda lo que sale.
 *  - Vigilar la casa (espada): de noche patrulla alrededor de su casa y ataca a los monstruos desde mas lejos.
 */
public final class AllyChores {
    private AllyChores() {}

    public static final int MAX_PER_SPECIES = 10;

    /** Estado de un quehacer en curso (uno por amigo). */
    public static final class State {
        @Nullable BlockPos water, stand;
        @Nullable Animal feedA, feedB;
        int progress, cooldown, stuck;
        @Nullable BlockPos buildPos;
        @Nullable BlockState buildState;
        long missingSaid;
        void reset() { water = null; stand = null; feedA = null; feedB = null; buildPos = null; buildState = null; progress = 0; stuck = 0; }
    }

    public static void tick(RiderAllyEntity ally, ServerLevel level, Job job, BlockPos center, int xp, State st) {
        float speed = Math.max(1.0F, AllyWork.speed(xp)) * com.draxer.dexrider.rider.AllyTrust.workBonus(ally.trust());
        switch (job) {
            case FISH -> fish(ally, level, center, speed, st);
            case BREED -> breed(ally, level, center, st);
            case SMELT -> smelt(ally, level, center, speed, st);
            case GUARD -> guard(ally, level, center, st);
            case BUILD -> build(ally, level, speed, st);
            default -> {}
        }
    }

    /** Guarda lo que consiguio: en el cofre de su casa si hay sitio, si no se lo queda encima. */
    static void stash(ServerLevel level, RiderAllyEntity ally, ItemStack s) {
        Container chest = AllyWork.chestNear(level, ally.home());
        ItemStack rest = chest == null ? s : AllyWork.insert(chest, s);
        if (!rest.isEmpty()) ally.bagAdd(rest);
    }

    private static void walk(RiderAllyEntity ally, BlockPos p, double speed) {
        if (ally.tickCount % 10 == 0) ally.getNavigation().moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, speed);
    }

    // ------------------------------------------------------------------ pescar

    private static boolean standable(ServerLevel level, BlockPos q) {
        BlockState below = level.getBlockState(q.below());
        return level.getBlockState(q).isAir() && level.getBlockState(q.above()).isAir()
                && !below.canBeReplaced() && below.getFluidState().isEmpty();
    }

    private static void findWater(RiderAllyEntity ally, ServerLevel level, BlockPos center, State st) {
        int r = AllyWork.WORK_RADIUS + 4;
        double best = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -4, -r), center.offset(r, 2, r))) {
            FluidState fs = level.getFluidState(p);
            if (!fs.is(FluidTags.WATER) || !fs.isSource() || !level.getBlockState(p.above()).isAir()) continue;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                for (BlockPos q : new BlockPos[]{p.relative(d), p.relative(d).above()}) {
                    if (!standable(level, q)) continue;
                    double score = q.distSqr(center);
                    if (score < best) { best = score; st.water = p.immutable(); st.stand = q.immutable(); }
                }
            }
        }
    }

    private static void fish(RiderAllyEntity ally, ServerLevel level, BlockPos center, float speed, State st) {
        if (st.water == null || !level.getFluidState(st.water).is(FluidTags.WATER)) {
            st.reset();
            if (st.cooldown-- > 0) return;
            st.cooldown = 60;
            findWater(ally, level, center, st);
            if (st.water == null) {
                var o = ally.ownerPlayer();
                if (o != null && ally.tickCount % 1200 == 0)
                    o.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.dexrider.ally_no_water", ally.rider().displayName()), true);
                return;
            }
        }
        Vec3 w = Vec3.atCenterOf(st.water);
        ally.getLookControl().setLookAt(w.x, w.y, w.z);
        if (ally.distanceToSqr(Vec3.atBottomCenterOf(st.stand)) > 1.5) {
            walk(ally, st.stand, 0.9);
            if (++st.stuck > 300) st.reset();
            return;
        }
        ally.getNavigation().stop();
        st.stuck = 0;
        int needed = Math.max(60, Math.round(240 / speed));
        if (++st.progress % 30 == 0) FX.particle(level, ParticleTypes.FISHING, w.add(0, 0.5, 0), 2, 0.1, 0.0);
        if (st.progress < needed) return;
        st.progress = 0;
        ally.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, w.x, w.y, w.z, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.6F, 1.0F);
        FX.particle(level, ParticleTypes.SPLASH, w.add(0, 0.6, 0), 8, 0.25, 0.0);
        ItemStack catchStack = rollCatch(level.getRandom());
        if (catchStack.is(Items.NAUTILUS_SHELL) || catchStack.is(Items.NAME_TAG)) AllyWork.react(level, ally, RiderDialogue.Line.FIND, 600, 1.0F);
        stash(level, ally, catchStack);
        ally.gainWorkXp(Job.FISH, 1);
    }

    /** Una tabla propia y sencilla (parecida a la de pescar, sin encantamientos). */
    static ItemStack rollCatch(RandomSource r) {
        float f = r.nextFloat();
        if (f < 0.55F) return new ItemStack(Items.COD);
        if (f < 0.78F) return new ItemStack(Items.SALMON);
        if (f < 0.80F) return new ItemStack(Items.TROPICAL_FISH);
        if (f < 0.90F) return new ItemStack(Items.PUFFERFISH);
        if (f < 0.97F) {
            ItemStack[] junk = {new ItemStack(Items.STICK), new ItemStack(Items.BONE), new ItemStack(Items.LEATHER),
                    new ItemStack(Items.STRING), new ItemStack(Items.LILY_PAD), new ItemStack(Items.INK_SAC)};
            return junk[r.nextInt(junk.length)];
        }
        return new ItemStack(r.nextBoolean() ? Items.NAUTILUS_SHELL : Items.NAME_TAG);
    }

    // ------------------------------------------------------------------ criar

    private static boolean ready(Animal a) {
        return a.isAlive() && !a.isBaby() && a.getAge() == 0 && a.canFallInLove();
    }

    private static int takeFood(Container chest, Animal a, int want) {
        int got = 0;
        for (int i = 0; i < chest.getContainerSize() && got < want; i++) {
            ItemStack s = chest.getItem(i);
            if (s.isEmpty() || !a.isFood(s)) continue;
            int n = Math.min(want - got, s.getCount());
            s.shrink(n);
            got += n;
            if (s.isEmpty()) chest.setItem(i, ItemStack.EMPTY);
        }
        if (got > 0) chest.setChanged();
        return got;
    }

    private static boolean hasFood(Container chest, Animal a, int want) {
        int n = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack s = chest.getItem(i);
            if (!s.isEmpty() && a.isFood(s)) n += s.getCount();
        }
        return n >= want;
    }

    private static void breed(RiderAllyEntity ally, ServerLevel level, BlockPos center, State st) {
        Container chest = AllyWork.chestNear(level, ally.home());
        if (st.feedA == null) {
            if (st.cooldown-- > 0) return;
            st.cooldown = 100;
            if (chest == null) return;
            int r = AllyWork.WORK_RADIUS + 4;
            Map<EntityType<?>, List<Animal>> byType = new HashMap<>();
            Map<EntityType<?>, Integer> count = new HashMap<>();
            for (Animal a : level.getEntitiesOfClass(Animal.class, new net.minecraft.world.phys.AABB(center).inflate(r, 4, r), Animal::isAlive)) {
                count.merge(a.getType(), 1, Integer::sum);
                if (ready(a)) byType.computeIfAbsent(a.getType(), k -> new ArrayList<>()).add(a);
            }
            for (var e : byType.entrySet()) {
                if (e.getValue().size() < 2 || count.getOrDefault(e.getKey(), 0) >= MAX_PER_SPECIES) continue;
                Animal a = e.getValue().get(0), b = e.getValue().get(1);
                if (!hasFood(chest, a, 2)) continue;
                st.feedA = a;
                st.feedB = b;
                st.stuck = 0;
                return;
            }
            return;
        }
        Animal next = ready(st.feedA) ? st.feedA : st.feedB != null && ready(st.feedB) ? st.feedB : null;
        if (next == null || chest == null || ++st.stuck > 400) {
            st.reset();
            return;
        }
        ally.getLookControl().setLookAt(next);
        if (ally.distanceTo(next) > 2.5) {
            if (ally.tickCount % 10 == 0) ally.getNavigation().moveTo(next, 0.9);
            return;
        }
        ally.getNavigation().stop();
        if (takeFood(chest, next, 1) == 0) { st.reset(); return; }
        ally.swing(InteractionHand.MAIN_HAND);
        next.setInLove(null);
        if (next == st.feedA) st.feedA = null;
        else st.feedB = null;
        if (st.feedA == null && st.feedB != null) { st.feedA = st.feedB; st.feedB = null; }
        else if (st.feedA == null) ally.gainWorkXp(Job.BREED, 2);
    }

    // ------------------------------------------------------------------ hornos

    private static boolean cookable(ServerLevel level, AbstractFurnaceBlockEntity f, ItemStack s) {
        if (s.isEmpty()) return false;
        SingleRecipeInput in = new SingleRecipeInput(s);
        var rm = level.getServer().getRecipeManager();
        if (f instanceof SmokerBlockEntity) return rm.getRecipeFor(RecipeType.SMOKING, in, level).isPresent();
        if (f instanceof BlastFurnaceBlockEntity) return rm.getRecipeFor(RecipeType.BLASTING, in, level).isPresent();
        return rm.getRecipeFor(RecipeType.SMELTING, in, level).isPresent();
    }

    public static List<AbstractFurnaceBlockEntity> furnacesNear(ServerLevel level, BlockPos home) {
        List<AbstractFurnaceBlockEntity> out = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(home.offset(-6, -2, -6), home.offset(6, 3, 6)))
            if (level.getBlockEntity(p) instanceof AbstractFurnaceBlockEntity f) out.add(f);
        return out;
    }

    private static void smelt(RiderAllyEntity ally, ServerLevel level, BlockPos home, float speed, State st) {
        if (ally.distanceToSqr(Vec3.atCenterOf(home)) > 16) {
            walk(ally, home, 0.9);
            return;
        }
        if (st.cooldown-- > 0) return;
        st.cooldown = Math.max(10, Math.round(40 / speed));
        Container chest = AllyWork.chestNear(level, home);
        if (chest == null) {
            AllyWork.react(level, ally, RiderDialogue.Line.FULL, 3000, 1.0F);
            return;
        }
        for (AbstractFurnaceBlockEntity f : furnacesNear(level, home)) {
            // 1) sacar lo que ya esta hecho
            ItemStack out = f.getItem(2);
            if (!out.isEmpty()) {
                ItemStack rest = AllyWork.insert(chest, out.copy());
                if (rest.getCount() != out.getCount()) {
                    f.setItem(2, rest);
                    act(ally, level, f.getBlockPos(), true);
                    return;
                }
            }
            // 2) meter algo que cocinar
            if (f.getItem(0).isEmpty()) {
                for (int i = 0; i < chest.getContainerSize(); i++) {
                    ItemStack s = chest.getItem(i);
                    if (!cookable(level, f, s) || f.canPlaceItem(1, s)) continue;   // el combustible no se cocina
                    f.setItem(0, s.split(Math.min(16, s.getCount())));
                    if (s.isEmpty()) chest.setItem(i, ItemStack.EMPTY);
                    chest.setChanged();
                    act(ally, level, f.getBlockPos(), false);
                    return;
                }
            }
            // 3) combustible si hay algo que cocinar
            if (!f.getItem(0).isEmpty() && f.getItem(1).isEmpty()) {
                for (int i = 0; i < chest.getContainerSize(); i++) {
                    ItemStack s = chest.getItem(i);
                    if (s.isEmpty() || !f.canPlaceItem(1, s)) continue;
                    f.setItem(1, s.split(Math.min(8, s.getCount())));
                    if (s.isEmpty()) chest.setItem(i, ItemStack.EMPTY);
                    chest.setChanged();
                    act(ally, level, f.getBlockPos(), false);
                    return;
                }
            }
        }
    }

    private static void act(RiderAllyEntity ally, ServerLevel level, BlockPos p, boolean took) {
        ally.getLookControl().setLookAt(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
        ally.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, p, took ? SoundEvents.ITEM_PICKUP : SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.6F, 1.0F);
        ally.gainWorkXp(Job.SMELT, 1);
    }

    // ------------------------------------------------------------------ vigilar

    private static void guard(RiderAllyEntity ally, ServerLevel level, BlockPos home, State st) {
        boolean night = AllyRoutine.night(level);
        if (st.cooldown-- > 0) return;
        RandomSource r = level.getRandom();
        if (night) {
            st.cooldown = 80 + r.nextInt(60);
            BlockPos p = home.offset(r.nextInt(17) - 8, 0, r.nextInt(17) - 8);
            ally.getNavigation().moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0.8);
            if (r.nextInt(4) == 0) FX.particle(level, ParticleTypes.FLAME, ally.getEyePosition().add(0, 0.3, 0), 1, 0.05, 0.0);
        } else {
            st.cooldown = 200;
            if (ally.distanceToSqr(Vec3.atCenterOf(home)) > 9) ally.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.8);
        }
    }

    // ------------------------------------------------------------------ construir copiando

    private static boolean creative(RiderAllyEntity ally) {
        var o = ally.ownerPlayer();
        return o != null && o.isCreative();
    }

    private static boolean chestHas(@Nullable Container chest, net.minecraft.world.item.Item it) {
        if (chest == null) return false;
        for (int i = 0; i < chest.getContainerSize(); i++) if (chest.getItem(i).is(it)) return true;
        return false;
    }

    private static boolean chestTake(@Nullable Container chest, net.minecraft.world.item.Item it) {
        if (chest == null) return false;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack s = chest.getItem(i);
            if (!s.is(it)) continue;
            s.shrink(1);
            if (s.isEmpty()) chest.setItem(i, ItemStack.EMPTY);
            chest.setChanged();
            return true;
        }
        return false;
    }

    private static boolean occupied(ServerLevel level, BlockPos p) {
        return !level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(p)).isEmpty();
    }

    private static void build(RiderAllyEntity ally, ServerLevel level, float speed, State st) {
        var e = ally.skillsEntry();
        if (!e.hasPlan() || !e.hasBuild()) { ally.finishBuild(level, false); return; }
        if (!level.dimension().identifier().toString().equals(e.buildDim())) return;
        Container chest = AllyWork.chestNear(level, ally.home());
        boolean free = creative(ally);
        if (st.buildPos == null) {
            if (st.cooldown-- > 0) return;
            st.cooldown = 8;
            int pending = 0;
            ItemStack missing = ItemStack.EMPTY;
            for (com.draxer.dexrider.rider.AllyBlueprint.Cell c : com.draxer.dexrider.rider.AllyBlueprint.cells(e)) {
                BlockState now = level.getBlockState(c.pos());
                if (now.equals(c.state())) continue;
                if (!now.canBeReplaced()) continue;          // algo tuyo en medio: no lo rompe
                pending++;
                if (!c.state().canSurvive(level, c.pos()) || occupied(level, c.pos())) continue;
                ItemStack cost = com.draxer.dexrider.rider.AllyBlueprint.cost(c.state());
                if (!cost.isEmpty() && !free && !ally.bagHas(cost.getItem()) && !chestHas(chest, cost.getItem())) {
                    if (missing.isEmpty()) missing = cost;
                    continue;
                }
                st.buildPos = c.pos();
                st.buildState = c.state();
                st.progress = 0;
                st.stuck = 0;
                return;
            }
            if (pending == 0) { ally.finishBuild(level, true); return; }
            st.cooldown = 100;
            var o = ally.ownerPlayer();
            if (!missing.isEmpty() && o != null && level.getGameTime() - st.missingSaid > 1200) {
                st.missingSaid = level.getGameTime();
                o.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.dexrider.ally_build_missing",
                        ally.rider().displayName(), missing.getHoverName()), true);
                AllyWork.react(level, ally, RiderDialogue.Line.FULL, 3000, 0.5F);
            }
            return;
        }
        BlockPos p = st.buildPos;
        Vec3 c = Vec3.atCenterOf(p);
        ally.getLookControl().setLookAt(c.x, c.y, c.z);
        double hx = c.x - ally.getX(), hz = c.z - ally.getZ(), dy = p.getY() - ally.getY();
        boolean reach = hx * hx + hz * hz <= 16 && dy >= -4 && dy <= 6;
        if (!reach && ++st.stuck < 200) {
            if (ally.tickCount % 10 == 0) ally.getNavigation().moveTo(c.x, p.getY(), c.z, 1.0);
            return;
        }
        ally.getNavigation().stop();
        if (++st.progress < Math.max(3, Math.round(10 / speed))) {
            if (st.progress % 4 == 0) ally.swing(InteractionHand.MAIN_HAND);
            return;
        }
        BlockState want = st.buildState;
        st.buildPos = null;
        st.buildState = null;
        if (want == null || !level.getBlockState(p).canBeReplaced() || occupied(level, p)) return;
        ItemStack cost = com.draxer.dexrider.rider.AllyBlueprint.cost(want);
        if (!cost.isEmpty() && !free && !ally.bagTake(cost.getItem()) && !chestTake(chest, cost.getItem())) return;
        level.setBlock(p, want, 3);
        level.playSound(null, p, want.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.8F, 1.0F);
        ally.swing(InteractionHand.MAIN_HAND);
        ally.gainWorkXp(Job.BUILD, 1);
    }
}
