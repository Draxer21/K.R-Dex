package com.draxer.dexrider.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** El cuaderno de Takumi: guia del Rider System dentro del juego. Clic derecho para leerlo. */
public class RiderGuideItem extends Item {
    public static final int PAGES = 21;

    public RiderGuideItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) com.draxer.dexrider.client.ClientHooks.openGuide();
        return InteractionResult.SUCCESS;
    }
}
