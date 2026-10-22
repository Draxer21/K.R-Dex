package com.draxer.dexrider.entity;

import com.draxer.dexrider.rider.FX;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Ataques especiales de los Riders controlados por el juego (ecos y aliados). */
public final class RiderNpcAttacks {
    private RiderNpcAttacks() {}

    public static void special(ServerLevel level, RiderMob self, LivingEntity target) {
        switch (self.rider()) {
            case NINE -> nineSpecial(level, self, target);
            case CIPHER -> {
                if (self.riderForm() == 2) blasterShot(level, self, target);
                else cipherMarker(level, self, target);
            }
            case SPORE -> sporeCloud(level, self);
            case FORGE -> forgeBarrage(level, self, target);
            case MIRAGE -> mirageFlash(level, self, target);
            case PROTO -> protoModule(level, self, target);
            case WARDEN -> wardenStrike(level, self, target);
            case PSYGA -> psygaDive(level, self, target);
            case ORGA -> orgaSlam(level, self, target);
            case KAIXA -> kaixaShots(level, self, target);
            default -> { }
        }
    }

    private static void hurt(ServerLevel level, RiderMob self, LivingEntity e, float dmg) {
        e.invulnerableTime = 0;
        e.hurtServer(level, self.damageSources().mobAttack(self), dmg * self.powerScale() * self.formScale());
    }

    /** Nine (Izuku): el ataque depende de su forma. */
    private static void nineSpecial(ServerLevel level, RiderMob self, LivingEntity target) {
        int v = self.riderForm();
        int color = NineForms.color(v);
        switch (NineForms.form(v)) {
            case BASE -> nineSmash(level, self, target);
            case DRAGON -> {   // Dragon Rod: salto altisimo y barrido con el baston
                if (self.distanceTo(target) > 4) {
                    Vec3 d = target.position().subtract(self.position()).normalize();
                    self.setDeltaMovement(d.x * 1.1, 1.0, d.z * 1.1);
                    self.hurtMarked = true;
                    self.specialCooldown = 18;
                    return;
                }
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, self.getBoundingBox().inflate(3.5),
                        e -> e != self && e.isAlive() && !self.isFriendlyTo(e))) {
                    hurt(level, self, e, 8.0F);
                    FX.push(e, self.position(), 1.6, 0.5);
                }
                FX.particle(level, ParticleTypes.SPLASH, FX.center(self), 60, 2.0, 0.2);
                FX.dust(level, color, 1.4F, FX.center(self), 30, 2.0);
                level.playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.2F, 0.8F);
                self.swing(InteractionHand.MAIN_HAND);
            }
            case PEGASUS -> {  // Pegasus Bow: disparo de precision desde lejos
                Vec3 from = self.getEyePosition();
                Vec3 to = FX.center(target);
                if (from.distanceTo(to) > 36 || !self.hasLineOfSight(target)) return;
                FX.line(level, from, to, color, 1.2F, 0.3);
                FX.particle(level, ParticleTypes.CRIT, to, 15, 0.3, 0.2);
                hurt(level, self, target, 9.0F);
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0));
                level.playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 1.2F, 1.4F);
            }
            case TITAN -> {    // Titan Blades: tajo pesado
                if (self.distanceTo(target) > 3.5) {
                    self.specialCooldown = 20;
                    return;
                }
                hurt(level, self, target, 14.0F);
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2));
                FX.push(target, self.position(), 1.2, 0.3);
                FX.dust(level, color, 1.6F, FX.center(target), 30, 0.7);
                level.playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.8F, 0.7F);
                self.swing(InteractionHand.MAIN_HAND);
            }
        }
    }

    /** Nine: salta hacia el objetivo y lo golpea con una onda de choque. */
    private static void nineSmash(ServerLevel level, RiderMob self, LivingEntity target) {
        Vec3 to = target.position().subtract(self.position());
        if (to.length() > 5) {
            Vec3 d = to.normalize();
            self.setDeltaMovement(d.x * 1.3, 0.6, d.z * 1.3);
            self.hurtMarked = true;
            self.specialCooldown = 15;   // al aterrizar, golpea
            return;
        }
        hurt(level, self, target, 12.0F);
        FX.push(target, self.position(), 1.8, 0.5);
        FX.particle(level, ParticleTypes.EXPLOSION, FX.center(target), 1, 0, 0);
        FX.dust(level, 0xAAFF96, 1.5F, FX.center(target), 20, 0.6);
        level.playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.8F, 1.5F);
        self.swing(InteractionHand.MAIN_HAND);
    }

    /** Cipher: fija al objetivo con un cono de codigo y lo embiste. */
    private static void cipherMarker(ServerLevel level, RiderMob self, LivingEntity target) {
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 9));
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0));
        FX.line(level, self.getEyePosition(), FX.center(target), 0xDCF0FF, 0.8F, 0.5);
        for (int i = 0; i < 4; i++) {
            FX.dust(level, 0xDCF0FF, 0.9F, FX.center(target).add(0, 0.6 + i * 0.4, 0), 6, 0.15 + i * 0.12);
        }
        Vec3 d = target.position().subtract(self.position()).normalize();
        self.setDeltaMovement(d.x * 1.6, 0.35, d.z * 1.6);
        self.hurtMarked = true;
        level.playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 0.8F, 2.0F);
    }

    /** Cipher Blaster: disparo del arma Blaster. */
    private static void blasterShot(ServerLevel level, RiderMob self, LivingEntity target) {
        Vec3 from = self.getEyePosition();
        Vec3 to = FX.center(target);
        if (from.distanceTo(to) > 36 || !self.hasLineOfSight(target)) return;
        FX.line(level, from, to, 0xFF3628, 1.8F, 0.3);
        FX.particle(level, ParticleTypes.EXPLOSION, to, 1, 0.2, 0);
        hurt(level, self, target, 11.0F);
        FX.push(target, self.position(), 1.0, 0.3);
        level.playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.6F, 1.8F);
    }

    /** Spore: nube de esporas que debilita y marea. */
    private static void sporeCloud(ServerLevel level, RiderMob self) {
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, self.getBoundingBox().inflate(5),
                e -> e != self && e.isAlive() && !self.isFriendlyTo(e))) {
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
            e.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0));
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0));
        }
        FX.particle(level, ParticleTypes.SPORE_BLOSSOM_AIR, FX.center(self), 60, 2.5, 0.02);
        level.playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.7F, 1.4F);
    }

    /** Forge: segun su mitad derecha (canon creado, canonazo pesado o escudo) y, a veces, la izquierda. */
    private static void forgeBarrage(ServerLevel level, RiderMob self, LivingEntity target) {
        int form = self.riderForm();
        com.draxer.dexrider.rider.ForgeForms.Right right = com.draxer.dexrider.rider.ForgeForms.right(form);
        com.draxer.dexrider.rider.ForgeForms.Left left = com.draxer.dexrider.rider.ForgeForms.left(form);
        if (right == com.draxer.dexrider.rider.ForgeForms.Right.SHIELD) {
            self.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 160, 1));
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, self.getBoundingBox().inflate(4),
                    e -> e != self && e.isAlive() && !self.isFriendlyTo(e))) {
                FX.push(e, self.position(), 1.2, 0.4);
            }
            FX.dust(level, 0xDCFAFF, 1.2F, FX.center(self), 25, 1.0);
            level.playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE, 0.8F, 1.6F);
        } else {
            Vec3 from = self.getEyePosition();
            Vec3 to = FX.center(target);
            if (from.distanceTo(to) > 24 || !self.hasLineOfSight(target)) return;
            boolean heavy = right == com.draxer.dexrider.rider.ForgeForms.Right.CANNON;
            FX.line(level, from, to, heavy ? 0xDE8430 : 0xB4B4BE, heavy ? 2.0F : 1.4F, 0.4);
            FX.particle(level, heavy ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, to, 1, 0.2, 0);
            hurt(level, self, target, heavy ? 10.0F : 7.0F);
            FX.push(target, self.position(), heavy ? 1.6 : 0.8, heavy ? 0.6 : 0.3);
            level.playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.6F, heavy ? 0.9F : 1.6F);
        }
        if (com.draxer.dexrider.rider.ForgeForms.sync(form) || self.getRandom().nextBoolean()) {
            if (left == com.draxer.dexrider.rider.ForgeForms.Left.MAGNESIUM) {
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0));
                FX.particle(level, ParticleTypes.END_ROD, FX.center(self).add(0, 1.5, 0), 30, 0.6, 0.3);
            } else if (left == com.draxer.dexrider.rider.ForgeForms.Left.FIBER && self.distanceTo(target) < 14) {
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 3));
                com.draxer.dexrider.rider.TempBlocks.placeIgnoringEntities(level, target.blockPosition(),
                        net.minecraft.world.level.block.Blocks.COBWEB.defaultBlockState(), 80);
                FX.line(level, self.getEyePosition(), FX.center(target), 0x6EC8A0, 0.8F, 0.4);
            }
        }
    }

    /** Mirage: destello cegador concentrando la luz. */
    private static void mirageFlash(ServerLevel level, RiderMob self, LivingEntity target) {
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, self.getBoundingBox().inflate(8),
                e -> e != self && e.isAlive() && !self.isFriendlyTo(e))) {
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
        }
        hurt(level, self, target, 5.0F);
        FX.particle(level, ParticleTypes.END_ROD, FX.center(self), 40, 1.5, 0.15);
        level.playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 1.5F, 1.6F);
    }

    /** Proto: modulo de gancho (atrae) o propulsor (embiste). */
    private static void protoModule(ServerLevel level, RiderMob self, LivingEntity target) {
        if (self.distanceTo(target) > 5) {
            Vec3 d = self.position().subtract(target.position()).normalize();
            target.setDeltaMovement(d.x * 1.4, 0.35, d.z * 1.4);
            target.hurtMarked = true;
            FX.line(level, self.getEyePosition(), FX.center(target), 0xFF8418, 1.0F, 0.4);
            level.playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.HOSTILE, 1.2F, 0.6F);
        } else {
            Vec3 d = target.position().subtract(self.position()).normalize();
            self.setDeltaMovement(d.x * 1.5, 0.5, d.z * 1.5);
            self.hurtMarked = true;
            hurt(level, self, target, 8.0F);
            target.igniteForSeconds(3.0F);
            FX.particle(level, ParticleTypes.FLAME, self.position(), 20, 0.3, 0.1);
            level.playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.0F, 1.2F);
        }
    }

    /** Warden: lo inmoviliza (contencion) y lo embiste a toda velocidad. */
    private static void wardenStrike(ServerLevel level, RiderMob self, LivingEntity target) {
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 6));
        FX.line(level, self.getEyePosition(), FX.center(target), 0xFF9628, 0.8F, 0.5);
        Vec3 d = target.position().subtract(self.position()).normalize();
        self.setDeltaMovement(d.x * 2.0, 0.3, d.z * 2.0);
        self.hurtMarked = true;
        if (self.distanceTo(target) < 6) hurt(level, self, target, 7.0F);
        level.playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 1.0F, 0.8F);
    }

    /** Psyga: sube con la mochila y cae en picada. */
    private static void psygaDive(ServerLevel level, RiderMob self, LivingEntity target) {
        if (self.onGround()) {
            self.setDeltaMovement(0, 1.3, 0);
            self.hurtMarked = true;
            FX.particle(level, ParticleTypes.CLOUD, self.position(), 20, 0.5, 0.05);
            self.specialCooldown = 18;   // al llegar arriba, cae
            return;
        }
        Vec3 d = FX.center(target).subtract(self.position()).normalize();
        self.setDeltaMovement(d.scale(1.8));
        self.hurtMarked = true;
        if (self.distanceTo(target) < 4) {
            hurt(level, self, target, 10.0F);
            FX.push(target, self.position(), 1.0, 0.4);
        }
        FX.dust(level, 0x5AAAF0, 1.2F, self.position(), 15, 0.4);
        level.playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.HOSTILE, 1.2F, 1.2F);
    }

    /** Orga: golpe al suelo con la alabarda, lento pero demoledor. */
    private static void orgaSlam(ServerLevel level, RiderMob self, LivingEntity target) {
        if (self.distanceTo(target) > 6) {
            self.getNavigation().moveTo(target, 1.2);
            self.specialCooldown = 20;
            return;
        }
        self.swing(InteractionHand.MAIN_HAND);
        Vec3 c = self.position();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(5, 2, 5),
                e -> e != self && e.isAlive() && !self.isFriendlyTo(e))) {
            hurt(level, self, e, 11.0F);
            FX.push(e, c, 1.4, 0.7);
        }
        FX.particle(level, ParticleTypes.EXPLOSION, c.add(0, 0.5, 0), 2, 1.0, 0);
        FX.dust(level, 0xF2C230, 1.6F, c.add(0, 0.5, 0), 30, 2.0);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0F, 0.6F);
    }

    /** Area de dano que respeta a los amigos del Rider. */
    public static void area(ServerLevel level, RiderMob self, Vec3 c, double r, float dmg) {
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r),
                e -> e != self && e.isAlive() && !self.isFriendlyTo(e))) {
            hurt(level, self, e, dmg);
        }
    }

    /** Kaixa (Tetsuya): dos disparos de la espada-pistola y, si el objetivo esta cerca, un tajo. */
    private static void kaixaShots(ServerLevel level, RiderMob self, LivingEntity target) {
        net.minecraft.world.phys.Vec3 from = self.getEyePosition(), to = FX.center(target);
        FX.line(level, from, to, 0x966EFF, 0.9F, 0.4);
        hurt(level, self, target, 5.0F);
        FX.line(level, from.add(0, -0.2, 0), to, 0x385CBE, 0.9F, 0.4);
        hurt(level, self, target, 4.0F);
        level.playSound(null, self.getX(), self.getY(), self.getZ(), net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_BLAST,
                net.minecraft.sounds.SoundSource.HOSTILE, 0.8F, 1.6F);
        if (self.distanceTo(target) < 4) {
            hurt(level, self, target, 6.0F);
            FX.particle(level, net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK, to, 2, 0.3, 0);
        }
    }
}
