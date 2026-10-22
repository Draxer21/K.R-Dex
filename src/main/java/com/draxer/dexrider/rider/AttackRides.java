package com.draxer.dexrider.rider;

import com.draxer.dexrider.entity.RiderAllyEntity;
import com.draxer.dexrider.item.DexCard;
import com.draxer.dexrider.item.DexDriverItem;
import com.draxer.dexrider.registry.DexSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * Attack Ride del fanfic (transformado, con cualquier identidad o forma). Suben de nivel re-escaneando (1-3).
 *
 *  OVERCLOCK     percepcion acelerada (Hawks) · Nv2+ el cuerpo alcanza a la cabeza (el Accel de Takumi).
 *                Dura poco y agota: al terminar, cansancio. Regla de Takumi en Nv2+: "siete y vuelves".
 *  GALE          rafaga de viento en cono (Inasa): empuja, y deshace los proyectiles que vienen hacia ti.
 *  RAZOR_FEATHER plumas cortantes que buscan a varios enemigos a la vez (Hawks) · Nv2 dejan plumas vigia
 *                (los enemigos cercanos quedan marcados y se ven a traves de las paredes).
 *  NITRO_BURST   detonacion de aire delante de ti (Bakugo), sin romper bloques · Nv2+ detonaciones encadenadas.
 *  GROUND_WAVE   onda por el suelo que lanza y frena a todo alrededor (Pixie-Bob) · Nv2 vibra y quita
 *                la lentitud y el aturdimiento que llevaras (anula vibraciones ajenas).
 */
public final class AttackRides {
    private AttackRides() {}

    public static boolean handles(DexCard card) {
        return switch (card) {
            case ATTACK_RIDE_OVERCLOCK, ATTACK_RIDE_GALE, ATTACK_RIDE_RAZOR_FEATHER,
                 ATTACK_RIDE_NITRO_BURST, ATTACK_RIDE_GROUND_WAVE,
                 ATTACK_RIDE_MANTIS_REAP, ATTACK_RIDE_RHINO_CHARGE, ATTACK_RIDE_CHIMERA_DIVE -> true;
            default -> false;
        };
    }

    /** Usa la carta. Devuelve los ticks de recarga de la carta (0 si no se pudo usar). */
    public static int use(ServerLevel level, ServerPlayer p, DexCard card, ItemStack stack) {
        int lvl = Math.max(1, DexDriverItem.getDeck(DexDriverItem.findDriver(p)).level(card));
        DexSounds.play(level, p, DexSounds.VOICE_ATTACK_RIDE, 1.0F, 1.0F);
        int cd = switch (card) {
            case ATTACK_RIDE_OVERCLOCK -> overclock(level, p, lvl);
            case ATTACK_RIDE_GALE -> gale(level, p, lvl);
            case ATTACK_RIDE_RAZOR_FEATHER -> razorFeather(level, p, lvl);
            case ATTACK_RIDE_NITRO_BURST -> nitroBurst(level, p, lvl);
            case ATTACK_RIDE_GROUND_WAVE -> groundWave(level, p, lvl);
            case ATTACK_RIDE_MANTIS_REAP -> mantisReap(level, p, lvl);
            case ATTACK_RIDE_RHINO_CHARGE -> rhinoCharge(level, p, lvl);
            case ATTACK_RIDE_CHIMERA_DIVE -> chimeraDive(level, p, lvl);
            default -> 0;
        };
        if (cd > 0) DexAdvancements.award(p, "attack_ride");
        if (cd > 0) p.sendSystemMessage(Component.translatable("message.dexrider.attack_ride_used",
                Component.translatable(card.translationKey()), lvl), true);
        return cd;
    }

    private static boolean foe(ServerPlayer p, Entity e) {
        if (!(e instanceof LivingEntity le) || e == p || !le.isAlive()) return false;
        if (e instanceof RiderAllyEntity ally && ally.isFriendlyTo(p)) return false;
        if (e instanceof net.minecraft.world.entity.player.Player) return false;
        return true;
    }

    private static void hit(ServerLevel level, ServerPlayer p, LivingEntity t, float dmg) {
        t.invulnerableTime = 0;
        t.hurtServer(level, p.damageSources().playerAttack(p), dmg);
    }

    // ------------------------------------------------------------------ Overclock

    private static int overclock(ServerLevel level, ServerPlayer p, int lvl) {
        int ticks = lvl == 1 ? 120 : lvl == 2 ? 160 : 200;
        int speed = lvl == 1 ? 1 : lvl == 2 ? 3 : 4;
        p.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, speed, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.HASTE, ticks, lvl, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, ticks + 20, 0, false, false, true));
        // todo lo demas parece ir lento
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(16), e -> foe(p, e))) {
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, lvl, false, false));
            if (lvl >= 2) e.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, ticks, 1, false, false));
        }
        FX.ring(level, p, FX.VOLT, 1.3, 7);
        FX.particle(level, ParticleTypes.ELECTRIC_SPARK, FX.center(p), 25, 0.5, 0.2);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 2.0F);
        if (lvl >= 2) Scheduler.soft(p, 140, () -> {
            p.sendSystemMessage(Component.translatable("message.dexrider.overclock_seven"), true);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0F, 2.0F);
        });
        // el precio: la cabeza (y en Nv2 el cuerpo) se cansan
        Scheduler.soft(p, ticks, () -> {
            p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60 + 30 * lvl, lvl == 1 ? 0 : 1, false, true, true));
            p.addEffect(new MobEffectInstance(MobEffects.HUNGER, 100, 1, false, true, true));
            if (lvl >= 2) p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0, false, true, true));
            FX.particle(level, ParticleTypes.SMOKE, FX.center(p), 15, 0.4, 0.02);
        });
        return 900 - 150 * lvl;
    }

    // ------------------------------------------------------------------ Gale

    private static int gale(ServerLevel level, ServerPlayer p, int lvl) {
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        double range = 7 + 3 * lvl;
        for (Entity e : level.getEntities(p, p.getBoundingBox().inflate(range))) {
            Vec3 to = FX.center(e).subtract(eye);
            if (to.length() > range || to.normalize().dot(look) < 0.55) continue;
            if (e instanceof Projectile proj && proj.getOwner() != p) {
                FX.particle(level, ParticleTypes.CLOUD, e.position(), 5, 0.2, 0.05);
                e.discard();
                continue;
            }
            if (foe(p, e)) {
                LivingEntity le = (LivingEntity) e;
                FX.push(le, p.position(), 1.4 + 0.5 * lvl, 0.35 + 0.15 * lvl);
                hit(level, p, le, 2.0F + lvl);
            }
        }
        for (int i = 1; i <= range; i++) {
            Vec3 at = eye.add(look.scale(i));
            FX.particle(level, ParticleTypes.GUST, at, 1, 0.2 + i * 0.08, 0.0);
            FX.particle(level, ParticleTypes.CLOUD, at, 3, 0.2 + i * 0.1, 0.05);
        }
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 1.2F, 0.9F);
        return 200 - 40 * lvl;
    }

    // ------------------------------------------------------------------ Razor Feather

    private static int razorFeather(ServerLevel level, ServerPlayer p, int lvl) {
        int count = 2 + 3 * lvl;
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(18),
                        e -> foe(p, e) && (e instanceof Enemy || (e instanceof net.minecraft.world.entity.Mob m && m.getTarget() == p)
                                || e instanceof com.draxer.dexrider.entity.RiderEchoEntity) && p.hasLineOfSight(e))
                .stream().sorted(Comparator.comparingDouble(e -> e.distanceToSqr(p))).limit(count).toList();
        Vec3 from = p.getEyePosition().add(0, 0.3, 0);
        for (int i = 0; i < targets.size(); i++) {
            LivingEntity t = targets.get(i);
            int delay = 2 + i * 2;
            Scheduler.later(p, delay, () -> {
                if (!t.isAlive()) return;
                Vec3 to = FX.center(t), d = to.subtract(from);
                int steps = (int) Math.ceil(d.length() * 2);
                for (int s = 0; s <= steps; s++) {
                    Vec3 at = from.add(d.scale(s / (double) steps));
                    FX.dust(level, s % 3 == 0 ? FX.WHITE : 0xC81E28, 0.8F, at, 1, 0.02);
                }
                hit(level, p, t, 4.0F + 1.5F * lvl);
                FX.particle(level, ParticleTypes.CRIT, to, 6, 0.2, 0.2);
                level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6F, 1.6F);
            });
        }
        if (lvl >= 2) {   // plumas vigia
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(24), e -> foe(p, e) && e instanceof Enemy)) {
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200 * lvl, 0, false, false));
            }
        }
        FX.particle(level, ParticleTypes.CRIT, FX.center(p), 20, 0.6, 0.1);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F, 1.5F);
        if (targets.isEmpty()) p.sendSystemMessage(Component.translatable("message.dexrider.razor_no_target"), true);
        return 260 - 50 * lvl;
    }

    // ------------------------------------------------------------------ Nitro Burst

    private static int nitroBurst(ServerLevel level, ServerPlayer p, int lvl) {
        Vec3 look = p.getLookAngle();
        int blasts = lvl == 1 ? 1 : lvl == 2 ? 3 : 5;
        for (int i = 0; i < blasts; i++) {
            final int k = i;
            Scheduler.later(p, 1 + i * 4, () -> {
                Vec3 at = p.getEyePosition().add(p.getLookAngle().scale(2.5 + k * 1.6)).add(0, -0.4, 0);
                level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 2, 0.3, 0.3, 0.3, 0);
                FX.particle(level, ParticleTypes.FLAME, at, 14, 0.6, 0.08);
                FX.dust(level, 0xF29A1E, 1.6F, at, 10, 0.6);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.9F, 1.3F);
                double r = 2.8 + 0.4 * lvl;
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(r), e -> foe(p, e))) {
                    hit(level, p, e, 5.0F + 1.5F * lvl);
                    e.igniteForSeconds(2.0F);
                    FX.push(e, at, 1.0 + 0.2 * lvl, 0.4);
                }
            });
        }
        // el retroceso te impulsa hacia atras (Bakugo vuela con sus explosiones)
        p.setDeltaMovement(p.getDeltaMovement().add(look.scale(-0.6)).add(0, 0.25, 0));
        p.hurtMarked = true;
        p.fallDistance = 0;
        return 160 - 30 * lvl;
    }

    // ------------------------------------------------------------------ Ground Wave

    private static int groundWave(ServerLevel level, ServerPlayer p, int lvl) {
        double r = 4 + 2 * lvl;
        for (int ring = 1; ring <= (int) r; ring++) {
            final int rr = ring;
            Scheduler.later(p, ring, () -> {
                for (int a = 0; a < 24; a++) {
                    double ang = a * Math.PI / 12;
                    Vec3 at = p.position().add(Math.cos(ang) * rr, 0.1, Math.sin(ang) * rr);
                    level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
                                    level.getBlockState(net.minecraft.core.BlockPos.containing(at).below())),
                            at.x, at.y, at.z, 2, 0.2, 0.1, 0.2, 0.1);
                }
            });
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r, 2, r),
                e -> foe(p, e) && e.distanceTo(p) <= r && e.onGround())) {
            hit(level, p, e, 5.0F + lvl);
            e.setDeltaMovement(e.getDeltaMovement().add(0, 0.7 + 0.15 * lvl, 0));
            e.hurtMarked = true;
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60 + 20 * lvl, 1));
        }
        if (lvl >= 2) {   // anula vibraciones ajenas
            p.removeEffect(MobEffects.SLOWNESS);
            p.removeEffect(MobEffects.NAUSEA);
            p.removeEffect(MobEffects.MINING_FATIGUE);
        }
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.8F, 1.4F);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6F, 0.6F);
        return 240 - 40 * lvl;
    }

    // ------------------------------------------------------------------ Kaijin de Dai-Shocker

    /** Mantis Reap: salto corto hacia delante y dos tajos en arco que hacen sangrar. */
    private static int mantisReap(ServerLevel level, ServerPlayer p, int lvl) {
        Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
        p.setDeltaMovement(look.scale(0.9 + 0.2 * lvl).add(0, 0.45, 0));
        p.hurtMarked = true;
        p.fallDistance = 0;
        for (int k = 0; k < 2; k++) {
            final int side = k == 0 ? -1 : 1;
            Scheduler.later(p, 6 + k * 5, () -> {
                Vec3 fwd = p.getLookAngle().multiply(1, 0, 1).normalize();
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(3.5 + 0.5 * lvl), e -> foe(p, e))) {
                    Vec3 to = e.position().subtract(p.position()).multiply(1, 0, 1);
                    if (to.lengthSqr() > 0.01 && to.normalize().dot(fwd) < -0.1) continue;
                    hit(level, p, e, 5.0F + 1.5F * lvl);
                    e.addEffect(new MobEffectInstance(MobEffects.WITHER, 40 + 20 * lvl, lvl >= 3 ? 1 : 0));
                }
                for (int a = -70; a <= 70; a += 20) {
                    Vec3 at = p.position().add(FX.yaw(fwd, a * side).scale(2.4)).add(0, 1.0 + a * side * 0.006, 0);
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                    FX.dust(level, 0x96DC5A, 0.8F, at, 1, 0.05);
                }
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.5F);
            });
        }
        return 200 - 30 * lvl;
    }

    /** Rhino Charge: embestida en linea recta con el hombro; lanza por los aires a lo que atraviesa. */
    private static int rhinoCharge(ServerLevel level, ServerPlayer p, int lvl) {
        Vec3 dir = p.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 from = p.position();
        double len = 7 + 2 * lvl;
        Vec3 end = FX.rayEnd(level, p, from.add(0, 0.5, 0), dir, len);
        Vec3 to = end.subtract(dir.scale(0.6)).add(0, -0.5, 0);
        for (LivingEntity e : FX.alongLine(level, p, from.add(0, 1, 0), to.add(0, 1, 0), 1.1)) {
            hit(level, p, e, 7.0F + 2.0F * lvl);
            FX.push(e, from, 1.6 + 0.3 * lvl, 0.7);
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 3));
        }
        for (double d = 0; d < from.distanceTo(to); d += 0.8) FX.particle(level, ParticleTypes.CLOUD, from.add(dir.scale(d)).add(0, 0.2, 0), 2, 0.2, 0.02);
        if (level.noCollision(p, p.getBoundingBox().move(to.subtract(from)))) p.teleportTo(to.x, to.y, to.z);
        if (lvl >= 2) p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 40, 1, false, false, true));
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.7F, 1.3F);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6F, 0.7F);
        return 220 - 40 * lvl;
    }

    /** Chimera Dive: alas de membrana; subes alto, planeas un instante y caes aplastando todo alrededor. */
    private static int chimeraDive(ServerLevel level, ServerPlayer p, int lvl) {
        p.setDeltaMovement(p.getDeltaMovement().x, 1.3 + 0.15 * lvl, p.getDeltaMovement().z);
        p.hurtMarked = true;
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, false, false, true));
        FX.particle(level, ParticleTypes.LARGE_SMOKE, FX.center(p), 20, 0.6, 0.05);
        FX.dust(level, 0x46283C, 1.6F, FX.center(p), 20, 0.8);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 1.2F, 0.6F);
        for (int t = 20; t <= 40; t++) Scheduler.later(p, t, () -> p.fallDistance = 0);   // la caida no le hace dano
        Scheduler.later(p, 26, () -> {
            p.setDeltaMovement(p.getDeltaMovement().x * 0.3, -2.4, p.getDeltaMovement().z * 0.3);
            p.hurtMarked = true;
        });
        Scheduler.later(p, 34, () -> {
            p.fallDistance = 0;
            double r = 4 + lvl;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r, 2, r), e -> foe(p, e))) {
                hit(level, p, e, 9.0F + 3.0F * lvl);
                FX.push(e, p.position(), 1.2, 0.6);
                if (lvl >= 2) e.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
            }
            level.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 0.2, p.getZ(), 4, 1.5, 0.2, 1.5, 0);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2F, 0.7F);
        });
        return 400 - 60 * lvl;
    }
}
