package com.draxer.dexrider.rider;

import com.draxer.dexrider.registry.DexComponents;
import com.draxer.dexrider.registry.DexItems;
import com.draxer.dexrider.registry.DexSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Modos de Cipher (del fanfic). Para cambiar hay que EQUIPARSE algo:
 *
 *  ACCEL   : el reloj Accel (lo construyo Takumi a los 19). Clic derecho con el reloj siendo Kamen Ride: Cipher.
 *            10 segundos de velocidad absoluta: todo a tu alrededor va lentisimo, tus golpes pegan x1.5.
 *            Regla de Takumi, "siete y vuelves": aviso a los 7 s; a los 10 el sistema se cierra y el cuerpo
 *            cae (lento y debil 5 s). Recarga de 60 s. El peto se abre y las trazas y el visor pasan a rojo.
 *  BLASTER : el arma Blaster (el equipo lo guarda Genzo). Mientras la llevas en la mano principal siendo Cipher,
 *            entras en la forma de combate total: Fuerza III, Resistencia II. El arma dispara (clic derecho) y corta
 *            (Shift + clic derecho), y el Final Attack Ride se vuelve "Blaster Overdrive".
 *
 * El modo se guarda en el peto (componente CIPHER_MODE) para que todos vean el modelo:
 * rider_cipher_accel / rider_cipher_blaster.
 */
public final class CipherModes {
    private CipherModes() {}

    public static final String ACCEL = "accel", BLASTER = "blaster";
    public static final int ACCEL_TICKS = 200, ACCEL_WARN = 140, ACCEL_COOLDOWN = 1200;

    private static final Map<UUID, Long> ACCEL_UNTIL = new HashMap<>();

    public static String mode(LivingEntity e) {
        return e.getItemBySlot(EquipmentSlot.CHEST).get(DexComponents.CIPHER_MODE.get());
    }

    private static void setMode(ServerPlayer p, String mode) {
        if (mode == null) p.getItemBySlot(EquipmentSlot.CHEST).remove(DexComponents.CIPHER_MODE.get());
        else p.getItemBySlot(EquipmentSlot.CHEST).set(DexComponents.CIPHER_MODE.get(), mode);
    }

    public static boolean isCipher(Player p) {
        return Transformation.isTransformed(p) && KamenRide.current(p) == KamenRide.CIPHER;
    }

    /** Al cambiar de identidad o destransformarse. */
    public static void clear(ServerPlayer p) {
        ACCEL_UNTIL.remove(p.getUUID());
        setMode(p, null);
    }

    // ------------------------------------------------------------------ Accel

    /** Clic derecho con el reloj Accel. Devuelve true si arranco. */
    public static boolean startAccel(ServerLevel level, ServerPlayer p) {
        if (!isCipher(p)) {
            p.sendSystemMessage(Component.translatable("message.dexrider.cipher_need"), true);
            return false;
        }
        if (ACCEL_UNTIL.containsKey(p.getUUID())) return false;
        ACCEL_UNTIL.put(p.getUUID(), level.getGameTime() + ACCEL_TICKS);
        setMode(p, ACCEL);
        FX.ring(level, p, 0xFF463C, 1.4, 8);
        FX.particle(level, ParticleTypes.ELECTRIC_SPARK, FX.center(p), 40, 0.6, 0.3);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2F, 1.8F);
        DexSounds.play(level, p, DexSounds.HENSHIN, 1.0F, 1.6F);
        p.sendSystemMessage(Component.translatable("message.dexrider.accel_start"), true);
        return true;
    }

    /** Cada tick estando transformado. */
    public static void tick(ServerLevel level, ServerPlayer p) {
        Long until = ACCEL_UNTIL.get(p.getUUID());
        if (until != null) {
            long left = until - level.getGameTime();
            if (left <= 0) { endAccel(level, p); return; }
            accelTick(level, p, left);
            return;
        }
        // Blaster: llevar el arma en la mano principal siendo Cipher
        boolean blaster = isCipher(p) && p.getMainHandItem().is(DexItems.CIPHER_BLASTER.get());
        String mode = mode(p);
        if (blaster && !BLASTER.equals(mode)) {
            setMode(p, BLASTER);
            FX.ring(level, p, 0xFF3628, 1.6, 9);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2F, 0.8F);
            p.sendSystemMessage(Component.translatable("message.dexrider.blaster_on"), true);
            Transformation.applyFormEffects(p);
        } else if (!blaster && BLASTER.equals(mode)) {
            setMode(p, null);
            RiderPowers.clear(p);
            Transformation.applyFormEffects(p);
            p.sendSystemMessage(Component.translatable("message.dexrider.blaster_off"), true);
        }
    }

    private static void accelTick(ServerLevel level, ServerPlayer p, long left) {
        p.addEffect(new MobEffectInstance(MobEffects.SPEED, 10, 5, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.HASTE, 10, 3, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 10, 1, false, false, true));
        // todo lo demas va lentisimo
        if (p.tickCount % 5 == 0) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(24),
                    e -> e != p && e.isAlive() && !(e instanceof com.draxer.dexrider.entity.RiderAllyEntity))) {
                e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, 5, false, false));
                e.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 10, 2, false, false));
            }
        }
        if (p.tickCount % 2 == 0) FX.dust(level, 0xFF463C, 1.0F, p.position().add(0, 1, 0), 3, 0.3);
        if (left % 20 == 0) {
            int s = (int) (left / 20);
            p.sendSystemMessage(Component.translatable("message.dexrider.accel_count", s), true);
            if (left == ACCEL_TICKS - ACCEL_WARN) {
                // "siete y vuelves"
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.2F, 0.6F);
                p.sendSystemMessage(Component.translatable("message.dexrider.accel_warn"), true);
            }
        }
    }

    private static void endAccel(ServerLevel level, ServerPlayer p) {
        ACCEL_UNTIL.remove(p.getUUID());
        if (ACCEL.equals(mode(p))) setMode(p, null);
        p.removeEffect(MobEffects.SPEED);
        p.removeEffect(MobEffects.HASTE);
        // el cuerpo no puede seguir el ritmo
        p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 1));
        p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
        FX.particle(level, ParticleTypes.LARGE_SMOKE, FX.center(p), 20, 0.4, 0.03);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 0.8F);
        p.sendSystemMessage(Component.translatable("message.dexrider.accel_end"), true);
        Transformation.applyFormEffects(p);
    }

    public static boolean isAccel(Player p) {
        return ACCEL_UNTIL.containsKey(p.getUUID());
    }

    /** Multiplicador de dano del modo activo. */
    public static float damageScale(Player p) {
        if (!isCipher(p)) return 1.0F;
        if (isAccel(p)) return 1.5F;
        if (BLASTER.equals(mode(p))) return 1.4F;
        return 1.0F;
    }

    /** Pasivas del Blaster (se suman a las de Cipher). Devuelve true si las aplico. */
    public static boolean blasterEffects(ServerPlayer p) {
        if (!BLASTER.equals(mode(p))) return false;
        RiderPowers.effect(p, MobEffects.STRENGTH, 2);
        RiderPowers.effect(p, MobEffects.RESISTANCE, 1);
        RiderPowers.effect(p, MobEffects.SPEED, 0);
        return true;
    }

    // ------------------------------------------------------------------ arma Blaster

    /** Clic derecho con el arma Blaster: disparo; Shift: tajo amplio. Devuelve la recarga en ticks (0 = no se uso). */
    public static int useBlaster(ServerLevel level, ServerPlayer p) {
        if (!isCipher(p)) {
            p.sendSystemMessage(Component.translatable("message.dexrider.cipher_need"), true);
            return 0;
        }
        if (p.isShiftKeyDown()) {
            for (LivingEntity e : RiderPowers.cone(level, p, 6, 0.3)) {
                FX.hit(level, p, e, 14.0F);
                FX.push(e, p.position(), 1.2, 0.3);
            }
            FX.dust(level, 0xFF3628, 1.6F, p.getEyePosition().add(p.getLookAngle().scale(2)), 40, 1.6);
            RiderPowers.sound(level, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.4F, 0.6F);
            return 30;
        }
        List<LivingEntity> hits = FX.beam(level, p, p.getLookAngle(), 40, 16.0F, 0xFF3628, 2.0F, true);
        for (LivingEntity e : hits) FX.push(e, p.position(), 1.0, 0.2);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.7F, 1.8F);
        return 40;
    }

    // ------------------------------------------------------------------ Final Attack Ride

    /** Final de Cipher segun el modo. Devuelve true si lo hizo (si no, el normal: Code Strike). */
    public static boolean finisher(ServerLevel level, ServerPlayer p) {
        if (isAccel(p)) {
            // Accel Strike: golpea a todos los cercanos casi a la vez
            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(16),
                    e -> e != p && e.isAlive() && !(e instanceof com.draxer.dexrider.entity.RiderAllyEntity));
            int n = 0;
            for (LivingEntity e : targets) {
                if (n++ >= 6) break;
                final LivingEntity t = e;
                Scheduler.later(p, 2 + n * 2, () -> {
                    if (!t.isAlive()) return;
                    FX.line(level, FX.center(p), FX.center(t), 0xFF463C, 1.2F, 0.4);
                    FX.hit(level, p, t, 18.0F);
                    FX.particle(level, ParticleTypes.EXPLOSION, FX.center(t), 1, 0, 0);
                });
            }
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5F, 1.8F);
            return true;
        }
        if (BLASTER.equals(mode(p))) {
            // Blaster Overdrive: carga y descarga un haz enorme
            FX.dust(level, 0xFF3628, 2.0F, FX.center(p), 50, 1.2);
            Scheduler.later(p, 16, () -> {
                List<LivingEntity> hits = FX.beam(level, p, p.getLookAngle(), 60, 40.0F, 0xFF3628, 3.0F, true);
                Vec3 end = hits.isEmpty() ? FX.rayEnd(level, p, p.getEyePosition(), p.getLookAngle(), 60) : FX.center(hits.get(hits.size() - 1));
                FX.particle(level, ParticleTypes.EXPLOSION_EMITTER, end, 2, 1.0, 0);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(end, end).inflate(4), e -> e != p && e.isAlive())) {
                    FX.hit(level, p, e, 16.0F);
                    FX.push(e, end, 1.6, 0.6);
                }
                level.playSound(null, end.x, end.y, end.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0F, 0.7F);
            });
            return true;
        }
        return false;
    }
}
