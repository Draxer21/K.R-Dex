package com.draxer.dexrider.rider;

import com.draxer.dexrider.item.DexCard;
import com.draxer.dexrider.item.DexDriverItem;
import com.draxer.dexrider.item.DriverDeck;
import com.draxer.dexrider.registry.DexComponents;
import com.draxer.dexrider.registry.DexItems;
import com.draxer.dexrider.registry.DexSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * K-Touch y Complete Form, con las reglas del fanfic:
 *
 *  - Al reunir NUEVE Kamen Ride (Dex + ocho), el Driver forja el K-Touch y la carta de Complete Form.
 *    La carta sale incompleta: ocho blasones, el de Dex al centro y un hueco apagado.
 *  - Con la DECIMA Kamen Ride la carta se completa.
 *  - Sin uso parcial: para entrar hay que pulsar los diez pads, incluido el de Dex.
 *  - El Driver se desacopla a la cadera derecha y el K-Touch ocupa el centro del cinturon (se ve en la armadura).
 *  - Multiplicador: +0,2 por cada Kamen Ride (con diez, x3). Agota rapido (hambre).
 *
 * Pendiente para la siguiente tanda: invocar las proyecciones de cada Rider pulsando su blason.
 */
public final class KTouch {
    private KTouch() {}

    public static final int FORGE_AT = 9;      // Dex + 8
    public static final int COMPLETE_AT = 10;  // Dex + 9

    public static int kamenRideCount(DriverDeck deck) {
        return deck.count(DexCard.Family.KAMEN_RIDE);
    }

    /** Llamado cada vez que el mazo gana una carta Kamen Ride. */
    public static void onKamenRideAdded(ServerPlayer player, ItemStack driver, DriverDeck deck) {
        int count = kamenRideCount(deck);
        if (count >= FORGE_AT && !Boolean.TRUE.equals(driver.get(DexComponents.KTOUCH_FORGED.get()))) {
            driver.set(DexComponents.KTOUCH_FORGED.get(), true);
            give(player, new ItemStack(DexItems.K_TOUCH.get()));
            give(player, new ItemStack(DexItems.COMPLETE_FORM_CARD.get()));
            DexAdvancements.award(player, "ktouch");
            if (player.level() instanceof ServerLevel level) {
                DexSounds.play(level, player, DexSounds.SCAN_COMPLETE, 1.4F, 0.6F);
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2F, 0.8F);
                for (KamenRide k : KamenRide.RING) FX.ring(level, player, k.color, 1.4, 3);
            }
            player.sendSystemMessage(Component.translatable("message.dexrider.ktouch_forged").withStyle(ChatFormatting.GOLD));
            if (count < COMPLETE_AT) {
                player.sendSystemMessage(Component.translatable("message.dexrider.ktouch_incomplete",
                        COMPLETE_AT - count).withStyle(ChatFormatting.GRAY));
            }
        }
        if (count == COMPLETE_AT) {
            player.sendSystemMessage(Component.translatable("message.dexrider.ktouch_complete").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    /** Para los mundos donde las cartas ya estaban antes de esta version. */
    public static void retroCheck(ServerPlayer player) {
        ItemStack driver = DexDriverItem.findDriver(player);
        if (driver.isEmpty()) return;
        DriverDeck deck = DexDriverItem.getDeck(driver);
        if (kamenRideCount(deck) >= FORGE_AT && !Boolean.TRUE.equals(driver.get(DexComponents.KTOUCH_FORGED.get()))) {
            onKamenRideAdded(player, driver, deck);
        }
    }

    public static boolean has(Player player, Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(item)) return true;
        }
        return false;
    }

    /** Pulsados los diez pads en la pantalla del K-Touch. */
    public static void enter(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!Transformation.isTransformed(player) || KamenRide.current(player) != KamenRide.DEX) {
            player.sendSystemMessage(Component.translatable("message.dexrider.ktouch_need_dex"), true);
            return;
        }
        if (!has(player, DexItems.K_TOUCH.get()) || !has(player, DexItems.COMPLETE_FORM_CARD.get())) return;
        int count = kamenRideCount(DexDriverItem.getDeck(DexDriverItem.findDriver(player)));
        if (count < COMPLETE_AT) {
            player.sendSystemMessage(Component.translatable("message.dexrider.ktouch_incomplete", COMPLETE_AT - count), true);
            return;
        }
        KamenRide.set(level, player, KamenRide.COMPLETE);
        DexAdvancements.award(player, "complete_form");
        // espectaculo: las nueve cartas giran alrededor y se clavan en la armadura
        Vec3 c = FX.center(player);
        for (int i = 0; i < KamenRide.RING.length; i++) {
            KamenRide k = KamenRide.RING[i];
            double a = Math.toRadians(i * 40);
            Vec3 from = c.add(Math.cos(a) * 3, 1.2, Math.sin(a) * 3);
            Scheduler.later(player, 1 + i * 2, () -> {
                FX.card(level, from, c.subtract(from).normalize(), k.color);
                FX.line(level, from, c, k.color, 1.0F, 0.4);
            });
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5F, 0.6F);
        player.sendSystemMessage(Component.translatable("message.dexrider.complete_form",
                String.format("%.1f", RiderPowers.completeMultiplier(player))).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /** Salir de la Complete Form (K-Touch otra vez, o agotamiento). */
    public static void exit(ServerLevel level, ServerPlayer player, boolean exhausted) {
        if (KamenRide.current(player) != KamenRide.COMPLETE) return;
        KamenRide.set(level, player, KamenRide.DEX);
        if (exhausted) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 2));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1));
            player.sendSystemMessage(Component.translatable("message.dexrider.complete_exhausted"), true);
        }
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }
}
