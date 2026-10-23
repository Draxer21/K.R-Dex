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
 * Clic derecho transformado: la pantalla del aparato (nueve blasones para invocar amigos y el de Dex, que abre o
 * cierra la Complete Form). La misma pantalla que la tecla B. Teclado numerico: 1-9 blasones, 0 el de Dex.
 */
public class KTouchItem extends Item {

    public KTouchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel serverLevel)) {
            if (Transformation.isTransformed(player)) ClientHooks.openSummonScreen();
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        if (!Transformation.isTransformed(sp)) {
            sp.sendSystemMessage(Component.translatable("message.dexrider.need_transform"), true);
        }
        return InteractionResult.SUCCESS;
    }
}
