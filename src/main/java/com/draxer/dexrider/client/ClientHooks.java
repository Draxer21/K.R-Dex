package com.draxer.dexrider.client;

import com.draxer.dexrider.item.SuitConfig;
import com.draxer.dexrider.registry.DexComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/** Llamadas que solo existen en el cliente (se usan desde los items solo cuando el mundo es del cliente). */
public final class ClientHooks {
    private ClientHooks() {}

    public static void openSuitScreen(ItemStack driver) {
        SuitConfig config = driver.get(DexComponents.SUIT.get());
        Minecraft.getInstance().setScreen(new SuitScreen(config));
    }

    public static void openSummonScreen() {
        Minecraft.getInstance().setScreen(new SummonScreen());
    }

    public static void openKTouchScreen() {
        Minecraft.getInstance().setScreen(new KTouchScreen());
    }
}
