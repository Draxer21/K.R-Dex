package com.draxer.dexrider.client;

import com.draxer.dexrider.DexRider;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.enums.PlayState;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;

/**
 * Poses del jugador con Player Animation Library (dependencia OPCIONAL: esta clase solo se carga si esta instalada).
 * Animaciones: assets/dexrider/player_animations/dex_poses.json (henshin, card, kick, finisher, complete).
 */
public final class PoseClient {
    private PoseClient() {}

    private static final Identifier LAYER = DexRider.id("poses");

    public static void init() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, 1500,
                avatar -> new PlayerAnimationController(avatar, (controller, state, setter) -> PlayState.STOP));
        DexRider.LOGGER.info("Poses del jugador activadas (Player Animation Library)");
    }

    public static void play(int entityId, String pose) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        Entity e = level.getEntity(entityId);
        if (!(e instanceof Avatar avatar)) return;
        if (PlayerAnimationAccess.getPlayerAnimationLayer(avatar, LAYER) instanceof PlayerAnimationController c) {
            c.triggerAnimation(DexRider.id(pose));
        }
    }
}
