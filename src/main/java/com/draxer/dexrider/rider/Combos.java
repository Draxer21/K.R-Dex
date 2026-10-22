package com.draxer.dexrider.rider;

import com.draxer.dexrider.entity.RiderAllyEntity;
import com.draxer.dexrider.entity.RiderNpcAttacks;
import com.draxer.dexrider.registry.DexSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Combos entre Riders. Al usar el Final Attack Ride:
 *  - tus Riders amigos cerca (invocados, dobles o proyecciones del K-Touch) lanzan a la vez su ataque
 *    especial contra tu objetivo, y los dos remates se juntan en un golpe combinado con nombre propio;
 *  - si otro jugador transformado uso su Final Attack Ride cerca en los ultimos 3 s, los dos se combinan.
 *
 * Cada pareja tiene su forma: CRUZ (dos rayos que se cruzan en el objetivo), PILAR (lo lanza hacia arriba y
 * cae), ANILLO (onda que se cierra sobre el objetivo).
 */
public final class Combos {
    private Combos() {}

    public enum Style { CROSS, PILLAR, RING }

    private record Recent(long tick, UUID target, Vec3 pos) {}
    private static final Map<UUID, Recent> RECENT = new HashMap<>();

    public static Style style(KamenRide k) {
        return switch (k) {
            case CIPHER, KAIXA, MIRAGE, PROTO -> Style.CROSS;
            case FORGE, ORGA, WARDEN -> Style.PILLAR;
            default -> Style.RING;
        };
    }

    /** Llamado tras activar el Final Attack Ride (ya transformado). Devuelve cuantos se unieron. */
    public static int onFinisher(ServerLevel level, ServerPlayer p) {
        long now = level.getGameTime();
        LivingEntity target = target(level, p);
        Recent mine = new Recent(now, target != null ? target.getUUID() : null, target != null ? FX.center(target) : null);

        // 1) otro jugador remato hace poco y cerca
        for (ServerPlayer other : level.players()) {
            if (other == p || other.distanceToSqr(p) > 24 * 24 || !Transformation.isTransformed(other)) continue;
            Recent r = RECENT.get(other.getUUID());
            if (r == null || now - r.tick > 60) continue;
            RECENT.remove(other.getUUID());
            LivingEntity t = target != null ? target : (r.target != null && level.getEntity(r.target) instanceof LivingEntity le && le.isAlive() ? le : null);
            if (t == null) continue;
            KamenRide partner = KamenRide.current(other);
            fire(level, p, t, List.of(FX.center(p), FX.center(other)), new int[]{KamenRide.current(p).color, partner.color},
                    style(partner), 2, Component.translatable("combo.dexrider.players"), other.getDisplayName());
            DexAdvancements.award(p, "combo"); DexAdvancements.award(other, "combo");
            return 1;
        }
        RECENT.put(p.getUUID(), mine);
        if (target == null) return 0;

        // 2) tus amigos Rider cerca
        List<RiderAllyEntity> allies = allies(level, p);
        if (allies.isEmpty()) return 0;
        List<Vec3> from = new ArrayList<>();
        int[] colors = new int[allies.size() + 1];
        colors[0] = KamenRide.current(p).color;
        from.add(FX.center(p));
        for (int i = 0; i < allies.size(); i++) {
            RiderAllyEntity a = allies.get(i);
            a.setTarget(target);
            RiderNpcAttacks.special(level, a, target);
            from.add(FX.center(a));
            colors[i + 1] = a.rider().color;
        }
        RiderAllyEntity lead = allies.get(0);
        Component name = allies.size() >= 3
                ? Component.translatable("combo.dexrider.all")
                : Component.translatable("combo.dexrider." + lead.rider().id());
        fire(level, p, target, from, colors, style(lead.rider()), allies.size() + 1, name, lead.rider().displayName());
        DexAdvancements.award(p, "combo");
        return allies.size();
    }

    /** Objetivo: el que miras, o el ultimo al que golpeaste si sigue cerca. */
    @Nullable
    static LivingEntity target(ServerLevel level, ServerPlayer p) {
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(24), e -> foe(p, e))) {
            Vec3 to = FX.center(e).subtract(eye);
            double d = to.length(), dot = to.normalize().dot(look);
            if (d > 24 || dot < 0.85 || !p.hasLineOfSight(e)) continue;
            double score = d * (2.0 - dot);
            if (score < bestScore) { bestScore = score; best = e; }
        }
        if (best == null && p.getLastHurtMob() != null && p.getLastHurtMob().isAlive() && p.distanceTo(p.getLastHurtMob()) < 24
                && foe(p, p.getLastHurtMob())) best = p.getLastHurtMob();
        return best;
    }

    private static boolean foe(ServerPlayer p, LivingEntity e) {
        if (e == p || !e.isAlive() || e instanceof Player) return false;
        if (e instanceof com.draxer.dexrider.entity.RiderMob rm && rm.isFriendlyTo(p)) return false;
        return e instanceof net.minecraft.world.entity.monster.Enemy || e instanceof com.draxer.dexrider.entity.RiderMob
                || e.getLastHurtByMob() == p || (e instanceof net.minecraft.world.entity.Mob m && m.getTarget() == p);
    }

    static List<RiderAllyEntity> allies(ServerLevel level, ServerPlayer p) {
        List<RiderAllyEntity> out = new ArrayList<>(level.getEntitiesOfClass(RiderAllyEntity.class, p.getBoundingBox().inflate(16),
                a -> a.isAlive() && p.getUUID().equals(a.owner()) && !a.isCivilian()));
        if (KTouch.isDragged(p)) out.removeIf(a -> a.mode() == RiderAllyEntity.Mode.PROJECTION);   // la correa te los quita
        out.sort(java.util.Comparator.comparingDouble(a -> a.distanceToSqr(p)));
        return out.size() > 4 ? out.subList(0, 4) : out;
    }

    /** El golpe combinado (llega 18 ticks despues, cuando los dos remates coinciden). */
    private static void fire(ServerLevel level, ServerPlayer p, LivingEntity target, List<Vec3> from, int[] colors,
                             Style style, int members, Component comboName, Component partner) {
        p.sendSystemMessage(Component.translatable("message.dexrider.combo", partner, comboName), true);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.4F);
        for (int t = 2; t <= 16; t += 2) {
            final int tt = t;
            Scheduler.later(p, t, () -> {
                if (!target.isAlive()) return;
                Vec3 c = FX.center(target);
                for (int i = 0; i < from.size(); i++) {
                    Vec3 a = from.get(i);
                    Vec3 head = a.add(c.subtract(a).scale(tt / 16.0));
                    FX.dust(level, colors[i % colors.length], 1.5F, head, 3, 0.08);
                }
            });
        }
        Scheduler.later(p, 18, () -> {
            if (!target.isAlive()) return;
            Vec3 c = FX.center(target);
            float dmg = 10.0F + 6.0F * (members - 1);
            switch (style) {
                case CROSS -> {
                    for (int i = 0; i < from.size(); i++) FX.line(level, from.get(i), c, colors[i % colors.length], 1.4F, 0.4);
                    level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
                    FX.hit(level, p, target, dmg * 1.15F);
                }
                case PILLAR -> {
                    for (double y = 0; y < 6; y += 0.4) FX.dust(level, colors[(int) (y * 2) % colors.length], 1.8F, target.position().add(0, y, 0), 3, 0.3);
                    FX.hit(level, p, target, dmg);
                    target.setDeltaMovement(target.getDeltaMovement().add(0, 1.1, 0));
                    target.hurtMarked = true;
                    Scheduler.later(p, 14, () -> { if (target.isAlive()) FX.hit(level, p, target, dmg * 0.4F); });
                }
                case RING -> {
                    for (int k = 0; k < colors.length; k++) {
                        double r = 3.5 - k * 0.8;
                        for (int a = 0; a < 360; a += 15) {
                            double rad = Math.toRadians(a);
                            FX.dust(level, colors[k], 1.6F, c.add(Math.cos(rad) * r, -0.6 + k * 0.3, Math.sin(rad) * r), 1, 0.02);
                        }
                    }
                    for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(3.0), e -> foe(p, e))) {
                        FX.hit(level, p, e, e == target ? dmg : dmg * 0.5F);
                        e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2));
                    }
                }
            }
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            level.playSound(null, c.x, c.y, c.z, DexSounds.FAR_IMPACT.get(), SoundSource.PLAYERS, 1.6F, 0.8F);
        });
    }

    public static void forget(UUID id) { RECENT.remove(id); }
}
