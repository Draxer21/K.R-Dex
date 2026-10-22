package com.draxer.dexrider.rider;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Acciones con retraso ligadas a un jugador (golpes multiples de los finishers).
 * Se cancelan si el jugador muere, se desconecta o se destransforma.
 */
public final class Scheduler {
    private Scheduler() {}

    private static final class Task {
        int ticksLeft;
        final Runnable action;
        final boolean soft;

        Task(int ticks, Runnable action, boolean soft) {
            this.ticksLeft = ticks;
            this.action = action;
            this.soft = soft;
        }
    }

    private static final Map<UUID, List<Task>> TASKS = new HashMap<>();

    public static void later(ServerPlayer player, int ticks, Runnable action) {
        TASKS.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(new Task(ticks, action, false));
    }

    /** Como later, pero no bloquea la transformacion ni las patadas (efectos largos que no son un ataque en curso). */
    public static void soft(ServerPlayer player, int ticks, Runnable action) {
        TASKS.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(new Task(ticks, action, true));
    }

    public static boolean busy(ServerPlayer player) {
        List<Task> list = TASKS.get(player.getUUID());
        if (list == null) return false;
        for (Task t : list) if (!t.soft) return true;
        return false;
    }

    public static void cancel(ServerPlayer player) {
        TASKS.remove(player.getUUID());
    }

    public static void tick(ServerPlayer player) {
        List<Task> list = TASKS.get(player.getUUID());
        if (list == null) return;
        if (!player.isAlive() || !Transformation.isTransformed(player)) {
            TASKS.remove(player.getUUID());
            return;
        }
        List<Task> due = new ArrayList<>();
        for (Iterator<Task> it = list.iterator(); it.hasNext(); ) {
            Task t = it.next();
            if (--t.ticksLeft <= 0) {
                due.add(t);
                it.remove();
            }
        }
        if (list.isEmpty()) TASKS.remove(player.getUUID());
        for (Task t : due) t.action.run();
    }
}
