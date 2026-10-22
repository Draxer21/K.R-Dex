package com.draxer.dexrider.rider;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bloqueo y contraataque (transformado).
 *
 *  - PARADA: pulsar Shift justo antes de que llegue un golpe de frente (5 ticks; 7 en la UpgForm, que no
 *    tiene miedo). Anula el golpe, aturde al atacante y deja listo un CONTRAATAQUE: tu siguiente golpe en 2 s
 *    pega un 60 % mas.
 *  - BLOQUEO: si mantienes Shift (hasta 1,5 s desde que lo pulsaste) el golpe de frente se reduce a la mitad,
 *    a cambio de un poco de hambre.
 *  - Pulsar Shift sin parar (mas de dos veces en un segundo) no sirve: el pulso tiembla y no hay parada.
 *
 * El mismo gesto es el "no" de Yusuke: la Senal del Arquitecto se corta si dices que no a tiempo.
 */
public final class Guard {
    private Guard() {}

    public static final int PERFECT = 5, PERFECT_UPGRADE = 7, BLOCK = 30, COUNTER = 40;

    private static final Map<UUID, ArrayDeque<Long>> PRESSES = new HashMap<>();
    private static final Map<UUID, Boolean> WAS_DOWN = new HashMap<>();
    private static final Map<UUID, Long> COUNTER_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> PARRY_READY = new HashMap<>();

    /** Cada tick por jugador: detecta cuando se pulsa Shift (flanco de subida). */
    public static void tick(ServerPlayer p) {
        boolean down = p.isShiftKeyDown();
        if (down && !WAS_DOWN.getOrDefault(p.getUUID(), false)) press(p, p.level().getGameTime());
        WAS_DOWN.put(p.getUUID(), down);
    }

    /** Registra una pulsacion de Shift (publico para las pruebas). */
    public static void press(Player p, long now) {
        ArrayDeque<Long> q = PRESSES.computeIfAbsent(p.getUUID(), u -> new ArrayDeque<>());
        q.addLast(now);
        while (q.size() > 4) q.removeFirst();
    }

    public static long lastPress(Player p) {
        ArrayDeque<Long> q = PRESSES.get(p.getUUID());
        return q == null || q.isEmpty() ? Long.MIN_VALUE / 2 : q.peekLast();
    }

    /** Pulso firme: no mas de dos pulsaciones en el ultimo segundo. */
    private static boolean steady(Player p, long now) {
        ArrayDeque<Long> q = PRESSES.get(p.getUUID());
        if (q == null) return true;
        int n = 0;
        for (long t : q) if (now - t <= 20) n++;
        return n <= 2;
    }

    public static int window(Player p) {
        return UpgradeForm.isActive(p) ? PERFECT_UPGRADE : PERFECT;
    }

    /** El "no": Shift pulsado entre {@code from} y {@code to} (ticks de juego). */
    public static boolean saidNo(Player p, long from, long to) {
        ArrayDeque<Long> q = PRESSES.get(p.getUUID());
        if (q == null) return false;
        for (long t : q) if (t >= from && t <= to) return true;
        return false;
    }

    public static boolean counterReady(Player p) {
        return COUNTER_UNTIL.getOrDefault(p.getUUID(), 0L) >= p.level().getGameTime();
    }

    /**
     * Antes del dano a un jugador transformado. Devuelve true si el golpe se anulo (parada).
     */
    public static boolean onIncoming(ServerLevel level, ServerPlayer p, LivingIncomingDamageEvent event) {
        if (!Transformation.isTransformed(p) || p.isPassenger() || RiderKick.isKicking(p) || !p.isShiftKeyDown()) return false;
        DamageSource src = event.getSource();
        if (src.getEntity() == null && src.getDirectEntity() == null) return false;
        if (src.is(DamageTypeTags.BYPASSES_SHIELD) || src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        Vec3 from = src.getSourcePosition();
        if (from == null) return false;
        Vec3 to = from.subtract(p.position()).multiply(1, 0, 1);
        Vec3 look = p.getLookAngle().multiply(1, 0, 1);
        if (to.lengthSqr() > 0.01 && look.lengthSqr() > 0.01 && to.normalize().dot(look.normalize()) < 0.25) return false;   // por la espalda no

        long now = level.getGameTime();
        long since = now - lastPress(p);
        if (since <= window(p) && steady(p, now) && PARRY_READY.getOrDefault(p.getUUID(), 0L) <= now) {
            parry(level, p, src, now);
            event.setCanceled(true);
            return true;
        }
        if (since <= BLOCK) {
            event.setAmount(event.getAmount() * 0.5F);
            p.causeFoodExhaustion(0.8F);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 0.7F, 1.2F);
            FX.particle(level, ParticleTypes.CRIT, p.getEyePosition().add(p.getLookAngle().scale(0.6)), 4, 0.2, 0.05);
        }
        return false;
    }

    private static void parry(ServerLevel level, ServerPlayer p, DamageSource src, long now) {
        PARRY_READY.put(p.getUUID(), now + 8);
        COUNTER_UNTIL.put(p.getUUID(), now + COUNTER);
        if (src.getEntity() instanceof LivingEntity attacker && attacker != p) {
            boolean player = attacker instanceof Player;
            attacker.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, player ? 20 : 40, player ? 2 : 6));
            attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, player ? 20 : 40, player ? 0 : 3));
            if (src.getDirectEntity() == attacker) FX.push(attacker, p.position(), 0.6, 0.2);
            if (attacker instanceof com.draxer.dexrider.entity.ArchitectEntity boss) boss.parried();
        }
        Vec3 at = p.getEyePosition().add(p.getLookAngle().scale(0.8));
        FX.particle(level, ParticleTypes.CRIT, at, 14, 0.25, 0.25);
        FX.dust(level, FX.WHITE, 1.4F, at, 10, 0.3);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 1.0F, 1.6F);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.6F, 1.8F);
        p.sendSystemMessage(Component.translatable("message.dexrider.parry"), true);
        DexAdvancements.award(p, "parry");
    }

    /** Multiplicador del golpe de un jugador: el contraataque se gasta en el primer golpe. */
    public static float counterScale(ServerPlayer attacker, LivingEntity victim) {
        Long until = COUNTER_UNTIL.get(attacker.getUUID());
        if (until == null || until < attacker.level().getGameTime()) return 1.0F;
        COUNTER_UNTIL.remove(attacker.getUUID());
        if (attacker.level() instanceof ServerLevel level) {
            FX.particle(level, ParticleTypes.ENCHANTED_HIT, FX.center(victim), 18, 0.4, 0.3);
            FX.dust(level, FX.GOLD, 1.6F, FX.center(victim), 12, 0.4);
            level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2F, 0.8F);
        }
        attacker.sendSystemMessage(Component.translatable("message.dexrider.counter"), true);
        return 1.6F;
    }

    public static void forget(UUID id) {
        PRESSES.remove(id); WAS_DOWN.remove(id); COUNTER_UNTIL.remove(id); PARRY_READY.remove(id);
    }
}
