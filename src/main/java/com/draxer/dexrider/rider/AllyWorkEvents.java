package com.draxer.dexrider.rider;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/** Los amigos Rider miran lo que haces (bus de NeoForge; se registra en DexRider). */
public final class AllyWorkEvents {
    private AllyWorkEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BreakBlockEvent event) {
        if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer p)) return;
        AllyWork.onBreak(p, event.getPos(), event.getState());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer p)) return;
        AllyWork.onPlace(p, event.getPos(), event.getPlacedBlock());
    }

    @SubscribeEvent
    public static void onCraft(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        AllyWork.onCraft(p, AllyWork.patternOf(event.getInventory()));
    }
}
