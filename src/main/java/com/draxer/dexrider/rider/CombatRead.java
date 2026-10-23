package com.draxer.dexrider.rider;

import com.draxer.dexrider.network.TargetSyncPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lectura del Driver (transformado): tras intercambiar golpes con algo, el Driver te muestra su vida, cuanto le haces
 * por golpe, cuanto te hace el por golpe y un estimado de cuantos golpes faltan para tumbarlo (y cuantos aguantas tu).
 *
 * El dano se mide de verdad: vida + absorcion antes y despues de cada golpe (con armadura, encantamientos, Form Ride,
 * Emotion, adaptacion del Arquitecto... todo incluido). La estimacion usa la media de tus ultimos tres golpes.
 */
public final class CombatRead {
    private CombatRead() {}

    public static final int TIMEOUT = 200;   // 10 s sin intercambiar golpes: el panel se apaga

    public static final class Track {
        public int targetId = -1;
        public UUID target;
        public float lastDealt = -1;
        public final ArrayDeque<Float> recent = new ArrayDeque<>();
        public final Map<UUID, Float> takenFrom = new HashMap<>();
        public long lastTime;

        public float avgDealt() {
            if (recent.isEmpty()) return -1;
            float s = 0;
            for (float f : recent) s += f;
            return s / recent.size();
        }

        public float lastTaken() { return target == null ? -1 : takenFrom.getOrDefault(target, -1F); }
    }

    private static final Map<UUID, Track> TRACKS = new HashMap<>();
    private static final Map<Integer, Float> BEFORE = new HashMap<>();
    private static final java.util.Set<UUID> SHOWN = new java.util.HashSet<>();

    @Nullable
    public static Track track(ServerPlayer p) { return TRACKS.get(p.getUUID()); }

    private static float life(LivingEntity e) { return e.getHealth() + e.getAbsorptionAmount(); }

    /** Antes de aplicar el dano (LivingDamageEvent.Pre). */
    public static void before(LivingEntity victim) {
        if (BEFORE.size() > 512) BEFORE.clear();
        BEFORE.put(victim.getId(), life(victim));
    }

    /** Despues de aplicar el dano (LivingDamageEvent.Post). */
    public static void after(LivingEntity victim, @Nullable Entity attacker) {
        Float b = BEFORE.remove(victim.getId());
        if (b == null || attacker == null || attacker == victim) return;
        float dealt = b - Math.max(0, life(victim));
        if (dealt <= 0.01F) return;
        long now = victim.level().getGameTime();
        if (attacker instanceof ServerPlayer p && Transformation.isTransformed(p)) recordDealt(p, victim, dealt, now);
        if (victim instanceof ServerPlayer p && Transformation.isTransformed(p) && attacker instanceof LivingEntity src)
            recordTaken(p, src, dealt, now);
    }

    public static void recordDealt(ServerPlayer p, LivingEntity target, float dealt, long now) {
        Track t = TRACKS.computeIfAbsent(p.getUUID(), u -> new Track());
        if (!target.getUUID().equals(t.target)) {
            t.target = target.getUUID();
            t.targetId = target.getId();
            t.recent.clear();
        }
        t.lastDealt = dealt;
        t.recent.addLast(dealt);
        while (t.recent.size() > 3) t.recent.removeFirst();
        t.lastTime = now;
    }

    public static void recordTaken(ServerPlayer p, LivingEntity attacker, float taken, long now) {
        Track t = TRACKS.computeIfAbsent(p.getUUID(), u -> new Track());
        t.takenFrom.put(attacker.getUUID(), taken);
        // si no estabas leyendo a nadie (o hace rato), lees al que te pego
        if (t.target == null || now - t.lastTime > 60) {
            if (!attacker.getUUID().equals(t.target)) { t.recent.clear(); t.lastDealt = -1; }
            t.target = attacker.getUUID();
            t.targetId = attacker.getId();
        }
        if (attacker.getUUID().equals(t.target)) t.lastTime = now;
    }

    /** Golpes que faltan con la media actual (0 si no hay datos). */
    public static int hitsLeft(float hp, float avg) {
        return avg <= 0 ? 0 : (int) Math.ceil(hp / avg);
    }

    /** Cada 5 ticks por jugador transformado: manda la lectura al cliente. */
    public static void sync(ServerLevel level, ServerPlayer p) {
        Track t = TRACKS.get(p.getUUID());
        LivingEntity target = t == null ? null : (level.getEntity(t.targetId) instanceof LivingEntity le ? le : null);
        long now = level.getGameTime();
        if (t == null || target == null || !target.isAlive() || now - t.lastTime > TIMEOUT || target.distanceToSqr(p) > 48 * 48) {
            if (t != null && (target == null || !target.isAlive())) { t.target = null; t.targetId = -1; t.recent.clear(); t.lastDealt = -1; }
            if (SHOWN.remove(p.getUUID())) TargetSyncPayload.send(p, TargetSyncPayload.EMPTY);
            return;
        }
        float hp = life(target);
        float avg = t.avgDealt();
        float taken = t.lastTaken();
        SHOWN.add(p.getUUID());
        TargetSyncPayload.send(p, new TargetSyncPayload(true, target.getId(), hp, target.getMaxHealth(), t.lastDealt, hitsLeft(hp, avg),
                taken, taken > 0 ? hitsLeft(life(p), taken) : 0));
    }

    public static void clear(ServerPlayer p) {
        TRACKS.remove(p.getUUID());
        if (SHOWN.remove(p.getUUID())) TargetSyncPayload.send(p, TargetSyncPayload.EMPTY);
    }

    public static void forget(UUID id) { TRACKS.remove(id); SHOWN.remove(id); }
}
