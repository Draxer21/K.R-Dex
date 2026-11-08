package com.draxer.dexrider.rider;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Aguante del Rider (0.56): lo gastan la esquiva y los movimientos especiales del moveset (pesado, en carrera,
 * aereo). Se recupera solo, mas rapido quieto; con la Upgrade Form (sin miedo) el maximo sube.
 */
public final class Stamina {
    private Stamina() {}

    public static final int MAX = 100, MAX_UPGRADE = 130;

    private static final Map<UUID, float[]> VALUES = new HashMap<>();   // [aguante, pausa hasta (tick)]

    public static int max(ServerPlayer p) { return UpgradeForm.isActive(p) ? MAX_UPGRADE : MAX; }

    private static float[] of(ServerPlayer p) { return VALUES.computeIfAbsent(p.getUUID(), u -> new float[]{MAX, 0}); }

    public static int get(ServerPlayer p) { return (int) Math.min(of(p)[0], max(p)); }

    public static boolean has(ServerPlayer p, int amount) {
        return p.isCreative() || of(p)[0] >= amount;
    }

    public static boolean spend(ServerPlayer p, int amount) {
        if (p.isCreative()) return true;
        float[] v = of(p);
        if (v[0] < amount) return false;
        v[0] -= amount;
        pause(p, 20);
        return true;
    }

    public static void give(ServerPlayer p, int amount) {
        float[] v = of(p);
        v[0] = Math.min(max(p), v[0] + amount);
    }

    /** No recupera durante unos ticks (despues de gastar o de golpear). */
    public static void pause(ServerPlayer p, int ticks) {
        float[] v = of(p);
        v[1] = Math.max(v[1], p.level().getGameTime() + ticks);
    }

    public static void tick(ServerPlayer p) {
        float[] v = of(p);
        int max = max(p);
        if (v[0] >= max) { v[0] = max; return; }
        if (p.level().getGameTime() < v[1]) return;
        boolean still = p.getDeltaMovement().horizontalDistanceSqr() < 0.003;
        v[0] = Math.min(max, v[0] + (still ? 2.0F : p.isSprinting() ? 0.6F : 1.2F));
    }

    public static void forget(UUID id) { VALUES.remove(id); }
}
