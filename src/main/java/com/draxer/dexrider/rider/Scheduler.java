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

        Task(int ticks, Runnable action) {
            this.ticksLeft = ticks;
            this.action = action;
        }
    }

    private static final Map<UUID, List<Task>> TASKS = new HashMap<>();

    public static void later(ServerPlayer player, int ticks, Runnable action) {
        TASKS.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(new Task(ticks, action));
    }

    public static boolean busy(ServerPlayer player) {
        List<Task> list = TASKS.get(player.getUUID());
        return list != null && !list.isEmpty();
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
