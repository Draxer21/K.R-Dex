package com.draxer.dexrider.rider;

import com.draxer.dexrider.item.DexWeaponItem.WeaponKind;
import com.draxer.dexrider.registry.DexComponents;
import com.draxer.dexrider.registry.DexSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Armas de los Riders en manos de Dex: clic derecho, efectos al golpear y Final Attack Ride.
 * Son las mismas que usan sus duenios (Cipher, Mirage, Proto, Forge, Warden, Orga, Spore).
 */
public final class RiderWeapons {
    private RiderWeapons() {}

    // ================================================================== clic derecho (devuelve ticks de enfriamiento, 0 = nada)

    public static int use(ServerLevel level, ServerPlayer p, WeaponKind kind, ItemStack stack) {
        Vec3 look = p.getLookAngle();
        switch (kind) {
            case CIPHER_EDGE -> {                       // corte de codigo: avance corto y corte
                Vec3 flat = flat(look);
                Vec3 to = p.position().add(flat.scale(Math.max(0, FX.rayEnd(level, p, p.position().add(0, 0.3, 0), flat, 4)
                        .distanceTo(p.position().add(0, 0.3, 0)) - 0.6)));
                for (LivingEntity e : FX.alongLine(level, p, p.position().add(0, 1, 0), to.add(flat.scale(1.5)).add(0, 1, 0), 0.9)) {
                    FX.hit(level, p, e, 6.0F);
                    FX.dust(level, 0xDCF0FF, 1.0F, FX.center(e), 10, 0.3);
                }
                if (level.noCollision(p, p.getBoundingBox().move(to.subtract(p.position())))) p.teleportTo(to.x, to.y, to.z);
                FX.line(level, p.position().add(0, 1, 0).subtract(flat.scale(3)), p.position().add(0, 1, 0).add(flat.scale(1.5)), 0xDCF0FF, 1.0F, 0.3);
                sound(level, p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.5F);
                return 30;
            }
            case PRISM_EDGE -> {                        // destello cegador en cono
                int n = 0;
                for (LivingEntity e : cone(level, p, 9, 0.55)) {
                    e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
                    n++;
                }
                FX.particle(level, ParticleTypes.END_ROD, p.getEyePosition().add(look), 40, 1.0, 0.2);
                sound(level, p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F, 1.7F);
                return n > 0 ? 80 : 20;
            }
            case MODULE_DRIVER -> {
                int module = stack.getOrDefault(DexComponents.MODULE.get(), 0);
                if (p.isShiftKeyDown()) {
                    module = (module + 1) % 3;
                    stack.set(DexComponents.MODULE.get(), module);
                    DexSounds.play(level, p, DexSounds.MODE_SWITCH, 1.0F, 1.0F + module * 0.15F);
                    p.sendSystemMessage(Component.translatable("message.dexrider.module_" + module), true);
                    return 6;
                }
                switch (module) {
                    case 0 -> {                         // taladro: dispara un proyectil que perfora
                        List<LivingEntity> hits = FX.beam(level, p, look, 20, 6.0F, 0xB4B4BE, 1.2F, true);
                        hits.forEach(e -> FX.particle(level, ParticleTypes.CRIT, FX.center(e), 10, 0.3, 0.2));
                        sound(level, p, SoundEvents.CROSSBOW_SHOOT, 1.0F, 0.8F);
                        return 15;
                    }
                    case 1 -> {                         // gancho: atrae al primero
                        List<LivingEntity> hits = FX.beam(level, p, look, 16, 2.0F, 0xFF8418, 0.8F, false);
                        for (LivingEntity e : hits) {
                            Vec3 d = p.position().subtract(e.position()).normalize();
                            e.setDeltaMovement(d.x * 1.5, 0.35, d.z * 1.5);
                            e.hurtMarked = true;
                        }
                        sound(level, p, SoundEvents.FISHING_BOBBER_THROW, 1.2F, 0.6F);
                        return 25;
                    }
                    default -> {                        // soplete: cono corto de plasma que quema
                        for (LivingEntity e : cone(level, p, 5, 0.75)) {
                            FX.hit(level, p, e, 5.0F);
                            e.igniteForSeconds(4.0F);
                        }
                        Vec3 m = p.getEyePosition().add(look.scale(0.8));
                        for (int i = 0; i < 12; i++) {
                            Vec3 d = look.add((level.getRandom().nextDouble() - 0.5) * 0.3, (level.getRandom().nextDouble() - 0.5) * 0.2,
                                    (level.getRandom().nextDouble() - 0.5) * 0.3).normalize();
                            level.sendParticles(ParticleTypes.FLAME, m.x, m.y, m.z, 0, d.x, d.y, d.z, 0.35);
                        }
                        sound(level, p, SoundEvents.FIRECHARGE_USE, 1.0F, 0.8F);
                        return 25;
                    }
                }
            }
            case FORGE_LANCE -> {                       // estocada con avance
                Vec3 flat = flat(look);
                p.setDeltaMovement(flat.x * 1.4, 0.15, flat.z * 1.4);
                p.hurtMarked = true;
                for (LivingEntity e : FX.alongLine(level, p, p.getEyePosition(), p.getEyePosition().add(look.scale(6)), 0.6)) {
                    FX.hit(level, p, e, 8.0F);
                    FX.push(e, p.position(), 1.2, 0.3);
                }
                FX.line(level, p.getEyePosition().add(look), p.getEyePosition().add(look.scale(6)), 0xE8E8EC, 1.0F, 0.4);
                sound(level, p, SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 1.2F);
                return 30;
            }
            case WARDEN_BATON -> {                      // contencion a un objetivo
                LivingEntity t = FX.findTarget(level, p, 12, 0.85);
                if (t == null) return 0;
                t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 9));
                t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 1));
                t.addEffect(new MobEffectInstance(MobEffects.GLOWING, 80, 0));
                FX.line(level, p.getEyePosition(), FX.center(t), 0xFF9628, 0.8F, 0.4);
                sound(level, p, SoundEvents.CHAIN_PLACE, 1.2F, 0.8F);
                return 100;
            }
            case KAIXA_GUNBLADE -> {                    // dos disparos; Shift: tajo de remate en linea
                if (p.isShiftKeyDown()) {
                    Vec3 dir = flat(look);
                    Vec3 from = p.position();
                    Vec3 to = FX.rayEnd(level, p, from.add(0, 0.3, 0), dir, 6).subtract(dir.scale(0.6)).add(0, -0.3, 0);
                    for (LivingEntity e : FX.alongLine(level, p, from.add(0, 1, 0), to.add(0, 1, 0), 0.9)) {
                        FX.hit(level, p, e, e.getHealth() < e.getMaxHealth() * 0.5F ? 11.0F : 7.0F);
                    }
                    FX.line(level, from.add(0, 1, 0), to.add(0, 1, 0), 0xFFC434, 1.1F, 0.4);
                    if (level.noCollision(p, p.getBoundingBox().move(to.subtract(from)))) p.teleportTo(to.x, to.y, to.z);
                    sound(level, p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 1.3F);
                    return 40;
                }
                for (int i = 0; i < 2; i++) {
                    Scheduler.later(p, 1 + i * 4, () -> FX.beam(level, p, p.getLookAngle(), 28.0, 6.0F, 0xFFC434, 0.9F, false));
                }
                sound(level, p, SoundEvents.FIREWORK_ROCKET_BLAST, 0.9F, 1.6F);
                return 14;
            }
            case ORGA_HALBERD -> {                      // barrido en arco
                for (LivingEntity e : cone(level, p, 4.5, -0.2)) {
                    FX.hit(level, p, e, 9.0F);
                    FX.push(e, p.position(), 1.6, 0.4);
                }
                for (int a = -120; a <= 120; a += 20) {
                    Vec3 at = p.position().add(FX.yaw(flat(look), a).scale(3)).add(0, 1, 0);
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                }
                sound(level, p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4F, 0.6F);
                return 50;
            }
            case SPORE_ROD -> {
                if (p.isShiftKeyDown()) {               // clavarla: la red sensorial llega mucho mas lejos
                    int n = 0;
                    for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(48),
                            e -> e != p && e.isAlive())) {
                        e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 300, 0, false, false));
                        n++;
                    }
                    FX.particle(level, ParticleTypes.SPORE_BLOSSOM_AIR, p.position().add(0, 0.5, 0), 120, 5.0, 0.02);
                    sound(level, p, SoundEvents.AZALEA_LEAVES_BREAK, 1.5F, 0.6F);
                    p.sendSystemMessage(Component.translatable("message.dexrider.spore_rod_anchor", n), true);
                    return 200;
                }
                for (LivingEntity e : cone(level, p, 7, 0.6)) {   // estallido de esporas a distancia
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 1));
                    e.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 80, 0));
                    e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 80, 0));
                }
                FX.particle(level, ParticleTypes.SPORE_BLOSSOM_AIR, p.getEyePosition().add(look.scale(3)), 50, 1.5, 0.02);
                sound(level, p, SoundEvents.FIRE_EXTINGUISH, 0.8F, 1.4F);
                return 40;
            }
            default -> {
                return 0;
            }
        }
    }

    // ================================================================== al golpear cuerpo a cuerpo

    public static void onHit(ServerLevel level, ServerPlayer p, WeaponKind kind, LivingEntity target) {
        switch (kind) {
            case CIPHER_EDGE -> FX.dust(level, 0xDCF0FF, 1.0F, FX.center(target), 8, 0.3);
            case PRISM_EDGE -> {
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0));
                FX.particle(level, ParticleTypes.END_ROD, FX.center(target), 6, 0.3, 0.05);
            }
            case FORGE_LANCE -> FX.push(target, p.position(), 0.8, 0.2);
            case WARDEN_BATON -> target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 4));
            case ORGA_HALBERD -> {
                FX.push(target, p.position(), 1.2, 0.4);
                FX.dust(level, 0xF2C230, 1.2F, FX.center(target), 10, 0.4);
            }
            case KAIXA_GUNBLADE -> {                    // remata: mas dano a los que ya estan tocados
                if (target.getHealth() < target.getMaxHealth() * 0.5F) FX.hit(level, p, target, 3.0F);
                FX.dust(level, 0xFFC434, 0.9F, FX.center(target), 6, 0.3);
            }
            case SPORE_ROD -> {                         // no corta: traba y empuja
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2));
                FX.push(target, p.position(), 0.6, 0.1);
                FX.particle(level, ParticleTypes.SPORE_BLOSSOM_AIR, FX.center(target), 8, 0.3, 0.01);
            }
            default -> { }
        }
    }

    // ================================================================== Final Attack Ride del arma

    public static boolean finisher(ServerLevel level, ServerPlayer p, WeaponKind kind) {
        switch (kind) {
            case CIPHER_EDGE -> codeEdgeBreak(level, p);
            case PRISM_EDGE -> prismCut(level, p);
            case MODULE_DRIVER -> moduleOverload(level, p);
            case FORGE_LANCE -> lanceStorm(level, p);
            case WARDEN_BATON -> wardenArrest(level, p);
            case ORGA_HALBERD -> orgaJudgement(level, p);
            case SPORE_ROD -> bloomBurst(level, p);
            case KAIXA_GUNBLADE -> RiderPowers.cobaltBreaker(level, p);
            default -> { return false; }
        }
        DexSounds.play(level, p, DexSounds.FAR_CHARGE, 1.5F, 1.0F);
        DexSounds.play(level, p, DexSounds.VOICE_FINAL_ATTACK_RIDE, 1.0F, 1.0F);
        p.sendSystemMessage(Component.translatable("message.dexrider.final_attack_ride_weapon",
                Component.translatable("finisher.dexrider." + kind.name().toLowerCase(java.util.Locale.ROOT))), true);
        return true;
    }

    /** Code Edge Break: marca al objetivo con el cono de codigo y lo atraviesa con un corte. */
    private static void codeEdgeBreak(ServerLevel level, ServerPlayer p) {
        LivingEntity t = FX.findTarget(level, p, 20, 0.6);
        if (t == null) return;
        t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 9));
        FX.cardPath(level, p.position().add(0, 1, 0), FX.center(t), 3, 0xDCF0FF);
        Scheduler.later(p, 10, () -> {
            if (!t.isAlive()) return;
            Vec3 d = t.position().subtract(p.position()).multiply(1, 0, 1).normalize();
            Vec3 behind = t.position().add(d.scale(1.5));
            FX.line(level, p.position().add(0, 1, 0), behind.add(0, 1, 0), 0xDCF0FF, 1.8F, 0.25);
            if (level.noCollision(p, p.getBoundingBox().move(behind.subtract(p.position())))) p.teleportTo(behind.x, behind.y, behind.z);
            FX.hit(level, p, t, 24.0F);
            FX.particle(level, ParticleTypes.EXPLOSION, FX.center(t), 1, 0, 0);
            DexSounds.play(level, p, DexSounds.FAR_IMPACT, 1.4F, 1.2F);
        });
    }

    /** Prism Cut: seis cortes espejados desde seis direcciones. */
    private static void prismCut(ServerLevel level, ServerPlayer p) {
        LivingEntity t = FX.findTarget(level, p, 16, 0.6);
        if (t == null) return;
        int[] cols = {0x5096FF, 0x8C78FF, 0xAA64F0, 0xDC78C8, 0xFFB450, 0xFFC846};
        for (int i = 0; i < 6; i++) {
            final int k = i;
            Scheduler.later(p, 6 + i * 3, () -> {
                if (!t.isAlive()) return;
                double a = Math.toRadians(k * 60);
                Vec3 c = FX.center(t);
                FX.line(level, c.add(Math.cos(a) * 3, 1.5, Math.sin(a) * 3), c.add(-Math.cos(a) * 3, -1, -Math.sin(a) * 3), cols[k], 1.2F, 0.25);
                FX.hit(level, p, t, 5.0F);
                sound(level, p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 1.0F + k * 0.12F);
            });
        }
    }

    /** Module Overload: taladro, gancho y soplete seguidos. */
    private static void moduleOverload(ServerLevel level, ServerPlayer p) {
        Scheduler.later(p, 6, () -> FX.beam(level, p, p.getLookAngle(), 24, 10.0F, 0xB4B4BE, 1.6F, true));
        Scheduler.later(p, 14, () -> {
            for (LivingEntity e : FX.beam(level, p, p.getLookAngle(), 18, 4.0F, 0xFF8418, 1.0F, true)) {
                Vec3 d = p.position().subtract(e.position()).normalize();
                e.setDeltaMovement(d.x * 1.4, 0.3, d.z * 1.4);
                e.hurtMarked = true;
            }
        });
        Scheduler.later(p, 24, () -> {
            for (LivingEntity e : cone(level, p, 6, 0.6)) {
                FX.hit(level, p, e, 14.0F);
                e.igniteForSeconds(6.0F);
            }
            FX.particle(level, ParticleTypes.EXPLOSION_EMITTER, p.getEyePosition().add(p.getLookAngle().scale(3)), 1, 0, 0);
            DexSounds.play(level, p, DexSounds.FAR_IMPACT, 1.4F, 1.3F);
        });
    }

    /** Lance Storm: Forge crea seis lanzas que caen sobre la zona del objetivo. */
    private static void lanceStorm(ServerLevel level, ServerPlayer p) {
        LivingEntity t = FX.findTarget(level, p, 28, 0.5);
        Vec3 c = t != null ? t.position() : FX.rayEnd(level, p, p.getEyePosition(), p.getLookAngle(), 20);
        for (int i = 0; i < 6; i++) {
            Scheduler.later(p, 8 + i * 4, () -> {
                Vec3 at = (t != null && t.isAlive() ? t.position() : c)
                        .add((level.getRandom().nextDouble() - 0.5) * 3, 0, (level.getRandom().nextDouble() - 0.5) * 3);
                FX.line(level, at.add(0, 10, 0), at, 0xE8E8EC, 1.4F, 0.4);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(2),
                        e -> e != p && e.isAlive())) {
                    FX.hit(level, p, e, 8.0F);
                }
                FX.particle(level, ParticleTypes.CRIT, at.add(0, 0.5, 0), 15, 0.4, 0.2);
                sound(level, p, SoundEvents.ANVIL_LAND, 0.6F, 1.4F);
            });
        }
    }

    /** Warden Arrest: encadena al objetivo 5 s y lo golpea tres veces. */
    private static void wardenArrest(ServerLevel level, ServerPlayer p) {
        LivingEntity t = FX.findTarget(level, p, 16, 0.6);
        if (t == null) return;
        t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 9));
        t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 2));
        for (int i = 0; i < 3; i++) {
            Scheduler.later(p, 8 + i * 8, () -> {
                if (!t.isAlive()) return;
                FX.line(level, p.getEyePosition(), FX.center(t), 0xFF9628, 1.0F, 0.4);
                FX.hit(level, p, t, 9.0F);
                sound(level, p, SoundEvents.CHAIN_PLACE, 1.4F, 0.7F);
            });
        }
    }

    /** Orga Judgement: la alabarda se despliega en un corte recto de 12 bloques. */
    private static void orgaJudgement(ServerLevel level, ServerPlayer p) {
        Scheduler.later(p, 12, () -> {
            Vec3 dir = p.getLookAngle();
            Vec3 start = p.getEyePosition();
            Vec3 end = FX.rayEnd(level, p, start, dir, 12);
            for (LivingEntity e : FX.alongLine(level, p, start, end, 1.2)) {
                FX.hit(level, p, e, 26.0F);
                FX.push(e, p.position(), 1.6, 0.5);
            }
            FX.line(level, start.add(dir), end, 0xF2C230, 2.0F, 0.25);
            FX.particle(level, ParticleTypes.EXPLOSION_EMITTER, end, 1, 0, 0);
            DexSounds.play(level, p, DexSounds.FAR_IMPACT, 1.4F, 0.8F);
        });
    }

    /** Bloom Burst (del fanfic): clavada, una floracion de esporas en anillo duerme a todo lo que esta dentro, sin dano. */
    private static void bloomBurst(ServerLevel level, ServerPlayer p) {
        Vec3 c = p.position();
        for (int ring = 1; ring <= 7; ring++) {
            final double r = ring;
            Scheduler.later(p, ring * 2, () -> {
                for (int a = 0; a < 360; a += 15) {
                    Vec3 at = c.add(Math.cos(Math.toRadians(a)) * r, 0.3, Math.sin(Math.toRadians(a)) * r);
                    FX.particle(level, ParticleTypes.SPORE_BLOSSOM_AIR, at, 2, 0.2, 0.01);
                }
            });
        }
        Scheduler.later(p, 16, () -> {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(7, 3, 7),
                    e -> e != p && e.isAlive() && e.distanceToSqr(c) <= 49)) {
                // "dormido": no se mueve, no ve, no pega
                e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 160, 9));
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 160, 0));
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 9));
                e.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 160, 4));
                if (e instanceof net.minecraft.world.entity.Mob m) m.setTarget(null);
                FX.particle(level, ParticleTypes.SPORE_BLOSSOM_AIR, FX.center(e), 10, 0.4, 0.01);
            }
            sound(level, p, SoundEvents.AZALEA_LEAVES_BREAK, 1.6F, 0.5F);
        });
    }

    // ================================================================== comun

    private static List<LivingEntity> cone(ServerLevel level, ServerPlayer p, double range, double minDot) {
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        return level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range), e -> {
            if (e == p || !e.isAlive()) return false;
            Vec3 to = FX.center(e).subtract(eye);
            return to.length() <= range && to.normalize().dot(look) >= minDot;
        });
    }

    private static Vec3 flat(Vec3 v) {
        Vec3 h = new Vec3(v.x, 0, v.z);
        return h.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : h.normalize();
    }

    private static void sound(ServerLevel level, ServerPlayer p, SoundEvent s, float vol, float pitch) {
        level.playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
    }
}
