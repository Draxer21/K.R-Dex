package com.draxer.dexrider.rider;

import com.draxer.dexrider.item.DexDriverItem;
import com.draxer.dexrider.item.DexCard;
import com.draxer.dexrider.item.DriverDeck;
import com.draxer.dexrider.registry.DexSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Poderes de cada identidad de Kamen Ride (y de la Complete Form).
 *
 *  NINE     : Fuerza II, Velocidad I, Salto II, Resistencia I.
 *             R = Smash (punetazo con onda de choque; el retroceso te ralentiza) · Shift+R = Air Force (disparo de aire)
 *             FAR = Rising Smash (salto y caida en picada con relampago verde)
 *  CIPHER   : Velocidad II, Fuerza I, Prisa I, Salto I.
 *             R = Code Marker (fija al objetivo en un cono de codigo) · Shift+R = Code Shot (rafaga de 3 disparos)
 *             FAR = Code Strike (si hay alguien marcado, lo atraviesa con una patada-taladro)
 *  SPORE    : no es una forma de combate. Velocidad I, Vision nocturna; percibe a los hostiles cercanos (brillan).
 *             R = Red de esporas (revela todo en 48 bloques) · Shift+R = Nube de esporas (debilita y marea)
 *             FAR = Spore Bloom (anillo de setas gigantes temporales + estallido)
 *  FORGE    : Resistencia I, Fuerza I, Prisa I.
 *             R = Forge Cannon (crea un canon: tres disparos) · Shift+R = Muro de creacion (hierro temporal)
 *             FAR = Forge Barrage (bombardeo de seis proyectiles creados sobre el objetivo)
 *  MIRAGE   : Velocidad II, Salto I, Vision nocturna.
 *             R = Destello (ciega en cono) · Shift+R = Dobles (dos duplicados solidos durante 10 s)
 *             FAR = Prism Refraction (seis rayos de luz convergen en el objetivo)
 *  PROTO    : Prisa II, Salto I, Velocidad I.
 *             R = Modulo propulsor (impulso hacia donde miras) · Shift+R = Modulo gancho (atrae al objetivo)
 *             FAR = Plasma Cutter (soplete de plasma que lo atraviesa todo)
 *  COMPLETE : Fuerza III, Resistencia II, Velocidad I, inmune al fuego. Consume hambre rapido; al vaciarse, se cae.
 *             R / Shift+R = Card Cannon (rayo magenta desde la pila de cartas del antebrazo)
 *             Multiplicador de dano: +0,2 por cada Kamen Ride del mazo (con diez, x3).
 */
public final class RiderPowers {
    private RiderPowers() {}

    public record Used(String name, int cooldownSeconds) {}

    private static final List<Holder<MobEffect>> ALL_EFFECTS = List.of(
            MobEffects.STRENGTH, MobEffects.SPEED, MobEffects.JUMP_BOOST, MobEffects.RESISTANCE, MobEffects.HASTE,
            MobEffects.NIGHT_VISION, MobEffects.FIRE_RESISTANCE, MobEffects.SLOW_FALLING);

    /** Objetivo marcado por Cipher (uuid del jugador -> entidad y hasta cuando). */
    private record Mark(LivingEntity target, long until) {}
    private static final Map<UUID, Mark> MARKS = new HashMap<>();

    static void effect(ServerPlayer p, Holder<MobEffect> e, int amplifier) {
        p.addEffect(new MobEffectInstance(e, 60, amplifier, false, false, true));
    }

    public static void clear(ServerPlayer p) {
        for (Holder<MobEffect> e : ALL_EFFECTS) p.removeEffect(e);
        MARKS.remove(p.getUUID());
        TONES_UNTIL.remove(p.getUUID());
        if (FormRide.current(p) != FormRide.SKY) FormRide.revokeFlight(p);
    }

    // ---------------------------------------------------------------- Orga: leer los tonos de las cartas
    private static final Map<UUID, Long> TONES_UNTIL = new HashMap<>();

    /** Orga lee los tonos y anticipa los golpes: esquiva cuerpo a cuerpo mientras dura. */
    public static boolean dodge(ServerPlayer p, net.minecraft.world.damagesource.DamageSource source) {
        Long until = TONES_UNTIL.get(p.getUUID());
        if (until == null || until < p.level().getGameTime()) return false;
        if (!(source.getEntity() instanceof LivingEntity) || source.getDirectEntity() != source.getEntity()) return false;
        if (p.level() instanceof ServerLevel level) {
            FX.dust(level, 0xF2C230, 1.0F, FX.center(p), 10, 0.5);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.6F);
        }
        return true;
    }

    // ================================================================== pasivas (cada 20 ticks)

    public static void applyEffects(ServerPlayer p, KamenRide kr) {
        switch (kr) {
            case NINE -> {
                effect(p, MobEffects.STRENGTH, 1);
                effect(p, MobEffects.SPEED, 0);
                effect(p, MobEffects.JUMP_BOOST, 1);
                effect(p, MobEffects.RESISTANCE, 0);
            }
            case CIPHER -> {
                if (CipherModes.blasterEffects(p)) return;
                effect(p, MobEffects.SPEED, 1);
                effect(p, MobEffects.STRENGTH, 0);
                effect(p, MobEffects.HASTE, 0);
                effect(p, MobEffects.JUMP_BOOST, 0);
            }
            case SPORE -> {
                effect(p, MobEffects.SPEED, 0);
                effect(p, MobEffects.NIGHT_VISION, 0);
            }
            case FORGE -> {
                effect(p, MobEffects.RESISTANCE, 0);
                effect(p, MobEffects.STRENGTH, 0);
                effect(p, MobEffects.HASTE, 0);
            }
            case MIRAGE -> {
                effect(p, MobEffects.SPEED, 1);
                effect(p, MobEffects.JUMP_BOOST, 0);
                effect(p, MobEffects.NIGHT_VISION, 0);
            }
            case WARDEN -> {
                effect(p, MobEffects.SPEED, 2);
                effect(p, MobEffects.STRENGTH, 0);
                effect(p, MobEffects.JUMP_BOOST, 0);
            }
            case PSYGA -> {
                effect(p, MobEffects.SPEED, 0);
                effect(p, MobEffects.JUMP_BOOST, 1);
            }
            case ORGA -> {
                effect(p, MobEffects.STRENGTH, 1);
                effect(p, MobEffects.RESISTANCE, 1);
            }
            case KAIXA -> {
                effect(p, MobEffects.STRENGTH, 0);
                effect(p, MobEffects.SPEED, 0);
                effect(p, MobEffects.HASTE, 0);
                effect(p, MobEffects.JUMP_BOOST, 0);
            }
            case PROTO -> {
                effect(p, MobEffects.HASTE, 1);
                effect(p, MobEffects.JUMP_BOOST, 0);
                effect(p, MobEffects.SPEED, 0);
            }
            case COMPLETE -> {
                effect(p, MobEffects.STRENGTH, 2);
                effect(p, MobEffects.RESISTANCE, 1);
                effect(p, MobEffects.SPEED, 0);
                effect(p, MobEffects.JUMP_BOOST, 0);
                effect(p, MobEffects.FIRE_RESISTANCE, 0);
            }
            default -> { }
        }
    }

    // ================================================================== cada tick

    public static void tick(ServerLevel level, ServerPlayer p, KamenRide kr) {
        switch (kr) {
            case NINE -> {
                if (p.tickCount % 6 == 0 && p.getDeltaMovement().horizontalDistanceSqr() > 0.02)
                    FX.dust(level, 0xAAFF96, 0.8F, p.position().add(0, 1, 0), 2, 0.4);
            }
            case SPORE -> {
                if (p.tickCount % 40 == 0) {
                    for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(24),
                            e -> e != p && e.isAlive() && e instanceof Enemy)) {
                        e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 50, 0, false, false));
                    }
                }
                if (p.tickCount % 5 == 0) FX.particle(level, ParticleTypes.SPORE_BLOSSOM_AIR, p.position().add(0, 1.5, 0), 2, 0.6, 0.01);
            }
            case PSYGA -> {
                if (p.tickCount % 10 == 0) FormRide.grantFlight(p);       // la mochila de vuelo
                if (p.getAbilities().flying && p.tickCount % 3 == 0)
                    FX.particle(level, ParticleTypes.CLOUD, p.position().add(0, 0.8, 0).subtract(p.getLookAngle().scale(0.4)), 1, 0.1, 0.01);
            }
            case CIPHER -> {
                Mark m = MARKS.get(p.getUUID());
                if (m != null && (m.until < level.getGameTime() || !m.target.isAlive())) MARKS.remove(p.getUUID());
            }
            case COMPLETE -> {
                // La Complete Form agota: consume hambre rapido. Sin comida, se cae.
                if (!p.isCreative()) p.causeFoodExhaustion(KTouch.isLevel2(p) ? 0.045F : 0.06F);
                KTouch.tickHeld(level, p);
                if (p.tickCount % 10 == 0) {
                    for (KamenRide k : KamenRide.RING) {
                        if (level.getRandom().nextInt(4) == 0) FX.dust(level, k.color, 0.9F, FX.center(p), 1, 0.7);
                    }
                }
                if (!p.isCreative() && p.getFoodData().getFoodLevel() <= 0) {
                    KTouch.exit(level, p, true);
                }
            }
            default -> { }
        }
    }

    // ================================================================== tecla R

    @Nullable
    public static Used ability(ServerLevel level, ServerPlayer p, KamenRide kr, boolean shift) {
        return switch (kr) {
            case NINE -> shift ? airForce(level, p) : smash(level, p);
            case CIPHER -> shift ? codeShot(level, p) : codeMarker(level, p);
            case SPORE -> shift ? sporeCloud(level, p) : sporeNet(level, p);
            case FORGE -> shift ? forgeWall(level, p) : forgeCannon(level, p);
            case WARDEN -> shift ? wardenDash(level, p) : wardenContain(level, p);
            case PSYGA -> shift ? psygaFeathers(level, p) : psygaDive(level, p);
            case ORGA -> shift ? orgaTones(level, p) : orgaBlow(level, p);
            case MIRAGE -> shift ? mirageDoubles(level, p) : mirageFlash(level, p);
            case PROTO -> shift ? protoHook(level, p) : protoThruster(level, p);
            case KAIXA -> shift ? kaixaCut(level, p) : kaixaTwinShot(level, p);
            case COMPLETE -> cardCannon(level, p);
            default -> null;
        };
    }

    // ---------------------------------------------------------------- Nine

    private static Used smash(ServerLevel level, ServerPlayer p) {
        LivingEntity target = FX.findTarget(level, p, 5, 0.6);
        Vec3 front = p.getEyePosition().add(p.getLookAngle().scale(1.5));
        if (target != null) {
            FX.hit(level, p, target, 14.0F);
            FX.push(target, p.position(), 2.2, 0.6);
            front = FX.center(target);
        }
        // onda de choque en cono aunque no haya objetivo directo
        for (LivingEntity e : cone(level, p, 8, 0.75)) {
            if (e == target) continue;
            FX.hit(level, p, e, 6.0F);
            FX.push(e, p.position(), 1.6, 0.4);
        }
        FX.particle(level, ParticleTypes.EXPLOSION, front, 2, 0.3, 0);
        FX.particle(level, ParticleTypes.CLOUD, front, 30, 0.8, 0.2);
        FX.dust(level, 0xAAFF96, 1.6F, front, 25, 0.8);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 1.5F);
        // retroceso del poder: el cuerpo lo paga
        p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
        return new Used("smash", 6);
    }

    private static Used airForce(ServerLevel level, ServerPlayer p) {
        List<LivingEntity> hits = FX.beam(level, p, p.getLookAngle(), 24, 5.0F, 0xE6FFE6, 1.4F, true);
        for (LivingEntity e : hits) FX.push(e, p.position(), 2.0, 0.3);
        sound(level, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.4F, 1.6F);
        return new Used("air_force", 4);
    }

    // ---------------------------------------------------------------- Cipher

    private static Used codeMarker(ServerLevel level, ServerPlayer p) {
        LivingEntity target = FX.findTarget(level, p, 20, 0.85);
        if (target == null) {
            p.sendSystemMessage(Component.translatable("message.dexrider.ability_no_target"), true);
            return null;
        }
        mark(level, p, target, 60);
        return new Used("code_marker", 8);
    }

    private static void mark(ServerLevel level, ServerPlayer p, LivingEntity target, int ticks) {
        MARKS.put(p.getUUID(), new Mark(target, level.getGameTime() + ticks));
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 9));
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0));
        FX.line(level, p.getEyePosition(), FX.center(target), 0xDCF0FF, 0.8F, 0.5);
        for (int t = 0; t < ticks; t += 4) {
            Scheduler.later(p, t + 1, () -> {
                if (!target.isAlive()) return;
                codeCone(level, target);
                target.setDeltaMovement(0, Math.min(0, target.getDeltaMovement().y), 0);
                target.hurtMarked = true;
            });
        }
        sound(level, target.position(), SoundEvents.BEACON_ACTIVATE, 1.0F, 2.0F);
    }

    /** Cono de codigo blanco que apunta hacia abajo sobre el objetivo. */
    private static void codeCone(ServerLevel level, LivingEntity target) {
        Vec3 tip = FX.center(target);
        double h = target.getBbHeight() + 1.4;
        for (int i = 0; i < 4; i++) {
            double r = 0.2 + i * 0.25;
            Vec3 c = tip.add(0, 0.5 + i * 0.4, 0);
            for (int a = 0; a < 360; a += 45) {
                Vec3 at = c.add(Math.cos(Math.toRadians(a)) * r, 0, Math.sin(Math.toRadians(a)) * r);
                FX.dust(level, 0xDCF0FF, 0.7F, at, 1, 0);
            }
        }
        FX.dust(level, 0xFFFFFF, 1.0F, tip.add(0, h * 0.2, 0), 2, 0.05);
    }

    private static Used codeShot(ServerLevel level, ServerPlayer p) {
        for (int i = 0; i < 3; i++) {
            Scheduler.later(p, 1 + i * 3, () -> {
                FX.beam(level, p, p.getLookAngle(), 28, 4.0F, 0xF0F8FF, 1.0F, false);
                sound(level, p.position(), SoundEvents.CROSSBOW_SHOOT, 0.8F, 1.8F);
            });
        }
        return new Used("code_shot", 3);
    }

    // ---------------------------------------------------------------- Spore

    private static Used sporeNet(ServerLevel level, ServerPlayer p) {
        int creatures = 0, players = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(48),
                e -> e != p && e.isAlive())) {
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 300, 0, false, false));
            if (e instanceof Player) players++; else creatures++;
        }
        FX.particle(level, ParticleTypes.SPORE_BLOSSOM_AIR, p.position().add(0, 1, 0), 120, 6.0, 0.02);
        sound(level, p.position(), SoundEvents.AZALEA_LEAVES_BREAK, 1.5F, 0.6F);
        p.sendSystemMessage(Component.translatable("message.dexrider.spore_net", creatures, players), false);
        return new Used("spore_net", 15);
    }

    private static Used sporeCloud(ServerLevel level, ServerPlayer p) {
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(6),
                e -> e != p && e.isAlive())) {
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1));
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 120, 0));
            e.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 120, 0));
        }
        FX.particle(level, ParticleTypes.SPORE_BLOSSOM_AIR, FX.center(p), 80, 3.0, 0.02);
        FX.particle(level, ParticleTypes.MYCELIUM, FX.center(p), 60, 3.0, 0.02);
        sound(level, p.position(), SoundEvents.FIRE_EXTINGUISH, 0.8F, 1.4F);
        return new Used("spore_cloud", 10);
    }

    // ---------------------------------------------------------------- Warden

    /** Contencion: todo lo que esta en un cono de 10 bloques queda inmovilizado 3 s. */
    private static Used wardenContain(ServerLevel level, ServerPlayer p) {
        int n = 0;
        for (LivingEntity e : cone(level, p, 10, 0.6)) {
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 9));
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1));
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0));
            FX.line(level, p.getEyePosition(), FX.center(e), 0xFF9628, 0.8F, 0.5);
            n++;
        }
        if (n == 0) return null;
        sound(level, p.position(), SoundEvents.CHAIN_PLACE, 1.2F, 0.8F);
        return new Used("warden_contain", 8);
    }

    /** Embestida: 8 bloques en linea recta golpeando lo que haya en medio. */
    private static Used wardenDash(ServerLevel level, ServerPlayer p) {
        Vec3 dir = p.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 from = p.position();
        double free = FX.rayEnd(level, p, from.add(0, 0.3, 0), dir, 8).distanceTo(from.add(0, 0.3, 0)) - 0.6;
        Vec3 to = from.add(dir.scale(Math.max(0, free)));
        for (LivingEntity e : FX.alongLine(level, p, from.add(0, 1, 0), to.add(0, 1, 0), 0.8)) {
            FX.hit(level, p, e, 7.0F);
            FX.push(e, from, 1.0, 0.3);
        }
        FX.line(level, from.add(0, 1, 0), to.add(0, 1, 0), 0xE8EAEE, 1.0F, 0.4);
        if (level.noCollision(p, p.getBoundingBox().move(to.subtract(from)))) p.teleportTo(to.x, to.y, to.z);
        sound(level, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 1.4F);
        return new Used("warden_dash", 4);
    }

    // ---------------------------------------------------------------- Psyga

    /** Picada: desde el aire cae en picada sobre el objetivo; en el suelo, salta y planea. */
    private static Used psygaDive(ServerLevel level, ServerPlayer p) {
        if (p.onGround()) {
            p.setDeltaMovement(p.getDeltaMovement().x, 1.4, p.getDeltaMovement().z);
            p.hurtMarked = true;
            FX.particle(level, ParticleTypes.CLOUD, p.position(), 20, 0.5, 0.05);
            return new Used("psyga_lift", 2);
        }
        LivingEntity target = FX.findTarget(level, p, 30, 0.6);
        if (target == null) {
            p.sendSystemMessage(Component.translatable("message.dexrider.ability_no_target"), true);
            return null;
        }
        p.getAbilities().flying = false;
        p.onUpdateAbilities();
        final boolean[] done = {false};
        for (int t = 1; t <= 20; t++) {
            final int tick = t;
            Scheduler.later(p, t, () -> {
                if (done[0] || !target.isAlive()) return;
                Vec3 d = FX.center(target).subtract(p.position()).normalize();
                p.setDeltaMovement(d.scale(1.9));
                p.hurtMarked = true;
                FX.dust(level, 0x5AAAF0, 1.2F, p.position().add(0, 0.5, 0), 4, 0.2);
                if (p.distanceTo(target) < 2.2 || tick == 20) {
                    done[0] = true;
                    impact(level, p, target, 14.0F, 3.0, 0x5AAAF0);
                }
            });
        }
        return new Used("psyga_dive", 7);
    }

    /** Rafaga de plumas de energia en abanico. */
    private static Used psygaFeathers(ServerLevel level, ServerPlayer p) {
        Vec3 look = p.getLookAngle();
        for (int i = -2; i <= 2; i++) FX.beam(level, p, FX.yaw(look, i * 9), 22, 4.0F, 0xAAD7FF, 0.9F, false);
        sound(level, p.position(), SoundEvents.PHANTOM_FLAP, 1.2F, 1.5F);
        return new Used("psyga_feathers", 5);
    }

    // ---------------------------------------------------------------- Orga

    /** Golpe demoledor: un solo golpe directo que lanza lejos y agrieta el suelo. */
    private static Used orgaBlow(ServerLevel level, ServerPlayer p) {
        LivingEntity target = FX.findTarget(level, p, 4.5, 0.6);
        if (target == null) {
            p.sendSystemMessage(Component.translatable("message.dexrider.ability_no_target"), true);
            return null;
        }
        FX.hit(level, p, target, 18.0F);
        FX.push(target, p.position(), 2.8, 0.7);
        BlockPos under = target.blockPosition().below();
        FX.particle(level, new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(under)),
                Vec3.atCenterOf(under).add(0, 0.6, 0), 40, 1.2, 0.2);
        FX.dust(level, 0xF2C230, 1.6F, FX.center(target), 20, 0.5);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 0.7F);
        return new Used("orga_blow", 7);
    }

    /** Leer los tonos: 5 s esquivando todo golpe cuerpo a cuerpo (Genzo lleva 50 anos oyendo las cartas). */
    private static Used orgaTones(ServerLevel level, ServerPlayer p) {
        TONES_UNTIL.put(p.getUUID(), level.getGameTime() + 100);
        FX.ring(level, p, 0xF2C230, 1.2, 4);
        sound(level, p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F, 0.7F);
        return new Used("orga_tones", 16);
    }

    // ---------------------------------------------------------------- Forge

    static Used forgeCannon(ServerLevel level, ServerPlayer p) {
        for (int i = 0; i < 3; i++) {
            Scheduler.later(p, 1 + i * 4, () -> {
                List<LivingEntity> hits = FX.beam(level, p, p.getLookAngle(), 26, 7.0F, 0xB4B4BE, 1.6F, false);
                Vec3 end = hits.isEmpty() ? FX.rayEnd(level, p, p.getEyePosition(), p.getLookAngle(), 26) : FX.center(hits.get(0));
                FX.particle(level, ParticleTypes.EXPLOSION, end, 1, 0.1, 0);
                for (LivingEntity e : hits) FX.push(e, p.position(), 0.8, 0.3);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6F, 1.7F);
            });
        }
        FX.dust(level, 0x78BEFF, 1.0F, p.getEyePosition().add(p.getLookAngle()), 15, 0.3);
        return new Used("forge_cannon", 5);
    }

    static Used forgeWall(ServerLevel level, ServerPlayer p) {
        Direction facing = p.getDirection();
        Direction side = facing.getClockWise();
        BlockPos base = p.blockPosition().relative(facing, 3);
        int placed = 0;
        for (int w = -2; w <= 2; w++) {
            BlockPos column = ground(level, base.relative(side, w));
            for (int h = 0; h < 3; h++) {
                if (TempBlocks.place(level, column.above(h), Blocks.IRON_BLOCK.defaultBlockState(), 160)) placed++;
            }
        }
        if (placed == 0) return null;
        FX.dust(level, 0x78BEFF, 1.4F, base.getCenter().add(0, 1, 0), 40, 1.5);
        sound(level, base.getCenter(), SoundEvents.ANVIL_PLACE, 0.8F, 1.2F);
        return new Used("forge_wall", 10);
    }

    // ---------------------------------------------------------------- Mirage

    private static Used mirageFlash(ServerLevel level, ServerPlayer p) {
        for (LivingEntity e : cone(level, p, 12, 0.5)) {
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
            FX.hit(level, p, e, 4.0F);
        }
        Vec3 c = p.getEyePosition().add(p.getLookAngle());
        FX.particle(level, ParticleTypes.END_ROD, c, 60, 1.2, 0.25);
        sound(level, p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5F, 1.6F);
        return new Used("mirage_flash", 6);
    }

    private static Used mirageDoubles(ServerLevel level, ServerPlayer p) {
        AllyManager.mirageDoubles(level, p, 2);
        sound(level, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.5F, 0.8F);
        return new Used("mirage_doubles", 16);
    }

    // ---------------------------------------------------------------- Proto

    private static Used protoThruster(ServerLevel level, ServerPlayer p) {
        Vec3 d = p.getLookAngle();
        p.setDeltaMovement(d.x * 1.8, Math.max(0.5, d.y * 1.4 + 0.4), d.z * 1.8);
        p.hurtMarked = true;
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false, true));
        p.resetFallDistance();
        for (int t = 1; t <= 10; t++) {
            Scheduler.later(p, t, () -> FX.particle(level, ParticleTypes.FLAME, p.position(), 4, 0.15, 0.02));
        }
        sound(level, p.position(), SoundEvents.FIRECHARGE_USE, 1.0F, 0.8F);
        return new Used("proto_thruster", 4);
    }

    private static Used protoHook(ServerLevel level, ServerPlayer p) {
        LivingEntity target = FX.findTarget(level, p, 20, 0.85);
        if (target == null) {
            p.sendSystemMessage(Component.translatable("message.dexrider.ability_no_target"), true);
            return null;
        }
        Vec3 d = p.position().subtract(target.position()).normalize();
        target.setDeltaMovement(d.x * 1.6, 0.4, d.z * 1.6);
        target.hurtMarked = true;
        FX.hit(level, p, target, 3.0F);
        FX.line(level, p.getEyePosition(), FX.center(target), 0xFF8418, 1.0F, 0.4);
        sound(level, p.position(), SoundEvents.FISHING_BOBBER_THROW, 1.2F, 0.6F);
        return new Used("proto_hook", 5);
    }

    // ---------------------------------------------------------------- Complete Form

    private static Used cardCannon(ServerLevel level, ServerPlayer p) {
        List<LivingEntity> hits = FX.beam(level, p, p.getLookAngle(), 32, 12.0F, FX.MAGENTA, 2.0F, true);
        for (LivingEntity e : hits) FX.push(e, p.position(), 1.0, 0.3);
        Vec3 start = p.getEyePosition();
        FX.card(level, start.add(p.getLookAngle().scale(1.5)), p.getLookAngle(), FX.GOLD);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8F, 1.8F);
        return new Used("card_cannon", 5);
    }

    // ================================================================== Final Attack Ride

    /** true si esta identidad tiene su propio Final Attack Ride. */
    public static boolean hasFinisher(KamenRide kr) {
        return kr == KamenRide.NINE || kr == KamenRide.CIPHER || kr == KamenRide.SPORE
                || kr == KamenRide.FORGE || kr == KamenRide.MIRAGE || kr == KamenRide.PROTO
                || kr == KamenRide.WARDEN || kr == KamenRide.PSYGA || kr == KamenRide.ORGA || kr == KamenRide.KAIXA;
    }

    public static void finalAttack(ServerLevel level, ServerPlayer p, KamenRide kr) {
        DexSounds.play(level, p, DexSounds.VOICE_FINAL_ATTACK_RIDE, 1.0F, 1.0F);
        DexSounds.play(level, p, DexSounds.FAR_CHARGE, 1.2F, 1.0F);
        p.sendSystemMessage(Component.translatable("message.dexrider.far_rider", kr.displayName()), true);
        switch (kr) {
            case NINE -> risingSmash(level, p);
            case CIPHER -> { if (!CipherModes.finisher(level, p)) codeStrike(level, p); }
            case SPORE -> sporeBloom(level, p);
            case FORGE -> forgeBarrage(level, p);
            case MIRAGE -> prismRefraction(level, p);
            case PROTO -> plasmaCutter(level, p);
            case WARDEN -> wardenSentence(level, p);
            case PSYGA -> psygaSkyfall(level, p);
            case ORGA -> orgaJudgement(level, p);
            case KAIXA -> kaixaBreaker(level, p);
            default -> { }
        }
    }

    // ---------------------------------------------------------------- Kaixa (Tetsuya)

    private static final int KAIXA_VIOLET = 0xFFC434;   // lineas doradas del traje

    /** Dos disparos de la espada-pistola, uno detras de otro, al primer objetivo en la linea. */
    private static Used kaixaTwinShot(ServerLevel level, ServerPlayer p) {
        for (int i = 0; i < 2; i++) {
            Scheduler.later(p, 1 + i * 4, () -> {
                Vec3 eye = p.getEyePosition(), dir = p.getLookAngle();
                Vec3 end = FX.rayEnd(level, p, eye, dir, 28);
                LivingEntity t = null;
                for (LivingEntity e : FX.alongLine(level, p, eye, end, 0.7)) { t = e; break; }
                if (t != null) { end = FX.center(t); FX.hit(level, p, t, 7.0F); }
                FX.line(level, eye.add(dir.scale(0.8)), end, KAIXA_VIOLET, 0.9F, 0.35);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.8F, 1.6F);
            });
        }
        return new Used("kaixa_twin_shot", 3);
    }

    /** Tajo de remate: avanza en linea recta cortando todo lo que atraviesa; mas dano a los que ya estan tocados. */
    private static Used kaixaCut(ServerLevel level, ServerPlayer p) {
        Vec3 dir = p.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 from = p.position();
        double free = FX.rayEnd(level, p, from.add(0, 0.3, 0), dir, 7).distanceTo(from.add(0, 0.3, 0)) - 0.6;
        Vec3 to = from.add(dir.scale(Math.max(0, free)));
        for (LivingEntity e : FX.alongLine(level, p, from.add(0, 1, 0), to.add(0, 1, 0), 0.9)) {
            FX.hit(level, p, e, e.getHealth() < e.getMaxHealth() * 0.5F ? 12.0F : 7.0F);   // el que termina lo que otros dejaron
            FX.push(e, from, 0.8, 0.3);
        }
        FX.line(level, from.add(0, 1, 0), to.add(0, 1, 0), KAIXA_VIOLET, 1.1F, 0.4);
        if (level.noCollision(p, p.getBoundingBox().move(to.subtract(from)))) p.teleportTo(to.x, to.y, to.z);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2F, 1.2F);
        return new Used("kaixa_cut", 5);
    }

    /** Final Attack Ride: Cobalt Breaker. Salta, fija al objetivo con seis disparos y cae con un tajo en cruz. */
    /** El mismo final, desde la espada-pistola (Armed Ride: Kaixa Gunblade). */
    public static void cobaltBreaker(ServerLevel level, ServerPlayer p) { kaixaBreaker(level, p); }

    private static void kaixaBreaker(ServerLevel level, ServerPlayer p) {
        LivingEntity target = FX.findTarget(level, p, 24, 0.55);
        p.setDeltaMovement(p.getDeltaMovement().x, 1.1, p.getDeltaMovement().z);
        p.hurtMarked = true;
        FX.ring(level, p, KAIXA_VIOLET, 1.2, 6);
        if (target == null) return;
        final LivingEntity t = target;
        for (int i = 0; i < 6; i++) {
            Scheduler.later(p, 6 + i * 2, () -> {
                if (!t.isAlive()) return;
                FX.line(level, p.getEyePosition(), FX.center(t), KAIXA_VIOLET, 0.8F, 0.4);
                FX.hit(level, p, t, 3.0F);
                t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 3));
            });
        }
        Scheduler.later(p, 20, () -> {
            if (!t.isAlive()) return;
            Vec3 at = FX.center(t);
            p.teleportTo(at.x, t.getY() + 0.2, at.z);
            FX.hit(level, p, t, 22.0F);
            FX.line(level, at.add(-1.5, 1.5, 0), at.add(1.5, -1.5, 0), KAIXA_VIOLET, 1.4F, 0.2);
            FX.line(level, at.add(1.5, 1.5, 0), at.add(-1.5, -1.5, 0), 0x385CBE, 1.4F, 0.2);
            level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 2, 0.4, 0.4, 0.4, 0);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2F, 1.2F);
        });
    }

    private static void risingSmash(ServerLevel level, ServerPlayer p) {
        p.setDeltaMovement(p.getDeltaMovement().x, 1.4, p.getDeltaMovement().z);
        p.hurtMarked = true;
        FX.dust(level, 0xAAFF96, 1.6F, p.position(), 30, 0.6);
        Scheduler.later(p, 12, () -> {
            LivingEntity target = FX.findTarget(level, p, 28, 0.5);
            Vec3 aim = target != null ? FX.center(target) : p.position().add(p.getLookAngle().scale(8)).add(0, -6, 0);
            Vec3 dir = aim.subtract(p.position()).normalize();
            p.setDeltaMovement(dir.scale(2.2));
            p.hurtMarked = true;
            final boolean[] done = {false};
            for (int t = 1; t <= 24; t++) {
                final int tick = t;
                Scheduler.later(p, t, () -> {
                    if (done[0]) return;
                    FX.dust(level, 0xAAFF96, 1.4F, p.position().add(0, 0.5, 0), 6, 0.3);
                    FX.particle(level, ParticleTypes.ELECTRIC_SPARK, p.position(), 4, 0.3, 0.1);
                    boolean near = target != null && p.distanceTo(target) < 2.6;
                    if (near || p.onGround() || tick == 24) {
                        done[0] = true;
                        impact(level, p, target, 22.0F, 4.0, 0xAAFF96);
                    } else {
                        p.setDeltaMovement(dir.scale(2.2));
                        p.hurtMarked = true;
                    }
                });
            }
        });
    }

    private static void codeStrike(ServerLevel level, ServerPlayer p) {
        Mark m = MARKS.get(p.getUUID());
        LivingEntity target = m != null && m.target.isAlive() && p.distanceTo(m.target) < 32 ? m.target : null;
        int delay = 0;
        if (target == null) {
            target = FX.findTarget(level, p, 24, 0.6);
            if (target == null) {
                p.sendSystemMessage(Component.translatable("message.dexrider.ability_no_target"), true);
                return;
            }
            mark(level, p, target, 50);
            delay = 12;
        }
        final LivingEntity t = target;
        p.setDeltaMovement(p.getDeltaMovement().x, 0.9, p.getDeltaMovement().z);
        p.hurtMarked = true;
        Scheduler.later(p, delay + 8, () -> {
            final boolean[] done = {false};
            for (int i = 1; i <= 16; i++) {
                final int tick = i;
                Scheduler.later(p, i, () -> {
                    if (done[0]) return;
                    if (!t.isAlive()) { done[0] = true; return; }
                    Vec3 dir = FX.center(t).subtract(p.position()).normalize();
                    p.setDeltaMovement(dir.scale(1.8));
                    p.hurtMarked = true;
                    FX.dust(level, 0xDCF0FF, 1.2F, p.position().add(0, 0.5, 0), 6, 0.2);
                    if (p.distanceTo(t) < 2.2 || tick == 16) {
                        done[0] = true;
                        // atraviesa: aparece al otro lado
                        Vec3 through = FX.center(t).add(dir.scale(2.0));
                        if (level.noCollision(p, p.getBoundingBox().move(through.subtract(p.position()).add(0, -0.9, 0)))) {
                            p.teleportTo(through.x, through.y - 0.9, through.z);
                        }
                        impact(level, p, t, 26.0F, 2.5, 0xDCF0FF);
                        MARKS.remove(p.getUUID());
                    }
                });
            }
        });
    }

    private static void sporeBloom(ServerLevel level, ServerPlayer p) {
        BlockPos c = p.blockPosition();
        BlockState red = Blocks.RED_MUSHROOM_BLOCK.defaultBlockState();
        BlockState brown = Blocks.BROWN_MUSHROOM_BLOCK.defaultBlockState();
        BlockState stem = Blocks.MUSHROOM_STEM.defaultBlockState();
        for (int a = 0; a < 360; a += 30) {
            BlockPos base = c.offset((int) Math.round(Math.cos(Math.toRadians(a)) * 5), 0, (int) Math.round(Math.sin(Math.toRadians(a)) * 5));
            final BlockPos b = ground(level, base);
            final boolean isRed = (a / 30) % 2 == 0;
            Scheduler.later(p, 1 + a / 30, () -> {
                TempBlocks.place(level, b, stem, 120);
                TempBlocks.place(level, b.above(), stem, 120);
                TempBlocks.place(level, b.above(2), isRed ? red : brown, 120);
                FX.particle(level, ParticleTypes.SPORE_BLOSSOM_AIR, b.above(2).getCenter(), 10, 0.5, 0.02);
            });
        }
        Scheduler.later(p, 14, () -> {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c).inflate(6, 3, 6),
                    e -> e != p && e.isAlive())) {
                FX.hit(level, p, e, 12.0F);
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 1));
                e.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
            }
            FX.particle(level, ParticleTypes.SPORE_BLOSSOM_AIR, c.getCenter().add(0, 1, 0), 200, 5.0, 0.05);
            FX.particle(level, ParticleTypes.EXPLOSION, c.getCenter().add(0, 1, 0), 3, 2.0, 0);
            DexSounds.play(level, p, DexSounds.FAR_IMPACT, 1.5F, 1.2F);
        });
    }

    static void forgeBarrage(ServerLevel level, ServerPlayer p) {
        LivingEntity target = FX.findTarget(level, p, 32, 0.6);
        Vec3 c = target != null ? target.position() : FX.rayEnd(level, p, p.getEyePosition(), p.getLookAngle(), 32);
        for (int i = 0; i < 6; i++) {
            final int k = i;
            Scheduler.later(p, 6 + i * 5, () -> {
                Vec3 at = (target != null && target.isAlive() ? target.position() : c)
                        .add((level.getRandom().nextDouble() - 0.5) * 3, 0, (level.getRandom().nextDouble() - 0.5) * 3);
                FX.line(level, at.add(0, 12, 0), at, 0xB4B4BE, 1.6F, 0.6);
                FX.particle(level, ParticleTypes.EXPLOSION, at.add(0, 0.5, 0), 2, 0.4, 0);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(3),
                        e -> e != p && e.isAlive())) {
                    FX.hit(level, p, e, 8.0F);
                    FX.push(e, at, 0.5, 0.5);
                }
                level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2F, 1.0F + k * 0.05F);
            });
        }
    }

    private static void prismRefraction(ServerLevel level, ServerPlayer p) {
        LivingEntity target = FX.findTarget(level, p, 28, 0.6);
        if (target == null) {
            p.sendSystemMessage(Component.translatable("message.dexrider.ability_no_target"), true);
            return;
        }
        int[] colors = {0x5096FF, 0x8C78FF, 0xAA64F0, 0xDC78C8, 0xFFB450, 0xFFC846};
        for (int i = 0; i < 6; i++) {
            final int k = i;
            Scheduler.later(p, 4 + i * 3, () -> {
                if (!target.isAlive()) return;
                double a = Math.toRadians(k * 60);
                Vec3 c = FX.center(target);
                Vec3 from = c.add(Math.cos(a) * 5, 3, Math.sin(a) * 5);
                FX.line(level, from, c, colors[k], 1.3F, 0.3);
                FX.hit(level, p, target, 5.0F);
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                sound(level, c, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 1.0F + k * 0.15F);
            });
        }
        Scheduler.later(p, 24, () -> {
            if (target.isAlive()) FX.particle(level, ParticleTypes.END_ROD, FX.center(target), 50, 0.6, 0.2);
        });
    }

    private static void plasmaCutter(ServerLevel level, ServerPlayer p) {
        Scheduler.later(p, 10, () -> {
            List<LivingEntity> hits = FX.beam(level, p, p.getLookAngle(), 24, 20.0F, 0xFF8418, 2.2F, true);
            for (LivingEntity e : hits) e.igniteForSeconds(6.0F);
            Vec3 end = FX.rayEnd(level, p, p.getEyePosition(), p.getLookAngle(), 24);
            FX.particle(level, ParticleTypes.EXPLOSION_EMITTER, end, 1, 0, 0);
            FX.particle(level, ParticleTypes.LAVA, end, 20, 1.0, 0.1);
            DexSounds.play(level, p, DexSounds.FAR_IMPACT, 1.4F, 1.3F);
        });
        FX.particle(level, ParticleTypes.FLAME, p.getEyePosition().add(p.getLookAngle()), 30, 0.3, 0.05);
        sound(level, p.position(), SoundEvents.FIRECHARGE_USE, 1.2F, 0.6F);
    }

    /** Warden Sentence: tres embestidas instantaneas contra hasta tres objetivos. */
    private static void wardenSentence(ServerLevel level, ServerPlayer p) {
        java.util.List<LivingEntity> targets = new java.util.ArrayList<>(level.getEntitiesOfClass(LivingEntity.class,
                p.getBoundingBox().inflate(16), e -> e != p && e.isAlive() && p.hasLineOfSight(e)));
        targets.sort(java.util.Comparator.comparingDouble(p::distanceToSqr));
        if (targets.isEmpty()) {
            p.sendSystemMessage(Component.translatable("message.dexrider.ability_no_target"), true);
            return;
        }
        for (int i = 0; i < Math.min(3, targets.size()); i++) {
            final LivingEntity t = targets.get(i);
            Scheduler.later(p, 6 + i * 8, () -> {
                if (!t.isAlive()) return;
                Vec3 from = p.position();
                Vec3 behind = t.position().add(t.position().subtract(from).multiply(1, 0, 1).normalize().scale(1.2));
                FX.line(level, from.add(0, 1, 0), behind.add(0, 1, 0), 0xFF9628, 1.2F, 0.3);
                if (level.noCollision(p, p.getBoundingBox().move(behind.subtract(from)))) p.teleportTo(behind.x, behind.y, behind.z);
                FX.hit(level, p, t, 16.0F);
                FX.push(t, p.position(), 1.0, 0.4);
                level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.4F, 0.8F);
            });
        }
        Scheduler.later(p, 32, () -> DexSounds.play(level, p, DexSounds.FAR_IMPACT, 1.2F, 1.1F));
    }

    /** Psyga Skyfall: sube alto y llueven ocho impactos de energia sobre la zona. */
    private static void psygaSkyfall(ServerLevel level, ServerPlayer p) {
        p.setDeltaMovement(0, 1.6, 0);
        p.hurtMarked = true;
        LivingEntity target = FX.findTarget(level, p, 32, 0.4);
        Vec3 c = target != null ? target.position() : FX.rayEnd(level, p, p.getEyePosition(), p.getLookAngle(), 24);
        for (int i = 0; i < 8; i++) {
            Scheduler.later(p, 14 + i * 3, () -> {
                Vec3 at = c.add((level.getRandom().nextDouble() - 0.5) * 6, 0, (level.getRandom().nextDouble() - 0.5) * 6);
                FX.line(level, p.getEyePosition(), at, 0xAAD7FF, 1.2F, 0.5);
                FX.particle(level, ParticleTypes.EXPLOSION, at.add(0, 0.5, 0), 1, 0, 0);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(2.5),
                        e -> e != p && e.isAlive())) {
                    FX.hit(level, p, e, 7.0F);
                }
            });
        }
        Scheduler.later(p, 40, () -> DexSounds.play(level, p, DexSounds.FAR_IMPACT, 1.2F, 1.3F));
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
            FX.line(level, start.add(dir).add(0, 0.4, 0), end.add(0, 0.4, 0), 0xFFAA28, 1.2F, 0.3);
            FX.particle(level, ParticleTypes.EXPLOSION_EMITTER, end, 1, 0, 0);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.6F, 0.6F);
            DexSounds.play(level, p, DexSounds.FAR_IMPACT, 1.4F, 0.8F);
        });
        FX.ring(level, p, 0xF2C230, 1.4, 6);
    }

    private static void impact(ServerLevel level, ServerPlayer p, @Nullable LivingEntity target, float damage, double radius, int color) {
        Vec3 c = target != null ? FX.center(target) : p.position();
        if (target != null) FX.hit(level, p, target, damage);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(radius),
                e -> e != p && e != target && e.isAlive())) {
            FX.hit(level, p, e, damage * 0.45F);
            FX.push(e, c, 1.2, 0.5);
        }
        FX.particle(level, ParticleTypes.EXPLOSION_EMITTER, c, 1, 0, 0);
        FX.dust(level, color, 2.0F, c, 50, 1.5);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0F, 0.9F);
        DexSounds.play(level, p, DexSounds.FAR_IMPACT, 1.5F, 1.0F);
        p.resetFallDistance();
        p.setDeltaMovement(p.getDeltaMovement().scale(0.2).add(0, 0.4, 0));
        p.hurtMarked = true;
    }

    // ================================================================== multiplicador de la Complete Form

    /** +0,2 por cada Kamen Ride del mazo (con diez cartas, x3). */
    public static float completeMultiplier(ServerPlayer p) {
        DriverDeck deck = DexDriverItem.getDeck(DexDriverItem.findDriver(p));
        // Nv.2: cada Kamen Ride suma +0,3 en vez de +0,2 (la forma crece con cada identidad nueva)
        return 1.0F + (KTouch.isLevel2(p) ? 0.3F : 0.2F) * deck.count(DexCard.Family.KAMEN_RIDE);
    }

    // ================================================================== comun

    static List<LivingEntity> cone(ServerLevel level, ServerPlayer p, double range, double minDot) {
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        return level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range), e -> {
            if (e == p || !e.isAlive()) return false;
            Vec3 to = FX.center(e).subtract(eye);
            return to.length() <= range && to.normalize().dot(look) >= minDot;
        });
    }

    static BlockPos ground(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos m = pos.mutable();
        for (int i = 0; i < 3 && !level.getBlockState(m).canBeReplaced(); i++) m.move(Direction.UP);
        for (int i = 0; i < 4 && level.getBlockState(m.below()).canBeReplaced(); i++) m.move(Direction.DOWN);
        return m.immutable();
    }

    static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }
}
