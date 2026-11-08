package com.draxer.dexrider.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** El diario de Yusuke: lee lo que el Driver fue anotando (rider/Diary). Clic derecho para leerlo. */
public class DiaryItem extends Item {
    public DiaryItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) com.draxer.dexrider.client.ClientHooks.openDiary();
        return InteractionResult.SUCCESS;
    }
}
