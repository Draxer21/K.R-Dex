package com.draxer.dexrider.rider;

import com.draxer.dexrider.item.SuitConfig;
import com.draxer.dexrider.item.SuitProfile;
import com.draxer.dexrider.item.SuitSlots;
import com.draxer.dexrider.registry.DexComponents;
import net.minecraft.world.item.ItemStack;

/** Pone el traje en el peto (lo que ven todos). */
public final class Suits {
    private Suits() {}

    /** Tu diseno puesto (la ranura activa del Driver). */
    public static void dressOwn(ItemStack chest, ItemStack driver) {
        chest.set(DexComponents.SUIT_PROFILE.get(), SuitSlots.of(driver).current());
        chest.remove(DexComponents.SUIT.get());
    }

    /** Un traje de tres colores (el de un portador del Driver): se marca con el componente antiguo. */
    public static void dressLegacy(ItemStack chest, SuitConfig c) {
        chest.set(DexComponents.SUIT_PROFILE.get(), SuitProfile.fromLegacy(c));
        chest.set(DexComponents.SUIT.get(), c);
    }

    /** Lo que lleva puesto un peto. */
    public static SuitProfile worn(ItemStack chest) {
        SuitProfile p = chest.get(DexComponents.SUIT_PROFILE.get());
        if (p != null) return p;
        SuitConfig c = chest.get(DexComponents.SUIT.get());
        return c == null ? SuitProfile.DEFAULT : SuitProfile.fromLegacy(c);
    }
}
