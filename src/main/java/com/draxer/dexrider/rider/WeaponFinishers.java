package com.draxer.dexrider.rider;

import com.draxer.dexrider.registry.DexSounds;
import com.draxer.dexrider.item.DexWeaponItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Final Attack Ride de cada arma (los nombres son los del fanfic).
 * Todos empiezan igual: cartas holograficas en linea hacia el objetivo y, unos ticks despues, el golpe.
 * El dano pasa por el escalado del mazo (DexEvents), asi que crece con cada Armed Ride escaneada.
 */
public final class WeaponFinishers {
    private WeaponFinishers() {}

    private static final int WINDUP = 8;   // ticks entre las cartas y el golpe

    public static void run(ServerLevel level, ServerPlayer player, DexWeaponItem weapon, ItemStack stack) {
        switch (weapon.kind()) {
            case RIDE_BOOKER -> {
                if (DexWeaponItem.isGunMode(stack)) bookerBlast(level, player);
                else bookerSlash(level, player);
            }
            case SHOCK_BATON -> voltCrush(level, player);
            case BLAST_SHOT -> fullBurst(level, player);
            case SWORD -> crossSlash(level, player);
            case VOLT_CHAIN -> voltLash(level, player);
            case POWER_KNUCKLE -> doubleImpact(level, player);
            default -> RiderWeapons.finisher(level, player, weapon.kind());
        }
    }

    // ------------------------------------------------------------------ Ride Booker

    /** Espada: onda de corte que atraviesa todo en 10 bloques. */
    private static void bookerSlash(ServerLevel level, ServerPlayer player) {
        Vec3 dir = flatLook(player);
        Vec3 start = player.position().add(0, 1.0, 0);
        Vec3 end = start.add(dir.scale(10));
        announce(level, player, "booker_slash", start, end, 3);
        Scheduler.later(player, WINDUP, () -> {
            Vec3 s = player.position().add(0, 1.0, 0);
            Vec3 e = s.add(flatLook(player).scale(10));
            sweep(level, s, e);
            FX.line(level, s, e, FX.MAGENTA, 2.0F, 0.25);
            for (LivingEntity t : FX.alongLine(level, player, s, e, 1.5)) {
                FX.hit(level, player, t, 20.0F);
                FX.push(t, s, 1.2, 0.3);
            }
            sound(level, player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.5F, 0.6F);
        });
    }

    /** Pistola: rayo cargado que atraviesa 40 bloques. */
    private static void bookerBlast(ServerLevel level, ServerPlayer player) {
        Vec3 start = player.getEyePosition();
        Vec3 end = FX.rayEnd(level, player, start, player.getLookAngle(), 40);
        announce(level, player, "booker_blast", start, end, 4);
        Scheduler.later(player, WINDUP, () -> {
            Vec3 dir = player.getLookAngle();
            FX.beam(level, player, dir, 40, 20.0F, FX.MAGENTA, 2.5F, true);
            Vec3 e = FX.rayEnd(level, player, player.getEyePosition(), dir, 40);
            FX.line(level, player.getEyePosition().add(dir), e, FX.GOLD, 1.0F, 0.5);
            FX.particle(level, ParticleTypes.EXPLOSION, e, 3, 0.4, 0);
            sound(level, player, DexSounds.FAR_IMPACT.get(), 1.6F, 1.1F);
        });
    }

    // ------------------------------------------------------------------ Shock Baton: Volt Crush

    /** Golpe al suelo: rayos (solo visuales) sobre todos los enemigos en 6 bloques. */
    private static void voltCrush(ServerLevel level, ServerPlayer player) {
        Vec3 start = player.position().add(0, 2.5, 0);
        announce(level, player, "volt_crush", start, start.add(0, 3, 0), 3);
        player.setDeltaMovement(0, 0.6, 0);
        player.hurtMarked = true;
        Scheduler.later(player, WINDUP + 4, () -> {
            Vec3 c = player.position();
            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                    AABB.ofSize(c, 12, 6, 12), e -> e != player && e.isAlive() && e.distanceToSqr(c) <= 36);
            int n = 0;
            for (LivingEntity t : targets) {
                if (n++ >= 8) break;
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
                if (bolt != null) {
                    bolt.setVisualOnly(true);
                    bolt.setPos(t.getX(), t.getY(), t.getZ());
                    level.addFreshEntity(bolt);
                }
                FX.hit(level, player, t, 14.0F);
                t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 3));
                FX.particle(level, ParticleTypes.ELECTRIC_SPARK, FX.center(t), 25, 0.4, 0.3);
            }
            for (int i = 0; i < 3; i++) FX.ring(level, player, FX.VOLT, 2.0 + i * 1.5, 1);
            FX.particle(level, ParticleTypes.ELECTRIC_SPARK, c, 60, 2.5, 0.4);
            sound(level, player, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8F, 1.4F);
        });
    }

    // ------------------------------------------------------------------ Blast Shot: Full Burst

    /** Cinco rafagas en abanico; la ultima es mas fuerte. */
    private static void fullBurst(ServerLevel level, ServerPlayer player) {
        Vec3 start = player.getEyePosition();
        announce(level, player, "full_burst", start, start.add(player.getLookAngle().scale(8)), 3);
        for (int volley = 0; volley < 5; volley++) {
            final boolean last = volley == 4;
            Scheduler.later(player, WINDUP + volley * 4, () -> {
                Vec3 look = player.getLookAngle();
                for (int a = -24; a <= 24; a += 12) {
                    FX.beam(level, player, FX.yaw(look, a), 28, last ? 8.0F : 5.0F,
                            last ? FX.GOLD : FX.RED, last ? 1.6F : 1.0F, false);
                }
                sound(level, player, DexSounds.SHOT.get(), 1.2F, last ? 0.8F : 1.1F);
            });
        }
    }

    // ------------------------------------------------------------------ Sword: Cross Slash

    /** Dos cortes cruzados en X, cada uno atraviesa 8 bloques. */
    private static void crossSlash(ServerLevel level, ServerPlayer player) {
        Vec3 start = player.position().add(0, 1.0, 0);
        announce(level, player, "cross_slash", start, start.add(flatLook(player).scale(8)), 3);
        for (int i = 0; i < 2; i++) {
            final boolean second = i == 1;
            Scheduler.later(player, WINDUP + i * 6, () -> {
                Vec3 dir = flatLook(player);
                Vec3 s = player.position().add(0, 1.0, 0);
                Vec3 e = s.add(dir.scale(8));
                drawSlash(level, s.add(dir.scale(2.5)), dir, second);
                sweep(level, s, e);
                for (LivingEntity t : FX.alongLine(level, player, s, e, 2.0)) {
                    FX.hit(level, player, t, 13.0F);
                    FX.push(t, s, second ? 1.4 : 0.4, second ? 0.4 : 0.1);
                }
                sound(level, player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.5F, second ? 0.7F : 1.1F);
            });
        }
    }

    /** Diagonal grande de particulas: '\' o '/'. */
    private static void drawSlash(ServerLevel level, Vec3 center, Vec3 dir, boolean mirrored) {
        Vec3 right = dir.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 up = new Vec3(0, 1, 0);
        double s = mirrored ? -1 : 1;
        Vec3 a = center.add(right.scale(-1.6 * s)).add(up.scale(1.4));
        Vec3 b = center.add(right.scale(1.6 * s)).add(up.scale(-1.2));
        FX.line(level, a, b, FX.WHITE, 1.8F, 0.15);
        FX.line(level, a, b, FX.RED, 1.0F, 0.3);
    }

    // ------------------------------------------------------------------ Volt Chain: Volt Lash

    /** Latigazo de 14 bloques que atraviesa a todos; despues explosion y noqueo. */
    private static void voltLash(ServerLevel level, ServerPlayer player) {
        Vec3 start = player.getEyePosition();
        Vec3 end = FX.rayEnd(level, player, start, player.getLookAngle(), 14);
        announce(level, player, "volt_lash", start, end, 3);
        Scheduler.later(player, WINDUP, () -> {
            Vec3 s = player.getEyePosition().add(0, -0.2, 0);
            Vec3 e = FX.rayEnd(level, player, s, player.getLookAngle(), 14);
            FX.line(level, s, e, FX.GOLD, 1.2F, 0.25);
            FX.line(level, s, e, FX.VOLT, 0.8F, 0.4);
            List<LivingEntity> hits = FX.alongLine(level, player, s, e, 0.9);
            for (LivingEntity t : hits) {
                FX.hit(level, player, t, 12.0F);
                Vec3 pull = player.position().subtract(t.position()).normalize().scale(0.8);
                t.setDeltaMovement(pull.x, 0.3, pull.z);
                t.hurtMarked = true;
            }
            sound(level, player, SoundEvents.FISHING_BOBBER_THROW, 1.5F, 0.5F);
            Scheduler.later(player, 10, () -> {
                for (LivingEntity t : hits) {
                    if (!t.isAlive()) continue;
                    FX.particle(level, ParticleTypes.EXPLOSION, FX.center(t), 2, 0.3, 0);
                    FX.particle(level, ParticleTypes.ELECTRIC_SPARK, FX.center(t), 30, 0.5, 0.3);
                    FX.hit(level, player, t, 8.0F);
                    // Noqueo, salvo resistencia muy alta (jefes)
                    if (t.getMaxHealth() > 100) {
                        t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
                    } else {
                        t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 6));
                        t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1));
                    }
                }
                sound(level, player, DexSounds.FAR_IMPACT.get(), 1.4F, 1.2F);
            });
        });
    }

    // ------------------------------------------------------------------ Power Knuckle: Double Impact

    /** Embestida + dos punietazos: el segundo lanza al objetivo por los aires. */
    private static void doubleImpact(ServerLevel level, ServerPlayer player) {
        LivingEntity target = FX.findTarget(level, player, 12, 0.5);
        Vec3 start = player.position().add(0, 1.0, 0);
        Vec3 end = target != null ? FX.center(target) : start.add(flatLook(player).scale(6));
        announce(level, player, "double_impact", start, end, 2);

        // Los dos punios alineados en vertical delante de Dex
        Vec3 front = player.getEyePosition().add(flatLook(player).scale(1.0));
        FX.dust(level, FX.GOLD, 2.0F, front.add(0, 0.3, 0), 12, 0.12);
        FX.dust(level, FX.RED, 2.0F, front.add(0, -0.4, 0), 12, 0.12);

        Scheduler.later(player, WINDUP - 2, () -> {
            Vec3 to = (target != null && target.isAlive() ? FX.center(target) : end).subtract(player.position());
            Vec3 dash = new Vec3(to.x, 0, to.z).normalize().scale(1.3);
            player.setDeltaMovement(dash.x, 0.15, dash.z);
            player.hurtMarked = true;
        });
        Scheduler.later(player, WINDUP + 4, () -> punch(level, player, target, 14.0F, false));
        Scheduler.later(player, WINDUP + 11, () -> punch(level, player, target, 22.0F, true));
    }

    private static void punch(ServerLevel level, ServerPlayer player, LivingEntity target, float damage, boolean finisher) {
        LivingEntity t = target != null && target.isAlive() && target.distanceToSqr(player) < 25
                ? target : FX.findTarget(level, player, 4, 0.2);
        Vec3 at = t != null ? FX.center(t) : player.getEyePosition().add(flatLook(player).scale(1.5));
        if (t != null) {
            FX.hit(level, player, t, damage);
            FX.push(t, player.position(), finisher ? 1.2 : 0.3, finisher ? 1.1 : 0.1);
        }
        FX.particle(level, finisher ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, at, 1, 0.1, 0);
        FX.dust(level, FX.GOLD, 2.0F, at, finisher ? 40 : 15, 0.5);
        sound(level, player, finisher ? DexSounds.FAR_IMPACT.get() : SoundEvents.ANVIL_LAND,
                finisher ? 1.8F : 0.8F, finisher ? 0.6F : 1.2F);
    }

    // ------------------------------------------------------------------ comun

    private static void announce(ServerLevel level, ServerPlayer player, String key, Vec3 from, Vec3 to, int cards) {
        FX.cardPath(level, from, to, cards, FX.MAGENTA);
        sound(level, player, DexSounds.FAR_CHARGE.get(), 1.5F, 1.0F);
        sound(level, player, DexSounds.VOICE_FINAL_ATTACK_RIDE.get(), 1.0F, 1.0F);
        player.sendSystemMessage(Component.translatable("message.dexrider.final_attack_ride_weapon",
                Component.translatable("finisher.dexrider." + key)), true);
    }

    private static void sweep(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        for (double t = 0.1; t <= 1.0; t += 0.15) {
            Vec3 p = from.add(d.scale(t));
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    private static Vec3 flatLook(ServerPlayer player) {
        Vec3 l = player.getLookAngle();
        Vec3 flat = new Vec3(l.x, 0, l.z);
        return flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
    }

    private static void sound(ServerLevel level, ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }
}
