package com.draxer.dexrider.rider;

import com.draxer.dexrider.DexRider;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Logros del mod (data/dexrider/advancement). Los que dependen de acciones del mod usan un criterio
 * "minecraft:impossible" llamado "done" y se otorgan desde el codigo.
 */
public final class DexAdvancements {
    private DexAdvancements() {}

    public static void award(ServerPlayer player, String name) {
        try {
            MinecraftServer server = player.level().getServer();
            if (server == null) return;
            AdvancementHolder holder = server.getAdvancements().get(DexRider.id(name));
            if (holder != null) player.getAdvancements().award(holder, "done");
        } catch (RuntimeException e) {
            DexRider.LOGGER.debug("No se pudo otorgar el logro {}", name, e);
        }
    }
}
