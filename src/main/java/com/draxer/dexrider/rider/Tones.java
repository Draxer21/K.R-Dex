package com.draxer.dexrider.rider;

import com.draxer.dexrider.entity.RiderMob;
import com.draxer.dexrider.item.DexCard;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Los tonos de las cartas (cap. 80 del fanfic): cada carta suena distinto al activarse. Genzo (Orga) lleva 50 anos
 * leyendolos, y se lo enseno a Tetsuya (Kaixa).
 *
 *  - LEER: si usas la MISMA carta dos veces en 10 s cerca de Orga o Kaixa (de los que pelean contra ti), la reconocen
 *    y esquivan tu siguiente golpe (3 s).
 *  - MENTIR: Shift + usar la carta del Ride Booker (Alt izquierdo) la corta a medio sonar: es un senuelo. Quien lo lea
 *    se prepara para algo que no llega y queda expuesto: tu siguiente golpe en 3 s pega un 50 % mas.
 *  - SOLAPAR: dos cartas distintas en menos de 1 s mezclan los tonos: durante 5 s nadie te puede leer.
 */
public final class Tones {
    private Tones() {}

    private record Tone(DexCard card, long time) {}

    private static final Map<UUID, Tone> LAST = new HashMap<>();
    private static final Map<UUID, Long> SCRAMBLED = new HashMap<>();   // jugador -> hasta cuando no se le puede leer
    private static final Map<UUID, Long> DODGE = new HashMap<>();       // lector -> esquiva preparada hasta
    private static final Map<UUID, Long> FOOLED = new HashMap<>();      // lector -> expuesto hasta

    /** 12 tonos (una escala cromatica): cada carta tiene el suyo. */
    public static float pitch(DexCard card) {
        int n = Math.floorMod(card.id().hashCode(), 12);
        return (float) Math.pow(2.0, (n - 6) / 12.0);
    }

    private static List<RiderMob> readers(ServerLevel level, ServerPlayer p) {
        return level.getEntitiesOfClass(RiderMob.class, p.getBoundingBox().inflate(20),
                m -> m.isAlive() && (m.rider() == KamenRide.ORGA || m.rider() == KamenRide.KAIXA) && !m.isFriendlyTo(p));
    }

    /** Llamado al activar cualquier carta. */
    public static void onCard(ServerLevel level, ServerPlayer p, DexCard card) {
        long now = level.getGameTime();
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.7F, pitch(card));
        Tone last = LAST.put(p.getUUID(), new Tone(card, now));
        if (last != null && last.card() != card && now - last.time() <= 20) {
            SCRAMBLED.put(p.getUUID(), now + 100);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.6F, pitch(last.card()) * 1.06F);
            p.sendSystemMessage(Component.translatable("message.dexrider.tones_overlap"), true);
            return;
        }
        if (SCRAMBLED.getOrDefault(p.getUUID(), 0L) > now) return;
        if (last != null && last.card() == card && now - last.time() <= 200) {
            for (RiderMob r : readers(level, p)) {
                DODGE.put(r.getUUID(), now + 60);
                FOOLED.remove(r.getUUID());
                FX.particle(level, ParticleTypes.NOTE, r.position().add(0, r.getBbHeight() + 0.3, 0), 3, 0.3, 1.0);
                p.sendSystemMessage(Component.translatable("message.dexrider.tones_read", r.rider().displayName()), true);
                RiderDialogue.say(level, r, r.rider(), RiderDialogue.Line.HURT, 200);
            }
        }
    }

    /** Senuelo: la carta se corta a medio sonar. */
    public static void decoy(ServerLevel level, ServerPlayer p, DexCard card) {
        long now = level.getGameTime();
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.5F, pitch(card));
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.PLAYERS, 0.4F, 0.6F);
        FX.card(level, p.getEyePosition().add(p.getLookAngle().scale(1.2)), p.getLookAngle(), FX.MAGENTA);
        int fooled = 0;
        for (RiderMob r : readers(level, p)) {
            FOOLED.put(r.getUUID(), now + 60);
            DODGE.remove(r.getUUID());
            FX.particle(level, ParticleTypes.ANGRY_VILLAGER, r.position().add(0, r.getBbHeight() + 0.3, 0), 2, 0.3, 0);
            fooled++;
        }
        p.sendSystemMessage(Component.translatable(fooled > 0 ? "message.dexrider.tones_decoy_hit" : "message.dexrider.tones_decoy"), true);
    }

    /** Antes de un golpe del jugador: el lector que leyo el tono lo esquiva. */
    public static boolean dodges(LivingEntity victim, DamageSource source) {
        if (!(victim instanceof RiderMob) || !(source.getEntity() instanceof ServerPlayer)) return false;
        Long until = DODGE.get(victim.getUUID());
        if (until == null || until < victim.level().getGameTime()) return false;
        DODGE.remove(victim.getUUID());
        if (victim.level() instanceof ServerLevel level) {
            FX.particle(level, ParticleTypes.CLOUD, FX.center(victim), 8, 0.3, 0.05);
            level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.HOSTILE, 1.0F, 1.4F);
        }
        return true;
    }

    /** Multiplicador sobre un lector enganado por un senuelo. */
    public static float exposed(LivingEntity victim) {
        Long until = FOOLED.get(victim.getUUID());
        if (until == null || until < victim.level().getGameTime()) return 1.0F;
        FOOLED.remove(victim.getUUID());
        return 1.5F;
    }
}
