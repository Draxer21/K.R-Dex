package com.draxer.dexrider.menu;

import com.draxer.dexrider.item.BookerContents;
import com.draxer.dexrider.item.DexDriverItem;
import com.draxer.dexrider.item.RiderCardItem;
import com.draxer.dexrider.registry.DexComponents;
import com.draxer.dexrider.registry.DexMenus;
import com.draxer.dexrider.registry.DexSounds;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Ride Booker: el estuche de cartas que Dex lleva en la cadera (en el fanfic, Mei lo diseno y el Driver lo genero
 * con cinco mazos). Se guarda en el Driver, asi que va contigo y no se pierde al destransformarte.
 *
 *  - 5 mazos (pestanas) de 18 cartas: Rescate, Multitudes, 1 vs 1, Persecucion, Todo salio mal.
 *  - Solo admite cartas Rider (y la carta de Complete Form).
 *  - Modo INSERTAR: haces clic en una carta y se activa al instante sin sacarla (insercion directa, como en el fanfic).
 */
public class RideBookerMenu extends AbstractContainerMenu {

    public static final int DECKS = 5;
    public static final int PER_DECK = 18;
    public static final int SIZE = DECKS * PER_DECK;
    /** Botones: 0..4 = mazo, 10 = alternar modo insertar. */
    public static final int BUTTON_INSERT = 10;

    private final Container storage;
    private final Player player;
    private int deck;
    private boolean insertMode;
    private int pendingUse = -1;

    /** Vista del mazo activo: 18 ranuras que apuntan al mazo elegido del almacenamiento. */
    private final Container view = new Container() {
        private int off() { return deck * PER_DECK; }
        @Override public int getContainerSize() { return PER_DECK; }
        @Override public boolean isEmpty() {
            for (int i = 0; i < PER_DECK; i++) if (!getItem(i).isEmpty()) return false;
            return true;
        }
        @Override public ItemStack getItem(int i) { return storage.getItem(off() + i); }
        @Override public ItemStack removeItem(int i, int n) { return storage.removeItem(off() + i, n); }
        @Override public ItemStack removeItemNoUpdate(int i) { return storage.removeItemNoUpdate(off() + i); }
        @Override public void setItem(int i, ItemStack s) { storage.setItem(off() + i, s); }
        @Override public void setChanged() { storage.setChanged(); }
        @Override public boolean stillValid(Player p) { return true; }
        @Override public void clearContent() { for (int i = 0; i < PER_DECK; i++) setItem(i, ItemStack.EMPTY); }
    };

    /** Cliente. */
    public RideBookerMenu(int id, Inventory inv) {
        this(id, inv, new SimpleContainer(SIZE));
    }

    /** Servidor. */
    public RideBookerMenu(int id, Inventory inv, Container storage) {
        super(DexMenus.RIDE_BOOKER.get(), id);
        this.storage = storage;
        this.player = inv.player;
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new CardSlot(view, row * 9 + col, 8 + col * 18, 34 + row * 18));
            }
        }
        addStandardInventorySlots(inv, 8, 84);
    }

    public int deck() { return deck; }
    public boolean insertMode() { return insertMode; }

    public static boolean isCard(ItemStack stack) {
        return stack.getItem() instanceof RiderCardItem || stack.is(com.draxer.dexrider.registry.DexItems.COMPLETE_FORM_CARD.get());
    }

    private class CardSlot extends Slot {
        CardSlot(Container c, int index, int x, int y) { super(c, index, x, y); }

        @Override public boolean mayPlace(ItemStack stack) { return isCard(stack); }

        @Override public int getMaxStackSize() { return 1; }

        @Override
        public boolean mayPickup(Player p) {
            if (insertMode && hasItem()) {
                // modo insertar: la carta no sale del Booker, se activa
                if (p instanceof ServerPlayer) pendingUse = deck * PER_DECK + getContainerSlot();
                return false;
            }
            return true;
        }
    }

    @Override
    public boolean clickMenuButton(Player p, int id) {
        if (id >= 0 && id < DECKS) {
            deck = id;
            return true;
        }
        if (id == BUTTON_INSERT) {
            insertMode = !insertMode;
            return true;
        }
        return false;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (pendingUse >= 0 && player instanceof ServerPlayer sp && sp.level() instanceof ServerLevel level) {
            ItemStack card = storage.getItem(pendingUse).copy();
            pendingUse = -1;
            if (card.getItem() instanceof RiderCardItem rc) {
                sp.closeContainer();
                DexSounds.play(level, sp, DexSounds.CARD_INSERT, 1.0F, 1.2F);
                rc.activate(level, sp, card, InteractionHand.MAIN_HAND);
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < PER_DECK) {
            if (!moveItemStackTo(stack, PER_DECK, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!isCard(stack) || !moveItemStackTo(stack, 0, PER_DECK, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player p) {
        return !(p instanceof ServerPlayer) || !DexDriverItem.findDriver(p).isEmpty();
    }

    /** Guarda el contenido en el Driver que lleva ahora el jugador (por si lo movio de ranura). */
    private static void save(Player player, Container c) {
        ItemStack driver = DexDriverItem.findDriver(player);
        if (driver.isEmpty()) return;
        NonNullList<ItemStack> now = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        for (int i = 0; i < SIZE; i++) now.set(i, c.getItem(i));
        driver.set(DexComponents.BOOKER.get(), BookerContents.of(now));
    }

    @Override
    public void removed(Player p) {
        super.removed(p);
        if (p instanceof ServerPlayer) save(p, storage);
    }

    // ------------------------------------------------------------------ uso rapido en combate (sin abrir el Booker)

    public static final int QUICK_PREV = 0, QUICK_NEXT = 1, QUICK_DECK_PREV = 2, QUICK_DECK_NEXT = 3, QUICK_USE = 4;
    private static final String[] DECK_KEYS = {"rescue", "crowd", "duel", "chase", "everything_wrong"};

    /**
     * Teclas rapidas: elegir la carta anterior/siguiente del mazo, cambiar de mazo, o insertar la elegida.
     * La eleccion se guarda en el Driver y se muestra encima de la barra de objetos.
     */
    public static void quick(ServerPlayer player, int action) {
        ItemStack driver = DexDriverItem.findDriver(player);
        if (driver.isEmpty() || !(player.level() instanceof ServerLevel level)) return;
        NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        BookerContents saved = driver.get(DexComponents.BOOKER.get());
        if (saved != null) saved.copyInto(items);
        Integer selBox = driver.get(DexComponents.BOOKER_SEL.get());
        int sel = selBox == null ? 0 : Math.floorMod(selBox, SIZE);
        int deck = sel / PER_DECK;

        switch (action) {
            case QUICK_PREV, QUICK_NEXT -> {
                int step = action == QUICK_NEXT ? 1 : -1;
                int found = scan(items, deck, sel % PER_DECK, step, false);
                if (found < 0) { emptyDeck(player, deck); return; }
                sel = found;
            }
            case QUICK_DECK_PREV, QUICK_DECK_NEXT -> {
                deck = Math.floorMod(deck + (action == QUICK_DECK_NEXT ? 1 : -1), DECKS);
                int found = scan(items, deck, 0, 1, true);
                sel = found < 0 ? deck * PER_DECK : found;
            }
            case QUICK_USE -> {
                if (items.get(sel).isEmpty()) {
                    int found = scan(items, deck, sel % PER_DECK, 1, false);
                    if (found < 0) { emptyDeck(player, deck); return; }
                    sel = found;
                    driver.set(DexComponents.BOOKER_SEL.get(), sel);
                }
                ItemStack card = items.get(sel).copy();
                if (card.getItem() instanceof RiderCardItem rc) {
                    DexSounds.play(level, player, DexSounds.CARD_INSERT, 1.0F, 1.2F);
                    rc.activate(level, player, card, InteractionHand.MAIN_HAND);
                }
                return;
            }
            default -> { return; }
        }
        driver.set(DexComponents.BOOKER_SEL.get(), sel);
        ItemStack chosen = items.get(sel);
        // En Violent / Heroic Emotion la carta responde sola: elegirla ya la activa, sin insertarla
        if (com.draxer.dexrider.rider.DexEmotions.current(player) != null && !chosen.isEmpty()
                && chosen.getItem() instanceof RiderCardItem rc && (action == QUICK_PREV || action == QUICK_NEXT)) {
            com.draxer.dexrider.rider.FX.ring(level, player, com.draxer.dexrider.rider.DexEmotions.colors(com.draxer.dexrider.rider.DexEmotions.current(player))[1], 0.9, 5);
            rc.activate(level, player, chosen.copy(), InteractionHand.MAIN_HAND);
            return;
        }
        DexSounds.play(level, player, DexSounds.CARD_INSERT, 0.35F, 1.7F);
        Component deckName = Component.translatable("booker.dexrider.deck." + DECK_KEYS[sel / PER_DECK]);
        player.sendSystemMessage(chosen.isEmpty()
                ? Component.translatable("message.dexrider.booker_deck_empty", deckName)
                : Component.translatable("message.dexrider.booker_selected", deckName, (sel % PER_DECK) + 1, chosen.getHoverName()), true);
    }

    /** Siguiente ranura con carta dentro del mazo (dando la vuelta). includeStart = mirar tambien la ranura de partida. */
    private static int scan(NonNullList<ItemStack> items, int deck, int from, int step, boolean includeStart) {
        for (int n = includeStart ? 0 : 1; n <= PER_DECK; n++) {
            int slot = Math.floorMod(from + n * step, PER_DECK);
            ItemStack s = items.get(deck * PER_DECK + slot);
            if (!s.isEmpty() && s.getItem() instanceof RiderCardItem) return deck * PER_DECK + slot;
        }
        return -1;
    }

    private static void emptyDeck(ServerPlayer player, int deck) {
        player.sendSystemMessage(Component.translatable("message.dexrider.booker_deck_empty",
                Component.translatable("booker.dexrider.deck." + DECK_KEYS[deck])), true);
    }

    // ------------------------------------------------------------------ abrir

    /** Abre el Ride Booker del Driver que lleva el jugador. */
    public static void open(ServerPlayer player) {
        ItemStack driver = DexDriverItem.findDriver(player);
        if (driver.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.dexrider.need_driver"), true);
            return;
        }
        NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        BookerContents saved = driver.get(DexComponents.BOOKER.get());
        if (saved != null) saved.copyInto(items);
        // cada cambio se guarda en el Driver
        SimpleContainer storage = new SimpleContainer(SIZE) {
            private boolean loading = true;
            @Override
            public void setChanged() {
                super.setChanged();
                if (!loading) save(player, this);
            }
            { for (int i = 0; i < SIZE; i++) setItem(i, items.get(i)); loading = false; }
        };
        if (player.level() instanceof ServerLevel level) DexSounds.play(level, player, DexSounds.CARD_INSERT, 0.6F, 0.8F);
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new RideBookerMenu(id, inv, storage),
                Component.translatable("screen.dexrider.ride_booker")));
    }
}
