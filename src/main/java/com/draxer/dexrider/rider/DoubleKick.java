package com.draxer.dexrider.rider;

import com.draxer.dexrider.entity.RiderAllyEntity;
import com.draxer.dexrider.registry.DexSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ataque combinado (etapa 7): Shift + Final Attack Ride con un amigo Rider cerca.
 *
 *  1. LLAMADA (0-4): el amigo se pone a tu lado, los dos miran al objetivo, el amigo responde y aparece el titulo.
 *  2. SUBIDA (4-16): saltan juntos; cada uno con un anillo de su color; se quedan un instante arriba.
 *  3. CARTAS (16): dos filas de cartas holograficas, una de cada color, que se cruzan en el objetivo.
 *  4. PICADA (16-56): patada doble en picada, persiguiendo al objetivo; las estelas se trenzan.
 *  5. IMPACTO: dano grande al objetivo y en area, explosion con los dos colores y el remate propio de la pareja.
 */
public final class DoubleKick {
    private DoubleKick() {}

    private static final int CALL = 4, RISE = 16, MAX = 60, CARDS = 4;

    private static final class State {
        final RiderAllyEntity ally;
        final LivingEntity target;
        final int colorA, colorB;
        final List<Vec3> cardsA = new ArrayList<>(), cardsB = new ArrayList<>();
        int ticks, passed;
        State(RiderAllyEntity ally, LivingEntity target, int a, int b) { this.ally = ally; this.target = target; colorA = a; colorB = b; }
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    public static boolean isActive(ServerPlayer p) { return ACTIVE.containsKey(p.getUUID()); }

    /** El amigo con quien se haria el ataque (el mas cercano), o null. */
    @Nullable
    public static RiderAllyEntity partner(ServerLevel level, ServerPlayer p) {
        List<RiderAllyEntity> allies = Combos.allies(level, p);
        return allies.isEmpty() ? null : allies.get(0);
    }

    /** Intenta empezar. Devuelve false (con un aviso) si no hay amigo u objetivo: entonces va el Final Attack normal. */
    public static boolean tryStart(ServerLevel level, ServerPlayer p) {
        if (isActive(p) || RiderKick.isKicking(p) || Scheduler.busy(p)) return false;
        RiderAllyEntity ally = partner(level, p);
        if (ally == null) {
            p.sendSystemMessage(Component.translatable("message.dexrider.double_no_ally"), true);
            return false;
        }
        LivingEntity target = Combos.target(level, p);
        if (target == null) {
            p.sendSystemMessage(Component.translatable("message.dexrider.double_no_target"), true);
            return false;
        }
        start(level, p, ally, target);
        return true;
    }

    public static void start(ServerLevel level, ServerPlayer p, RiderAllyEntity ally, LivingEntity target) {
        KamenRide me = KamenRide.current(p), them = ally.rider();
        int colorA = me == KamenRide.DEX ? FX.MAGENTA : me.color;
        State st = new State(ally, target, colorA, them.color);
        ACTIVE.put(p.getUUID(), st);

        // el amigo se pone a tu lado (a la derecha, mirando al objetivo)
        Vec3 to = target.position().subtract(p.position());
        Vec3 flat = new Vec3(to.x, 0, to.z).lengthSqr() < 1e-4 ? p.getLookAngle().multiply(1, 0, 1) : new Vec3(to.x, 0, to.z);
        flat = flat.normalize();
        Vec3 side = new Vec3(-flat.z, 0, flat.x);
        Vec3 spot = p.position().add(side.scale(2.0));
        if (ally.distanceToSqr(spot) > 4) {
            FX.particle(level, ParticleTypes.END_ROD, FX.center(ally), 14, 0.4, 0.05);
            ally.teleportTo(spot.x, p.getY(), spot.z);
            FX.particle(level, ParticleTypes.END_ROD, spot.add(0, 1, 0), 14, 0.4, 0.05);
        }
        ally.setTarget(target);
        ally.getNavigation().stop();
        ally.comboLock = MAX + 10;
        ally.setComboPose(true);
        ally.getLookControl().setLookAt(target);

        Component name = Component.translatable("combo.dexrider." + them.id());
        p.connection.send(new ClientboundSetTitlesAnimationPacket(4, 40, 10));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("title.dexrider.double_final")));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("title.dexrider.double_sub", them.displayName(), name)));
        DexSounds.play(level, p, DexSounds.VOICE_FINAL_ATTACK_RIDE, 1.0F, 1.0F);
        RiderDialogue.forget(ally);
        RiderDialogue.sayKey(level, ally, them, "combo.dexrider.call." + them.id(), 0);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2F, 1.2F);
    }

    /** Cada tick del jugador en el servidor. */
    public static void tick(ServerLevel level, ServerPlayer p) {
        State st = ACTIVE.get(p.getUUID());
        if (st == null) return;
        RiderAllyEntity a = st.ally;
        p.resetFallDistance();
        a.resetFallDistance();
        int t = ++st.ticks;
        if (!p.isAlive() || !Transformation.isTransformed(p) || !a.isAlive()) { finish(level, p, st, false); return; }
        boolean targetOk = st.target.isAlive();
        Vec3 aim = targetOk ? FX.center(st.target) : p.position().add(p.getLookAngle().scale(8));
        a.getNavigation().stop();
        a.getLookControl().setLookAt(aim.x, aim.y, aim.z);

        if (t == CALL) {
            com.draxer.dexrider.network.PosePayload.send(p, "kick");
            p.setDeltaMovement(0, 1.35, 0);
            p.hurtMarked = true;
            a.setDeltaMovement(0, 1.3, 0);
            a.hurtMarked = true;
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.5F, 0.7F);
        }
        if (t < RISE) {
            if (t > CALL) {
                FX.ring(level, p, st.colorA, 0.9, 1);
                FX.ring(level, a, st.colorB, 0.9, 1);
            }
            if (t > CALL + 8) {           // se quedan arriba un instante
                p.setDeltaMovement(p.getDeltaMovement().multiply(0.3, 0.1, 0.3)); p.hurtMarked = true;
                a.setDeltaMovement(a.getDeltaMovement().multiply(0.3, 0.1, 0.3)); a.hurtMarked = true;
            }
            return;
        }
        if (t == RISE) {
            st.cardsA.addAll(FX.cardPath(level, p.position().add(0, 1, 0), aim, CARDS, st.colorA));
            st.cardsB.addAll(FX.cardPath(level, a.position().add(0, 1, 0), aim, CARDS, st.colorB));
            level.playSound(null, p.getX(), p.getY(), p.getZ(), DexSounds.FAR_CHARGE.get(), SoundSource.PLAYERS, 1.6F, 0.9F);
        }

        // ---- picada doble ----
        dive(p, aim);
        dive(a, aim);
        Vec3 bp = p.position().add(0, 1, 0), ba = a.position().add(0, 1, 0);
        st.cardsA.removeIf(c -> pass(level, st, c, bp));
        st.cardsB.removeIf(c -> pass(level, st, c, ba));
        if (t % 2 == 0) {
            for (Vec3 c : st.cardsA) FX.card(level, c, aim.subtract(c).normalize(), st.colorA);
            for (Vec3 c : st.cardsB) FX.card(level, c, aim.subtract(c).normalize(), st.colorB);
        }
        // estelas trenzadas: cada uno deja su color y un hilo del color del otro
        double ang = t * 0.9;
        Vec3 twist = new Vec3(Math.cos(ang) * 0.5, Math.sin(ang) * 0.5, 0);
        FX.dust(level, st.colorA, 1.8F, p.position().add(0, 0.4, 0), 4, 0.12);
        FX.dust(level, st.colorB, 1.2F, p.position().add(0, 0.4, 0).add(twist), 2, 0.05);
        FX.dust(level, st.colorB, 1.8F, a.position().add(0, 0.4, 0), 4, 0.12);
        FX.dust(level, st.colorA, 1.2F, a.position().add(0, 0.4, 0).subtract(twist), 2, 0.05);

        boolean hit = targetOk && (p.getBoundingBox().inflate(0.8).intersects(st.target.getBoundingBox())
                || a.getBoundingBox().inflate(0.8).intersects(st.target.getBoundingBox()));
        boolean landed = t > RISE + 4 && (p.onGround() || p.horizontalCollision) && (a.onGround() || a.horizontalCollision);
        if (hit || landed || t > MAX || (!targetOk && t > RISE + 6)) finish(level, p, st, true);
    }

    private static void dive(net.minecraft.world.entity.Entity e, Vec3 aim) {
        Vec3 dir = aim.subtract(e.position().add(0, 0.6, 0)).normalize();
        e.setDeltaMovement(dir.scale(1.75));
        e.hurtMarked = true;
    }

    private static boolean pass(ServerLevel level, State st, Vec3 card, Vec3 body) {
        if (card.distanceTo(body) > 1.9) return false;
        st.passed++;
        FX.particle(level, ParticleTypes.END_ROD, card, 10, 0.35, 0.05);
        level.playSound(null, card.x, card.y, card.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5F, 1.1F + st.passed * 0.08F);
        return true;
    }

    private static void finish(ServerLevel level, ServerPlayer p, State st, boolean impact) {
        ACTIVE.remove(p.getUUID());
        RiderAllyEntity a = st.ally;
        a.comboLock = 0;
        a.setComboPose(false);
        if (!impact) return;
        Vec3 c = st.target.isAlive() ? FX.center(st.target) : p.position().add(0, 1, 0);
        float dmg = 30.0F + 4.0F * st.passed;     // 8 cartas -> 62 (antes del escalado del mazo)
        if (st.target.isAlive()) {
            FX.hit(level, p, st.target, dmg);
            FX.push(st.target, p.position(), 2.4, 0.7);
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(5.0),
                e -> e != p && e != st.target && e.isAlive() && Combos.foe(p, e))) {
            FX.hit(level, p, e, dmg * 0.45F);
            FX.push(e, c, 1.2, 0.4);
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2));
        }
        // explosion con los dos colores: dos anillos que se cruzan y una columna
        for (int k = 0; k < 2; k++) {
            int col = k == 0 ? st.colorA : st.colorB;
            for (int deg = 0; deg < 360; deg += 10) {
                double r = Math.toRadians(deg);
                Vec3 ring = k == 0 ? new Vec3(Math.cos(r) * 3.2, Math.sin(r) * 3.2, 0) : new Vec3(0, Math.sin(r) * 3.2, Math.cos(r) * 3.2);
                FX.dust(level, col, 1.8F, c.add(ring), 1, 0.02);
            }
            for (double y = 0; y < 7; y += 0.35) FX.dust(level, col, 1.6F, c.add(k == 0 ? 0.3 : -0.3, y - 1, 0), 2, 0.15);
        }
        FX.particle(level, ParticleTypes.EXPLOSION_EMITTER, c, 3, 0.9, 0);
        FX.particle(level, ParticleTypes.END_ROD, c, 40, 0.6, 0.3);
        level.playSound(null, c.x, c.y, c.z, DexSounds.FAR_IMPACT.get(), SoundSource.PLAYERS, 2.0F, 0.75F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.4F, 0.9F);
        // los dos rebotan hacia atras y caen de pie
        for (net.minecraft.world.entity.Entity e : new net.minecraft.world.entity.Entity[]{p, a}) {
            Vec3 back = e.position().subtract(c).multiply(1, 0, 1);
            back = back.lengthSqr() < 1e-4 ? Vec3.ZERO : back.normalize();
            e.setDeltaMovement(back.scale(0.6).add(0, 0.55, 0));
            e.hurtMarked = true;
        }
        p.sendSystemMessage(Component.translatable("message.dexrider.double_done", a.rider().displayName(),
                Component.translatable("combo.dexrider." + a.rider().id())), true);
        Scheduler.later(p, 20, () -> { if (a.isAlive()) RiderDialogue.sayKey(level, a, a.rider(), "combo.dexrider.after." + a.rider().id(), 0); });
        DexAdvancements.award(p, "combo");
        AllyTrust.onFight(level, p, a);
    }

    public static void forget(UUID id) { ACTIVE.remove(id); }
}
