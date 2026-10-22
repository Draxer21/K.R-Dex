package com.draxer.dexrider.rider;

import com.draxer.dexrider.registry.DexSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Final Attack Ride: Dex (Rider Kick). Se usa cuando no hay un arma activa.
 *
 *  - RISE: Dex salta.
 *  - Se fija un objetivo y aparecen cartas holograficas en linea hacia el.
 *  - DIVE: patada en picada que PERSIGUE al objetivo. Cada carta atravesada suma poder.
 *  - IMPACT: dano fuerte al objetivo y explosion en area (sin romper bloques).
 */
public final class RiderKick {
    private RiderKick() {}

    private enum Phase { RISE, DIVE }

    private static final int RISE_TICKS = 12;
    private static final int MAX_DIVE_TICKS = 60;
    private static final double TARGET_RANGE = 24.0;
    private static final int CARD_COUNT = 5;

    private static final float BASE_DAMAGE = 14.0F;
    private static final float DAMAGE_PER_CARD = 3.0F;   // 5 cartas -> 14 + 15 = 29 (antes del escalado)
    private static final double AOE_RADIUS = 4.0;

    private static final class KickState {
        Phase phase = Phase.RISE;
        int ticks = 0;
        LivingEntity target;
        Vec3 fallbackDir;
        final List<Vec3> cards = new ArrayList<>();
        int cardsPassed = 0;
    }

    private static final Map<UUID, KickState> ACTIVE = new HashMap<>();

    public static boolean isKicking(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    public static void cancel(ServerPlayer player) {
        ACTIVE.remove(player.getUUID());
    }

    public static void start(ServerLevel level, ServerPlayer player) {
        KickState state = new KickState();
        Vec3 look = player.getLookAngle();
        state.fallbackDir = new Vec3(look.x, Math.min(look.y, -0.6), look.z).normalize();
        ACTIVE.put(player.getUUID(), state);

        player.setDeltaMovement(look.x * 0.25, 1.35, look.z * 0.25);
        player.hurtMarked = true;   // sincroniza la velocidad con el cliente

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.5F, 0.7F);
        DexSounds.play(level, player, DexSounds.VOICE_FINAL_ATTACK_RIDE, 1.0F, 1.0F);
        player.sendSystemMessage(Component.translatable("message.dexrider.final_attack_ride"), true);
    }

    /** Se llama cada tick del jugador en el servidor. */
    public static void tick(ServerLevel level, ServerPlayer player) {
        KickState state = ACTIVE.get(player.getUUID());
        if (state == null) return;

        player.resetFallDistance();
        state.ticks++;

        if (!player.isAlive() || !Transformation.isTransformed(player)) {
            ACTIVE.remove(player.getUUID());
            return;
        }

        if (state.phase == Phase.RISE) {
            FX.ring(level, player, FX.GOLD, 0.9, 1);
            if (state.ticks >= RISE_TICKS) {
                state.target = FX.findTarget(level, player, TARGET_RANGE, 0.55);
                Vec3 end = state.target != null ? FX.center(state.target)
                        : player.position().add(state.fallbackDir.scale(10));
                Vec3 start = player.position().add(0, 1, 0);
                for (int i = 1; i <= CARD_COUNT; i++) {
                    state.cards.add(start.lerp(end, i / (double) (CARD_COUNT + 1)));
                }
                state.phase = Phase.DIVE;
                state.ticks = 0;
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        DexSounds.FAR_CHARGE.get(), SoundSource.PLAYERS, 1.5F, 1.0F);
            }
            return;
        }

        // ---- DIVE ----
        Vec3 dir;
        if (state.target != null && state.target.isAlive()) {
            dir = FX.center(state.target).subtract(player.position().add(0, 1, 0)).normalize();
        } else {
            state.target = null;
            dir = state.fallbackDir;
        }
        player.setDeltaMovement(dir.scale(1.7));
        player.hurtMarked = true;

        FX.dust(level, FX.MAGENTA, 2.0F, player.position().add(0, 0.3, 0), 6, 0.15);

        // Cartas holograficas: se dibujan y se "atraviesan".
        Vec3 body = player.position().add(0, 1, 0);
        state.cards.removeIf(card -> {
            if (card.distanceTo(body) < 1.8) {
                state.cardsPassed++;
                FX.particle(level, ParticleTypes.END_ROD, card, 12, 0.35, 0.05);
                level.playSound(null, card.x, card.y, card.z,
                        SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5F, 1.2F + state.cardsPassed * 0.1F);
                return true;
            }
            return false;
        });
        if (state.ticks % 2 == 0) {
            for (Vec3 card : state.cards) FX.card(level, card, dir, FX.MAGENTA);
        }

        boolean hitTarget = state.target != null
                && player.getBoundingBox().inflate(0.6).intersects(state.target.getBoundingBox());
        boolean landed = state.ticks > 3 && (player.onGround() || player.horizontalCollision);

        if (hitTarget || landed || state.ticks > MAX_DIVE_TICKS) {
            impact(level, player, state);
            ACTIVE.remove(player.getUUID());
        }
    }

    private static void impact(ServerLevel level, ServerPlayer player, KickState state) {
        float damage = BASE_DAMAGE + DAMAGE_PER_CARD * state.cardsPassed;
        Vec3 center = state.target != null ? state.target.position() : player.position();

        if (state.target != null) {
            FX.hit(level, player, state.target, damage);
            FX.push(state.target, player.position(), 2.0, 0.5);
        }

        AABB area = AABB.ofSize(center, AOE_RADIUS * 2, AOE_RADIUS * 2, AOE_RADIUS * 2);
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != player && e != state.target && e.isAlive())) {
            FX.hit(level, player, other, damage * 0.4F);
            FX.push(other, center, 1.0, 0.3);
        }

        // ---- EXPLOSION (solo visual y sonido: no rompe bloques ni dania a Dex) ----
        FX.particle(level, ParticleTypes.EXPLOSION_EMITTER, center.add(0, 0.5, 0), 3, 0.8, 0);
        FX.particle(level, ParticleTypes.EXPLOSION, center.add(0, 1, 0), 14, 1.6, 0);
        FX.particle(level, ParticleTypes.FLAME, center.add(0, 0.8, 0), 60, 1.4, 0.12);
        FX.particle(level, ParticleTypes.LARGE_SMOKE, center.add(0, 1.2, 0), 40, 1.6, 0.05);
        FX.particle(level, ParticleTypes.LAVA, center.add(0, 0.5, 0), 20, 1.0, 0);
        FX.dust(level, FX.MAGENTA, 2.5F, center.add(0, 1, 0), 80, 1.3);
        FX.dust(level, FX.GOLD, 2.0F, center.add(0, 1, 0), 40, 1.0);
        // onda expansiva: anillos a ras de suelo
        for (int r = 1; r <= 3; r++) {
            for (int i = 0; i < 32; i++) {
                double a = Math.PI * 2 * i / 32;
                Vec3 p = center.add(Math.cos(a) * r * 1.5, 0.2, Math.sin(a) * r * 1.5);
                FX.particle(level, ParticleTypes.CLOUD, p, 1, 0.05, 0.02);
            }
        }
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.0F, 0.75F);
        level.playSound(null, center.x, center.y, center.z,
                DexSounds.FAR_IMPACT.get(), SoundSource.PLAYERS, 2.0F, 1.0F);

        // Rebote hacia atras al conectar, como al final de una Rider Kick.
        Vec3 back = player.getLookAngle().scale(-0.6);
        player.setDeltaMovement(back.x, 0.6, back.z);
        player.hurtMarked = true;
    }
}
