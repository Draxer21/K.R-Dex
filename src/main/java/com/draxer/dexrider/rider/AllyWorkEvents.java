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

    /** Shift + clic derecho con el pico en un bloque: esquinas de la zona del amigo elegido. */
    @SubscribeEvent
    public static void onRightClickBlock(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !p.isShiftKeyDown()
                || !event.getItemStack().is(net.minecraft.tags.ItemTags.PICKAXES)) return;
        if (AllyWork.markCorner(p, event.getPos())) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer p) AllyWork.showZones(p);
    }

    /** Un amigo derriba a un enemigo: pelear juntos une. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onKill(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getSource().getEntity() instanceof com.draxer.dexrider.entity.RiderAllyEntity ally)) return;
        if (!(event.getEntity() instanceof net.minecraft.world.entity.monster.Enemy)) return;
        ServerPlayer o = ally.ownerPlayer();
        if (o != null && ally.level() instanceof net.minecraft.server.level.ServerLevel level) AllyTrust.onFight(level, o, ally);
    }

    @SubscribeEvent
    public static void onCraft(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        AllyWork.onCraft(p, AllyWork.patternOf(event.getInventory()));
    }
}
