package com.draxer.dexrider.item;

import com.draxer.dexrider.registry.DexComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import com.draxer.dexrider.client.ClientHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

/**
 * El Dex Driver. Basta con llevarlo en el inventario.
 * Clic derecho con el Driver en la mano: muestra el mazo y el escalado de cada familia.
 */
public class DexDriverItem extends Item {

    public DexDriverItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        // Shift + clic derecho: pantalla de personalizacion del traje (solo en el cliente)
        if (player.isShiftKeyDown()) {
            if (!(level instanceof ServerLevel)) {
                ClientHooks.openSuitScreen(player.getItemInHand(hand));
            }
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp) {
            ItemStack driver = player.getItemInHand(hand);
            DriverDeck deck = getDeck(driver);
            String loaded = driver.get(DexComponents.LOADED_CARD.get());

            sp.sendSystemMessage(Component.translatable("message.dexrider.deck_header", deck.cards().size())
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            for (DexCard.Family family : DexCard.Family.values()) {
                int count = deck.count(family);
                if (count == 0) continue;
                sp.sendSystemMessage(Component.literal(" • ")
                        .append(Component.translatable("family.dexrider." + family.name().toLowerCase()))
                        .append(Component.literal(": " + count + "  (x" + String.format("%.2f", deck.multiplier(family)) + ")"))
                        .withStyle(ChatFormatting.GOLD));
            }
            for (DexCard card : DexCard.values()) {
                int samples = deck.samples(card);
                if (samples > 0 && !deck.has(card)) {
                    MutableComponent line = Component.literal(" ? ")
                            .append(Component.translatable("message.dexrider.scan_partial",
                                    samples, card.samplesToScan()));
                    sp.sendSystemMessage(line.withStyle(ChatFormatting.GRAY));
                }
            }
            if (loaded != null) {
                sp.sendSystemMessage(Component.translatable("message.dexrider.driver_loaded",
                        Component.translatable("item.dexrider.card_" + loaded)), true);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Busca el Dex Driver: primero en la ranura de cinturon de Curios, despues en el inventario.
     * Devuelve ItemStack.EMPTY si no lo lleva.
     */
    public static ItemStack findDriver(Player player) {
        ItemStack worn = CuriosApi.getCuriosInventory(player)
                .flatMap(handler -> handler.findFirstCurio(stack -> stack.getItem() instanceof DexDriverItem))
                .map(SlotResult::stack)
                .orElse(ItemStack.EMPTY);
        if (!worn.isEmpty()) return worn;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof DexDriverItem) return stack;
        }
        return ItemStack.EMPTY;
    }

    public static DriverDeck getDeck(ItemStack driver) {
        DriverDeck deck = driver.get(DexComponents.DECK.get());
        return deck != null ? deck : DriverDeck.EMPTY;
    }

    public static void setDeck(ItemStack driver, DriverDeck deck) {
        driver.set(DexComponents.DECK.get(), deck);
    }

    /** Anota una carta en el mazo del Driver (si el jugador lleva uno). */
    public static void registerCard(Player player, DexCard card) {
        ItemStack driver = findDriver(player);
        if (!driver.isEmpty()) {
            DriverDeck deck = getDeck(driver);
            if (!deck.has(card)) {
                DriverDeck updated = deck.withCard(card);
                setDeck(driver, updated);
                if (card.family() == DexCard.Family.KAMEN_RIDE && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                    com.draxer.dexrider.rider.KTouch.onKamenRideAdded(sp, driver, updated);
                }
            }
        }
    }
}
