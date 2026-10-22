package com.draxer.dexrider.registry;

import com.draxer.dexrider.DexRider;
import com.draxer.dexrider.menu.RideBookerMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class DexMenus {
    private DexMenus() {}

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, DexRider.MODID);

    public static final Supplier<MenuType<RideBookerMenu>> RIDE_BOOKER =
            MENUS.register("ride_booker", () -> new MenuType<>(RideBookerMenu::new, FeatureFlags.DEFAULT_FLAGS));
}
