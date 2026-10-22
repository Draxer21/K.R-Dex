package com.draxer.dexrider.rider;

import com.draxer.dexrider.registry.DexSounds;
import com.draxer.dexrider.item.DexCard;
import com.draxer.dexrider.item.DexWeaponItem;
import com.draxer.dexrider.registry.DexItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Armed Ride: la carta se "inserta" y el arma aparece en la mano donde estaba la carta.
 * La carta vuelve al inventario (las cartas son permanentes). Solo un arma activa a la vez.
 */
public final class ArmedRide {
    private ArmedRide() {}

    public static void summon(ServerLevel level, ServerPlayer player, InteractionHand hand, DexCard card) {
        Item weaponItem = DexItems.weaponFor(card);
        if (weaponItem == null) return;

        removeWeapons(player);   // la anterior se desmaterializa

        ItemStack cardStack = player.getItemInHand(hand).copy();
        player.setItemInHand(hand, new ItemStack(weaponItem));
        if (!player.getInventory().add(cardStack)) player.drop(cardStack, false);

        Vec3 hold = player.getEyePosition().add(player.getLookAngle().scale(0.8)).add(0, -0.4, 0);
        FX.card(level, hold, player.getLookAngle(), FX.GOLD);
        FX.dust(level, FX.MAGENTA, 1.2F, hold, 20, 0.3);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                DexSounds.ARMED_RIDE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        DexSounds.play(level, player, DexSounds.VOICE_ARMED_RIDE, 1.0F, 1.0F);
        player.sendSystemMessage(Component.translatable("message.dexrider.armed_ride",
                Component.translatable("weapon.dexrider." + card.id())), true);
    }

    /**
     * Final Attack Ride: si hay un arma activa, se usa el finisher del arma; si no, la Rider Kick.
     * El arma pasa a la mano donde estaba la carta.
     */
    public static void finalAttack(ServerLevel level, ServerPlayer player, InteractionHand hand) {
        if (RiderKick.isKicking(player) || Scheduler.busy(player)) return;

        ItemStack weapon = activeWeapon(player);
        if (weapon.isEmpty()) {
            RiderKick.start(level, player);
            return;
        }
        // Pone el arma en la mano de la carta, y la carta donde estaba el arma.
        if (!(player.getItemInHand(hand).getItem() instanceof DexWeaponItem)) {
            int slot = slotOf(player, weapon);
            ItemStack card = player.getItemInHand(hand).copy();
            if (slot >= 0) {
                player.getInventory().setItem(slot, card);
            } else if (player.getItemBySlot(EquipmentSlot.OFFHAND) == weapon) {
                player.setItemSlot(EquipmentSlot.OFFHAND, card);
            }
            player.setItemInHand(hand, weapon);
        }
        WeaponFinishers.run(level, player, (DexWeaponItem) weapon.getItem(), weapon);
    }

    /** El arma de Dex que el jugador lleva encima (o EMPTY). */
    public static ItemStack activeWeapon(ServerPlayer player) {
        ItemStack off = player.getItemBySlot(EquipmentSlot.OFFHAND);
        if (DexItems.isDexWeapon(off.getItem())) return off;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (DexItems.isDexWeapon(s.getItem())) return s;
        }
        return ItemStack.EMPTY;
    }

    private static int slotOf(ServerPlayer player, ItemStack stack) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i) == stack) return i;
        }
        return -1;
    }

    /** Desmaterializa todas las armas de Dex (inventario, segunda mano y cursor). */
    public static void removeWeapons(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (DexItems.isDexWeapon(player.getInventory().getItem(i).getItem())) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        if (DexItems.isDexWeapon(player.getItemBySlot(EquipmentSlot.OFFHAND).getItem())) {
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }
        if (DexItems.isDexWeapon(player.containerMenu.getCarried().getItem())) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
    }

    public static boolean hasWeapon(ServerPlayer player) {
        return !activeWeapon(player).isEmpty()
                || DexItems.isDexWeapon(player.containerMenu.getCarried().getItem());
    }
}
