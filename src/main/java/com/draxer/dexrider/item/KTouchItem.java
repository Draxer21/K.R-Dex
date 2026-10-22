package com.draxer.dexrider.item;

import com.draxer.dexrider.client.ClientHooks;
import com.draxer.dexrider.registry.DexItems;
import com.draxer.dexrider.rider.KTouch;
import com.draxer.dexrider.rider.KamenRide;
import com.draxer.dexrider.rider.Transformation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * K-Touch. Lo forja el Driver al reunir nueve Kamen Ride.
 * Clic derecho transformado como Dex (con la carta de Complete Form en el inventario): pantalla de los diez pads.
 * Clic derecho en Complete Form: vuelves a Dex.
 */
public class KTouchItem extends Item {

    public KTouchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel serverLevel)) {
            if (Transformation.isTransformed(player) && KamenRide.current(player) == KamenRide.DEX
                    && KTouch.has(player, DexItems.COMPLETE_FORM_CARD.get())) {
                ClientHooks.openKTouchScreen();
            }
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        if (!Transformation.isTransformed(sp)) {
            sp.sendSystemMessage(Component.translatable("message.dexrider.need_transform"), true);
        } else if (KamenRide.current(sp) == KamenRide.COMPLETE) {
            KTouch.exit(serverLevel, sp, false);
        } else if (KamenRide.current(sp) != KamenRide.DEX) {
            sp.sendSystemMessage(Component.translatable("message.dexrider.ktouch_need_dex"), true);
        } else if (!KTouch.has(sp, DexItems.COMPLETE_FORM_CARD.get())) {
            sp.sendSystemMessage(Component.translatable("message.dexrider.ktouch_need_card"), true);
        }
        return InteractionResult.SUCCESS;
    }
}
