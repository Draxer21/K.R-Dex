package com.draxer.dexrider.rider;

import com.draxer.dexrider.config.DexConfig;
import com.draxer.dexrider.entity.RiderAllyEntity;
import com.draxer.dexrider.registry.DexComponents;
import com.draxer.dexrider.registry.DexSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Las Emotion de Dex (del fanfic): el Driver responde a lo que siente el portador. Solo como Dex.
 *
 *  VIOLENT : se despierta al borde (menos del 30 % de vida) o al recibir de golpe mucho dano (40 % de la vida en 3 s).
 *            20 s: Fuerza III, Velocidad II, golpes x1.3. El traje se oscurece a carmesi y morado y un cristal
 *            violeta brilla en el pecho. Cobra su precio: drena medio corazon cada 2 s (nunca por debajo de 2 corazones).
 *  HEROIC  : se despierta cuando alguien a quien Dex protege (sus amigos Rider o un aldeano cercano) recibe un golpe.
 *            20 s: Regeneracion II, Resistencia II, recibe un 20 % menos y pega x1.15. El traje pasa a azul y blanco
 *            con un cristal blanco.
 *  En las dos, las cartas del Ride Booker responden solas: elegir una (Z / X) ya la activa, sin insertarla.
 *  Sin recarga: en cuanto se apaga, puede volver a despertar. Se pueden desactivar en la configuracion.
 */
public final class DexEmotions {
    private DexEmotions() {}

    public static final String VIOLENT = "violent", HEROIC = "heroic";
    public static final int DURATION = 400, COOLDOWN = 1800;

    private static final Map<UUID, Long> UNTIL = new HashMap<>();
    private static final Map<UUID, Long> READY_AT = new HashMap<>();
    private static final Map<UUID, Deque<float[]>> RECENT = new HashMap<>();   // {tiempo, dano}

    /** {principal, ojos/cristal, acentos} */
    private static final int[] VIOLENT_COLORS = {0x5A1430, 0xB44CFF, 0x7A2AA0};
    private static final int[] HEROIC_COLORS = {0x2A5ED8, 0xEAF4FF, 0xF0F0F8};

    public static String current(LivingEntity e) {
        return e.getItemBySlot(EquipmentSlot.CHEST).get(DexComponents.EMOTION.get());
    }

    public static int[] colors(String emotion) {
        if (VIOLENT.equals(emotion)) return VIOLENT_COLORS;
        if (HEROIC.equals(emotion)) return HEROIC_COLORS;
        return null;
    }

    public static long remaining(ServerPlayer p, long now) {
        Long u = UNTIL.get(p.getUUID());
        return u == null ? 0 : Math.max(0, u - now);
    }

    public static long cooldownLeft(ServerPlayer p, long now) {
        Long r = READY_AT.get(p.getUUID());
        return r == null ? 0 : Math.max(0, r - now);
    }

    private static boolean canAwaken(ServerLevel level, ServerPlayer p) {
        return DexConfig.emotions() && Transformation.isTransformed(p) && KamenRide.current(p) == KamenRide.DEX
                && current(p) == null && !RiderKick.isKicking(p);
    }

    // ------------------------------------------------------------------ disparadores

    /** Dano que esta por recibir Dex (ya reducido por el traje). */
    public static void onDexHurt(ServerLevel level, ServerPlayer p, float amount) {
        if (amount <= 0 || !canAwaken(level, p)) return;
        long now = level.getGameTime();
        Deque<float[]> q = RECENT.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
        q.addLast(new float[]{now, amount});
        float sum = 0;
        while (!q.isEmpty() && now - q.peekFirst()[0] > 60) q.removeFirst();
        for (float[] e : q) sum += e[1];
        if (p.getHealth() - amount < p.getMaxHealth() * 0.3F || sum >= p.getMaxHealth() * 0.4F) {
            q.clear();
            start(level, p, VIOLENT);
        }
    }

    /** Alguien recibe un golpe: si Dex lo esta protegiendo, despierta la Heroic Emotion. */
    public static void onProtectedHurt(ServerLevel level, LivingEntity victim, DamageSource source) {
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker instanceof Player) return;
        if (victim instanceof RiderAllyEntity ally) {
            ServerPlayer owner = ally.ownerPlayer();
            if (owner != null && owner.distanceToSqr(ally) < 24 * 24 && canAwaken(level, owner)) start(level, owner, HEROIC);
            return;
        }
        if (!(victim instanceof AbstractVillager)) return;
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, victim.getBoundingBox().inflate(14))) {
            if (canAwaken(level, p)) { start(level, p, HEROIC); return; }
        }
    }

    // ------------------------------------------------------------------ ciclo

    private static void start(ServerLevel level, ServerPlayer p, String emotion) {
        p.getItemBySlot(EquipmentSlot.CHEST).set(DexComponents.EMOTION.get(), emotion);
        UNTIL.put(p.getUUID(), level.getGameTime() + DURATION);
        int[] c = colors(emotion);
        FX.ring(level, p, c[1], 1.8, 10);
        FX.ring(level, p, c[0], 1.2, 8);
        if (VIOLENT.equals(emotion)) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 0.6F, 1.4F);
            FX.particle(level, ParticleTypes.SOUL_FIRE_FLAME, FX.center(p), 30, 0.6, 0.1);
        } else {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2F, 1.3F);
            FX.particle(level, ParticleTypes.END_ROD, FX.center(p), 30, 0.6, 0.08);
        }
        DexSounds.play(level, p, DexSounds.HENSHIN, 1.0F, VIOLENT.equals(emotion) ? 0.7F : 1.3F);
        p.sendSystemMessage(Component.translatable("message.dexrider.emotion_" + emotion), true);
        effects(p, emotion);
        DexAdvancements.award(p, emotion + "_emotion");
    }

    public static void tick(ServerLevel level, ServerPlayer p) {
        String emotion = current(p);
        if (emotion == null) return;
        long left = remaining(p, level.getGameTime());
        if (left <= 0 || KamenRide.current(p) != KamenRide.DEX) { end(level, p); return; }
        if (p.tickCount % 20 == 0) effects(p, emotion);
        // el cristal del pecho
        if (p.tickCount % 4 == 0) {
            int[] c = colors(emotion);
            Vec3 chest = p.position().add(0, p.getBbHeight() * 0.72, 0).add(p.getLookAngle().multiply(0.35, 0, 0.35));
            FX.dust(level, c[1], 0.9F, chest, 2, 0.08);
            FX.dust(level, c[0], 1.2F, FX.center(p), 2, 0.45);
        }
        // Violent cobra su precio
        if (VIOLENT.equals(emotion) && p.tickCount % 40 == 0 && p.getHealth() > 4.0F) {
            p.setHealth(p.getHealth() - 1.0F);
        }
    }

    private static void effects(ServerPlayer p, String emotion) {
        if (VIOLENT.equals(emotion)) {
            p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 40, 2, false, false, true));
            p.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 1, false, false, true));
        } else {
            p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 1, false, false, true));
            p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 40, 1, false, false, true));
        }
    }

    private static void end(ServerLevel level, ServerPlayer p) {
        String emotion = current(p);
        clear(p);
        if (emotion == null) return;
        p.removeEffect(VIOLENT.equals(emotion) ? MobEffects.SPEED : MobEffects.REGENERATION);
        Transformation.applyFormEffects(p);
        FX.ring(level, p, FX.MAGENTA, 0.9, 5);
        p.sendSystemMessage(Component.translatable("message.dexrider.emotion_end"), true);
    }

    /** Al destransformarse o cambiar de identidad. */
    public static void clear(ServerPlayer p) {
        UNTIL.remove(p.getUUID());
        RECENT.remove(p.getUUID());
        p.getItemBySlot(EquipmentSlot.CHEST).remove(DexComponents.EMOTION.get());
    }

    public static float damageScale(Player p) {
        String e = current(p);
        return VIOLENT.equals(e) ? 1.3F : HEROIC.equals(e) ? 1.15F : 1.0F;
    }

    public static float takenScale(Player p) {
        return HEROIC.equals(current(p)) ? 0.8F : 1.0F;
    }
}
