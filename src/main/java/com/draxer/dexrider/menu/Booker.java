package com.draxer.dexrider.menu;

import com.draxer.dexrider.item.BookerContents;
import com.draxer.dexrider.item.DexDriverItem;
import com.draxer.dexrider.registry.DexComponents;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * Acceso al Ride Booker guardado en el Driver.
 *
 *  - Migracion: hasta la 0.31 los mazos tenian 18 ranuras; desde la 0.32 tienen 27. Al leer un Booker viejo se
 *    reacomodan las cartas (mazo m, ranura r) a su nueva posicion, una sola vez.
 *  - Auto-guardado: las cartas que consigues (escaneo, Riders, cofres que recoges...) van directo al Booker.
 */
public final class Booker {
    private Booker() {}

    public static final int OLD_PER_DECK = 18;

    /** Lee el Booker del Driver (migrando el formato viejo si hace falta). */
    public static NonNullList<ItemStack> load(ItemStack driver) {
        NonNullList<ItemStack> items = NonNullList.withSize(RideBookerMenu.SIZE, ItemStack.EMPTY);
        if (driver.isEmpty()) return items;
        BookerContents saved = driver.get(DexComponents.BOOKER.get());
        boolean migrated = Boolean.TRUE.equals(driver.get(DexComponents.BOOKER_V27.get()));
        if (saved != null) {
            if (migrated) saved.copyInto(items);
            else {
                for (BookerContents.Entry e : saved.entries()) {
                    int to = remap(e.slot());
                    if (to >= 0 && to < items.size()) items.set(to, e.stack().copy());
                }
            }
        }
        if (!migrated) {
            driver.set(DexComponents.BOOKER_V27.get(), true);
            if (saved != null) driver.set(DexComponents.BOOKER.get(), BookerContents.of(items));
            Integer sel = driver.get(DexComponents.BOOKER_SEL.get());
            if (sel != null) driver.set(DexComponents.BOOKER_SEL.get(), remap(sel));
        }
        return items;
    }

    /** Solo lectura (cliente): el Booker con el formato nuevo, sin tocar el Driver. */
    public static NonNullList<ItemStack> view(ItemStack driver) {
        NonNullList<ItemStack> items = NonNullList.withSize(RideBookerMenu.SIZE, ItemStack.EMPTY);
        if (driver.isEmpty()) return items;
        BookerContents saved = driver.get(DexComponents.BOOKER.get());
        if (saved == null) return items;
        boolean migrated = Boolean.TRUE.equals(driver.get(DexComponents.BOOKER_V27.get()));
        for (BookerContents.Entry e : saved.entries()) {
            int to = migrated ? e.slot() : remap(e.slot());
            if (to >= 0 && to < items.size()) items.set(to, e.stack().copy());
        }
        return items;
    }

    /** Vecina con carta dentro del mazo de {@code sel} (step +1/-1), o -1. */
    public static int neighbor(NonNullList<ItemStack> items, int sel, int step) {
        int deck = sel / RideBookerMenu.PER_DECK;
        for (int n = 1; n < RideBookerMenu.PER_DECK; n++) {
            int slot = deck * RideBookerMenu.PER_DECK + Math.floorMod(sel % RideBookerMenu.PER_DECK + n * step, RideBookerMenu.PER_DECK);
            if (!items.get(slot).isEmpty()) return slot;
        }
        return -1;
    }

    private static int remap(int oldSlot) {
        return (oldSlot / OLD_PER_DECK) * RideBookerMenu.PER_DECK + (oldSlot % OLD_PER_DECK);
    }

    public static void save(ItemStack driver, NonNullList<ItemStack> items) {
        if (driver.isEmpty()) return;
        driver.set(DexComponents.BOOKER_V27.get(), true);
        driver.set(DexComponents.BOOKER.get(), BookerContents.of(items));
    }

    /**
     * Guarda una carta en el Booker del Driver del jugador: primero en el mazo elegido, luego en cualquier otro.
     * No guarda copias de una carta que ya esta en el Booker, ni si el Booker esta abierto (se guardaria encima).
     */
    public static boolean store(ServerPlayer p, ItemStack card) {
        if (card.isEmpty() || !RideBookerMenu.isCard(card)) return false;
        if (p.containerMenu instanceof RideBookerMenu) return false;
        ItemStack driver = DexDriverItem.findDriver(p);
        if (driver.isEmpty()) return false;
        NonNullList<ItemStack> items = load(driver);
        for (ItemStack s : items) if (!s.isEmpty() && s.is(card.getItem())) return false;
        Integer selBox = driver.get(DexComponents.BOOKER_SEL.get());
        int deck = selBox == null ? 0 : Math.floorMod(selBox, RideBookerMenu.SIZE) / RideBookerMenu.PER_DECK;
        int free = -1;
        for (int d = 0; d < RideBookerMenu.DECKS && free < 0; d++) {
            int dd = (deck + d) % RideBookerMenu.DECKS;
            for (int i = 0; i < RideBookerMenu.PER_DECK; i++) {
                if (items.get(dd * RideBookerMenu.PER_DECK + i).isEmpty()) { free = dd * RideBookerMenu.PER_DECK + i; break; }
            }
        }
        if (free < 0) return false;
        items.set(free, card.copyWithCount(1));
        save(driver, items);
        p.sendSystemMessage(Component.translatable("message.dexrider.booker_stored", card.getHoverName(),
                Component.translatable("booker.dexrider.deck." + RideBookerMenu.DECK_KEYS[free / RideBookerMenu.PER_DECK]),
                free % RideBookerMenu.PER_DECK + 1), true);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.4F);
        return true;
    }

    /** Entrega una carta: al Booker si se puede; si no, al inventario (o al suelo). */
    public static void give(ServerPlayer p, ItemStack card) {
        if (store(p, card)) { card.shrink(1); if (card.isEmpty()) return; }
        if (!p.getInventory().add(card)) p.drop(card, false);
    }
}
