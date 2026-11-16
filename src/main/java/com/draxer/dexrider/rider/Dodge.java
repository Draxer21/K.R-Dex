package com.draxer.dexrider.rider;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Esquiva (0.56, tecla propia; boton lateral del raton por defecto): un salto corto en la direccion en que te mueves
 * (hacia atras si no te mueves) con unos ticks de invulnerabilidad. Gasta aguante.
 *  - Esquivar un golpe en esos ticks es una ESQUIVA PERFECTA: devuelve parte del aguante y deja listo el golpe
 *    "tras esquiva" del moveset durante un poco mas de tiempo.
 *  - El primer golpe despues de esquivar usa el movimiento "tras esquiva" del arma.
 */
public final class Dodge {
    private Dodge() {}

    public static final int COST = 30, IFRAMES = 8, COOLDOWN = 12, AFTER = 16, AFTER_PERFECT = 30;

    private static final Map<UUID, long[]> STATE = new HashMap<>();   // [ultima esquiva, invulnerable hasta, ventana del golpe hasta, esquiva ya premiada]

    private static long[] of(ServerPlayer p) { return STATE.computeIfAbsent(p.getUUID(), u -> new long[]{-1000, -1000, -1000, -2000}); }

    /** forward / strafe: -1, 0 o 1 segun las teclas de movimiento del cliente (strafe +1 = izquierda). */
    public static boolean dodge(ServerPlayer p, int forward, int strafe) {
        if (!Transformation.isTransformed(p) || p.isPassenger() || RiderKick.isKicking(p) || p.isSpectator()) return false;
        if (!(p.level() instanceof ServerLevel level)) return false;
        long now = level.getGameTime();
        long[] s = of(p);
        if (now - s[0] < COOLDOWN) return false;
        if (!Stamina.spend(p, COST)) {
            p.sendSystemMessage(Component.translatable("message.dexrider.no_stamina"), true);
            return false;
        }
        if (forward == 0 && strafe == 0) forward = -1;
        float yaw = (float) Math.toRadians(p.getYRot());
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 left = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3 dir = fwd.scale(forward).add(left.scale(strafe)).normalize();
        double speed = p.onGround() ? 1.15 : 0.7;
        p.setDeltaMovement(dir.x * speed, p.onGround() ? 0.22 : Math.max(0.1, p.getDeltaMovement().y), dir.z * speed);
        p.hurtMarked = true;
        s[0] = now;
        s[1] = now + IFRAMES;
        s[2] = now + AFTER;
        String anim = forward > 0 ? "dodge_f" : forward < 0 && strafe == 0 ? "dodge_b" : strafe > 0 ? "dodge_l" : strafe < 0 ? "dodge_r" : "dodge_b";
        com.draxer.dexrider.network.PosePayload.send(p, anim);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.7F, 1.4F);
        FX.dust(level, KamenRide.current(p).color, 1.3F, FX.center(p), 10, 0.35);
        FX.particle(level, ParticleTypes.CLOUD, p.position().add(0, 0.1, 0), 4, 0.25, 0.02);
        return true;
    }

    public static boolean invulnerable(ServerPlayer p) {
        long[] s = STATE.get(p.getUUID());
        return s != null && p.level().getGameTime() <= s[1];
    }

    /** Golpe entrante durante la esquiva: se anula. Devuelve true si se esquivo (esquiva perfecta). */
    public static boolean onIncoming(ServerLevel level, ServerPlayer p, DamageSource src) {
        if (!invulnerable(p)) return false;
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || src.is(DamageTypeTags.IS_FALL)) return false;
        if (src.getEntity() == null && src.getDirectEntity() == null && !src.is(DamageTypeTags.IS_EXPLOSION)) return false;
        long[] s = of(p);
        boolean first = s[3] != s[0];
        s[3] = s[0];
        s[2] = level.getGameTime() + AFTER_PERFECT;
        if (first) {
            Stamina.give(p, COST / 2);
            FX.dust(level, FX.WHITE, 1.5F, FX.center(p), 14, 0.5);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.6F);
            p.sendSystemMessage(Component.translatable("message.dexrider.perfect_dodge"), true);
            DexAdvancements.award(p, "perfect_dodge");
        }
        return true;
    }

    public static boolean justDodged(ServerPlayer p) {
        long[] s = STATE.get(p.getUUID());
        return s != null && p.level().getGameTime() <= s[2];
    }

    public static void consume(ServerPlayer p) {
        long[] s = STATE.get(p.getUUID());
        if (s != null) s[2] = -1000;
    }

    public static void forget(UUID id) { STATE.remove(id); }
}
