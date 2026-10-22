package com.draxer.dexrider.item;

import com.draxer.dexrider.registry.DexComponents;
import com.draxer.dexrider.registry.DexSounds;
import com.draxer.dexrider.rider.KamenRide;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Carta de Complete Form. Cada grupo distinto de nueve Kamen Ride forja la suya (regla del fanfic):
 *   0 = la primera (Nine, Forge, Psyga, Cipher, Warden, Orga, Spore, Mirage, Proto)
 *   1 = la segunda: Kaixa ocupa el hueco de Proto.
 * Clic derecho: la insertas en el K-Touch (decide que anillo usan la pantalla y los blasones).
 */
public class CompleteFormCardItem extends Item {
    private final int ring;

    public CompleteFormCardItem(Properties properties, int ring) {
        super(properties);
        this.ring = ring;
    }

    public int ring() { return ring; }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        if (KamenRide.current(sp) == KamenRide.COMPLETE) {
            sp.sendSystemMessage(Component.translatable("message.dexrider.complete_locked"), true);
            return InteractionResult.FAIL;
        }
        ItemStack driver = DexDriverItem.findDriver(sp);
        if (driver.isEmpty()) {
            sp.sendSystemMessage(Component.translatable("message.dexrider.need_driver"), true);
            return InteractionResult.FAIL;
        }
        driver.set(DexComponents.KTOUCH_RING.get(), ring);
        DexSounds.play(server, sp, DexSounds.CARD_INSERT, 1.0F, 0.8F);
        sp.sendSystemMessage(Component.translatable("message.dexrider.complete_card_" + ring), true);
        return InteractionResult.SUCCESS;
    }
}
