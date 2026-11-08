package com.draxer.dexrider.entity;

import com.draxer.dexrider.rider.AllyWork;
import com.draxer.dexrider.rider.FX;
import com.draxer.dexrider.rider.KamenRide;
import com.draxer.dexrider.rider.RiderDialogue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * La vida en casa de los amigos Rider (cuando no te acompanian ni trabajan):
 *  - De noche duermen en una cama libre a 8 bloques de su casa (si no hay, descansan junto a ella). Al amanecer se
 *    levantan. Dan las buenas noches y los buenos dias.
 *  - A veces comen algo del cofre de su casa (y se curan).
 *  - Momentos: dos amigos que se conocen se buscan y tienen su escena (Nagisa le lleva te a Genzo, Mei le desarma
 *    algo a Takumi, Kinoko y Toru con las setas...).
 */
public final class AllyRoutine {
    private AllyRoutine() {}

    public record Moment(KamenRide a, KamenRide b, String key, ParticleOptions fx) {}

    public static final List<Moment> MOMENTS = List.of(
            new Moment(KamenRide.PSYGA, KamenRide.ORGA, "tea", ParticleTypes.CLOUD),
            new Moment(KamenRide.PROTO, KamenRide.CIPHER, "parts", ParticleTypes.ELECTRIC_SPARK),
            new Moment(KamenRide.SPORE, KamenRide.MIRAGE, "mushrooms", ParticleTypes.SPORE_BLOSSOM_AIR),
            new Moment(KamenRide.NINE, KamenRide.FORGE, "study", ParticleTypes.ENCHANT),
            new Moment(KamenRide.WARDEN, KamenRide.CIPHER, "brothers", ParticleTypes.SMOKE),
            new Moment(KamenRide.PSYGA, KamenRide.KAIXA, "siblings", ParticleTypes.HAPPY_VILLAGER),
            new Moment(KamenRide.ORGA, KamenRide.KAIXA, "father", ParticleTypes.CRIT),
            new Moment(KamenRide.NINE, KamenRide.MIRAGE, "training", ParticleTypes.CRIT),
            new Moment(KamenRide.PROTO, KamenRide.KAIXA, "workshop", ParticleTypes.ELECTRIC_SPARK),
            new Moment(KamenRide.SPORE, KamenRide.PSYGA, "garden", ParticleTypes.HAPPY_VILLAGER));

    private static final Map<String, Long> MOMENT_COOLDOWN = new HashMap<>();
    private static final int MOMENT_GAP = 6000;
    public static final int MOMENT_VARIANTS = 2;

    /** Esta en casa sin nada que hacer. */
    public static boolean idleAtHome(RiderAllyEntity a) {
        return a.mode() == RiderAllyEntity.Mode.WANDER && a.home() != null && a.workJob() == null && a.getTarget() == null;
    }

    /** Solo para las pruebas: fuerza noche (true), dia (false) o la hora real (null). */
    @Nullable public static volatile Boolean testNight;

    public static boolean night(ServerLevel level) {
        if (testNight != null) return testNight;
        return !level.isBrightOutside();
    }

    /** Llamado cada tick desde el amigo. Devuelve true si la rutina lo tiene ocupado (no debe pasear). */
    public static boolean tick(RiderAllyEntity a, ServerLevel level) {
        if (!idleAtHome(a)) {
            if (a.isSleeping()) wake(a, level, false);
            return false;
        }
        boolean isNight = night(level);
        if (a.isSleeping()) {
            if (!isNight) wake(a, level, true);
            else {
                a.getNavigation().stop();
                if (a.tickCount % 60 == 0) FX.particle(level, ParticleTypes.NOTE, a.getEyePosition().add(0, 0.4, 0), 1, 0.1, 0.0);
            }
            return true;
        }
        if (isNight) return goToBed(a, level);
        if (a.momentPartner != null) return runMoment(a, level);
        if (a.tickCount % 200 == 0 && level.getRandom().nextInt(20) == 0) eat(a, level);
        if (a.tickCount % 400 == 0 && level.getRandom().nextInt(4) == 0) tryMoment(a, level);
        return false;
    }

    // ------------------------------------------------------------------ dormir

    @Nullable
    private static BlockPos freeBed(RiderAllyEntity a, ServerLevel level) {
        BlockPos home = a.home();
        for (BlockPos p : BlockPos.betweenClosed(home.offset(-8, -2, -8), home.offset(8, 2, 8))) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.HEAD && !s.getValue(BedBlock.OCCUPIED)) return p.immutable();
        }
        return null;
    }

    private static boolean goToBed(RiderAllyEntity a, ServerLevel level) {
        BlockPos bed = freeBed(a, level);
        BlockPos home = a.home();
        if (!a.saidGoodnight) {
            a.saidGoodnight = true;
            if (level.getRandom().nextInt(3) == 0) RiderDialogue.say(level, a, a.rider(), RiderDialogue.Line.SLEEP, 200);
        }
        if (bed == null) {
            // sin cama: descansa junto a su casa
            if (a.distanceToSqr(Vec3.atCenterOf(home)) > 4 && a.tickCount % 20 == 0) a.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.8);
            else if (a.tickCount % 80 == 0) FX.particle(level, ParticleTypes.NOTE, a.getEyePosition().add(0, 0.4, 0), 1, 0.1, 0.0);
            return true;
        }
        if (a.distanceToSqr(Vec3.atCenterOf(bed)) > 4) {
            if (a.tickCount % 20 == 0) a.getNavigation().moveTo(bed.getX() + 0.5, bed.getY(), bed.getZ() + 0.5, 0.8);
            return true;
        }
        a.getNavigation().stop();
        a.startSleeping(bed);
        return true;
    }

    private static void wake(RiderAllyEntity a, ServerLevel level, boolean morning) {
        a.stopSleeping();
        a.saidGoodnight = false;
        if (morning && level.getRandom().nextInt(3) == 0) RiderDialogue.say(level, a, a.rider(), RiderDialogue.Line.WAKE, 200);
    }

    // ------------------------------------------------------------------ comer

    private static void eat(RiderAllyEntity a, ServerLevel level) {
        Container chest = AllyWork.chestNear(level, a.home());
        if (chest == null) return;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack s = chest.getItem(i);
            if (s.isEmpty() || !s.has(net.minecraft.core.component.DataComponents.FOOD)) continue;
            s.shrink(1);
            if (s.isEmpty()) chest.setItem(i, ItemStack.EMPTY);
            chest.setChanged();
            a.swing(InteractionHand.MAIN_HAND);
            level.playSound(null, a.getX(), a.getY(), a.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 0.7F, 1.0F);
            FX.particle(level, ParticleTypes.HAPPY_VILLAGER, a.getEyePosition(), 4, 0.25, 0.0);
            a.heal(4.0F);
            return;
        }
    }

    // ------------------------------------------------------------------ momentos

    private static String pairKey(KamenRide x, KamenRide y) {
        return x.ordinal() < y.ordinal() ? x.id() + "|" + y.id() : y.id() + "|" + x.id();
    }

    @Nullable
    public static Moment momentFor(KamenRide x, KamenRide y) {
        for (Moment m : MOMENTS) if ((m.a() == x && m.b() == y) || (m.a() == y && m.b() == x)) return m;
        return null;
    }

    private static void tryMoment(RiderAllyEntity a, ServerLevel level) {
        if (a.owner() == null) return;
        for (RiderAllyEntity b : level.getEntitiesOfClass(RiderAllyEntity.class, a.getBoundingBox().inflate(12),
                o -> o != a && a.owner().equals(o.owner()) && idleAtHome(o) && !o.isSleeping() && o.momentPartner == null)) {
            Moment m = momentFor(a.rider(), b.rider());
            if (m == null) continue;
            String k = a.owner() + "|" + pairKey(a.rider(), b.rider());
            Long last = MOMENT_COOLDOWN.get(k);
            if (last != null && level.getGameTime() - last < MOMENT_GAP) continue;
            MOMENT_COOLDOWN.put(k, level.getGameTime());
            RiderAllyEntity first = m.a() == a.rider() ? a : b, second = first == a ? b : a;
            first.momentPartner = second;
            first.momentLeader = true;
            first.momentTicks = 0;
            second.momentPartner = first;
            second.momentLeader = false;
            second.momentTicks = 0;
            return;
        }
    }

    private static boolean runMoment(RiderAllyEntity a, ServerLevel level) {
        RiderAllyEntity b = a.momentPartner;
        if (b == null || !b.isAlive() || !idleAtHome(b) || ++a.momentTicks > 240) {
            endMoment(a);
            return false;
        }
        a.getLookControl().setLookAt(b);
        double d = a.distanceTo(b);
        if (d > 2.2 && a.momentTicks < 120) {
            if (a.tickCount % 10 == 0) a.getNavigation().moveTo(b, 0.9);
            return true;
        }
        a.getNavigation().stop();
        if (a.momentLeader) {
            Moment m = momentFor(a.rider(), b.rider());
            if (m != null && a.momentTicks % 30 == 0) FX.particle(level, m.fx(), a.position().add(b.position()).scale(0.5).add(0, 1.3, 0), 4, 0.3, 0.01);
            if (m != null && !a.momentSpoke) {
                a.momentSpoke = true;
                RiderDialogue.forget(a);
                String key = "moment.dexrider." + m.key() + "." + (1 + level.getRandom().nextInt(MOMENT_VARIANTS));
                RiderDialogue.sayKey(level, a, a.rider(), key, 0);
                b.replyLater(a, key + ".r");
                a.swing(InteractionHand.MAIN_HAND);
            }
            if (a.momentTicks > 160) { endMoment(b); endMoment(a); }
        }
        return true;
    }

    private static void endMoment(RiderAllyEntity a) {
        a.momentPartner = null;
        a.momentLeader = false;
        a.momentSpoke = false;
        a.momentTicks = 0;
    }
}
