package com.draxer.dexrider.item;

import com.draxer.dexrider.rider.CipherModes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Equipo de Cipher (no es un arma Armed Ride: es un objeto real que te dan, y no desaparece al destransformarte).
 *  ACCEL   : reloj. Clic derecho siendo Kamen Ride: Cipher -> 10 s de Accel.
 *  BLASTER : arma. Llevarla en la mano principal siendo Cipher -> forma Blaster. Clic derecho dispara, Shift corta.
 */
public class CipherGearItem extends Item {

    public enum Kind { ACCEL, BLASTER }

    private final Kind kind;

    public CipherGearItem(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        ItemStack stack = player.getItemInHand(hand);
        if (sp.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;
        if (kind == Kind.ACCEL) {
            if (CipherModes.startAccel(server, sp)) sp.getCooldowns().addCooldown(stack, CipherModes.ACCEL_COOLDOWN);
        } else {
            int cd = CipherModes.useBlaster(server, sp);
            if (cd > 0) sp.getCooldowns().addCooldown(stack, cd);
        }
        return InteractionResult.SUCCESS;
    }
}
