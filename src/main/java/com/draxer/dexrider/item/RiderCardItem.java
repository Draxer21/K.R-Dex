package com.draxer.dexrider.item;

import com.draxer.dexrider.registry.DexSounds;
import com.draxer.dexrider.registry.DexComponents;
import com.draxer.dexrider.rider.ArmedRide;
import com.draxer.dexrider.rider.FormRide;
import com.draxer.dexrider.rider.KamenRide;
import com.draxer.dexrider.rider.RiderPowers;
import com.draxer.dexrider.vehicle.DexMachineEntity;
import com.draxer.dexrider.rider.Transformation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Carta Rider. Las cartas son permanentes (no se gastan), como en el fanfic.
 * Al usarla, ademas, queda anotada en el mazo del Driver (cuenta para el escalado).
 */
public class RiderCardItem extends Item {

    private final DexCard card;

    public RiderCardItem(Properties properties, DexCard card) {
        super(properties);
        this.card = card;
    }

    public DexCard card() {
        return card;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        return activate(serverLevel, sp, player.getItemInHand(hand), hand);
    }

    /**
     * Activa la carta. Tambien lo usa el Ride Booker (insercion directa: la carta no sale del Booker;
     * un Armed Ride aparece en la mano principal y lo que tenias ahi pasa al inventario).
     */
    public InteractionResult activate(ServerLevel serverLevel, ServerPlayer sp, ItemStack stack, InteractionHand hand) {
        if (sp.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;

        DexDriverItem.registerCard(sp, card);

        switch (card.family()) {
            case KAMEN_RIDE -> loadKamenRide(serverLevel, sp);
            case ATTACK_RIDE -> attackRide(serverLevel, sp, stack);
            case ARMED_RIDE -> {
                if (requireTransformed(sp)) ArmedRide.summon(serverLevel, sp, hand, card);
            }
            case FORM_RIDE -> {
                if (requireTransformed(sp)) {
                    sp.getCooldowns().addCooldown(stack, 20);
                    FormRide.use(serverLevel, sp, FormRide.byCard(card));
                }
            }
            case VEHICLE_RIDE -> {
                if (requireTransformed(sp)) {
                    sp.getCooldowns().addCooldown(stack, 40);
                    com.draxer.dexrider.vehicle.BikeKind kind = com.draxer.dexrider.vehicle.BikeKind.byCard(card);
                    DexMachineEntity.summon(serverLevel, sp, kind != null ? kind : com.draxer.dexrider.vehicle.BikeKind.DEX_MACHINE);
                }
            }
            case FINAL_ATTACK_RIDE -> {
                if (requireTransformed(sp)) {
                    sp.getCooldowns().addCooldown(stack, 300);
                    KamenRide identity = KamenRide.current(sp);
                    if (RiderPowers.hasFinisher(identity)) RiderPowers.finalAttack(serverLevel, sp, identity);
                    else ArmedRide.finalAttack(serverLevel, sp, hand);
                }
            }
            default -> sp.sendSystemMessage(Component.translatable("message.dexrider.not_implemented"), true);
        }
        return InteractionResult.SUCCESS;
    }

    private static boolean requireTransformed(ServerPlayer player) {
        if (Transformation.isTransformed(player)) return true;
        player.sendSystemMessage(Component.translatable("message.dexrider.need_transform"), true);
        return false;
    }

    private void loadKamenRide(ServerLevel level, ServerPlayer player) {
        if (Transformation.isTransformed(player)) {
            // Transformado: la carta cambia de identidad (Nine, Cipher...) o vuelve a Dex
            KamenRide target = KamenRide.byCard(card);
            if (target != null) {
                player.getCooldowns().addCooldown(player.getMainHandItem().is(this) ? player.getMainHandItem() : player.getOffhandItem(), 20);
                KamenRide.use(level, player, target);
            }
            return;
        }
        if (card != DexCard.KAMEN_RIDE_DEX) {
            // Las Kamen Ride de otros Riders solo funcionan sobre la transformacion de Dex
            player.sendSystemMessage(Component.translatable("message.dexrider.kamen_ride_need_dex"), true);
            return;
        }
        ItemStack driver = DexDriverItem.findDriver(player);
        if (driver.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.dexrider.need_driver"), true);
            return;
        }
        driver.set(DexComponents.LOADED_CARD.get(), card.id());
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                DexSounds.CARD_INSERT.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        player.sendSystemMessage(Component.translatable("message.dexrider.card_loaded"), true);
    }

    private void attackRide(ServerLevel level, ServerPlayer player, ItemStack stack) {
        if (!requireTransformed(player)) return;
        // Attack Ride: Slash -> golpes mucho mas fuertes durante 10 segundos.
        player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 200, 2, false, true, true));
        player.getCooldowns().addCooldown(stack, 600);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.8F);
        DexSounds.play(level, player, DexSounds.VOICE_ATTACK_RIDE, 1.0F, 1.0F);
        player.sendSystemMessage(Component.translatable("message.dexrider.attack_ride_slash"), true);
    }
}
