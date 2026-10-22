package com.draxer.dexrider.rider;

import com.draxer.dexrider.item.DexCard;
import com.draxer.dexrider.item.DexDriverItem;
import com.draxer.dexrider.item.DriverDeck;
import com.draxer.dexrider.item.SuitConfig;
import com.draxer.dexrider.registry.DexComponents;
import com.draxer.dexrider.registry.DexItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * El Driver prestado (del fanfic), en multijugador:
 *
 *  - PRESTAR: sin transformarte, clic derecho con el Dex Driver sobre otro jugador. El Driver pasa a su inventario.
 *  - Funciona con tu consentimiento: si pulsas V sin Driver, lo REVOCAS: se apaga, el portador se destransforma
 *    al instante y el Driver vuelve a ti. El portador tambien puede devolverlo (clic derecho con el Driver).
 *  - El portador se transforma con V sin necesidad de la carta. El Driver cambia con cada usuario: su traje sale
 *    con una paleta propia. Transformarse cansa poco; pelear drena mucho (hambre). No escanea.
 *  - La primera vez que alguien se transforma con tu Driver, nace en tu mazo "Kamen Ride: Portador" con su forma:
 *    usandola (transformado como Dex) tomas su paleta. Cuenta como Kamen Ride (escalado y forja del K-Touch).
 */
public final class LentDriver {
    private LentDriver() {}

    /** duenio -> portador */
    private static final Map<UUID, UUID> LENT = new HashMap<>();

    public static boolean isLent(ItemStack driver) {
        return driver.get(DexComponents.LENT_BY.get()) != null;
    }

    public static boolean lend(ServerLevel level, ServerPlayer owner, ServerPlayer target, ItemStack driver) {
        if (Transformation.isTransformed(owner)) {
            owner.sendSystemMessage(Component.translatable("message.dexrider.lend_transformed"), true);
            return false;
        }
        if (isLent(driver) || Transformation.isTransformed(target)) return false;
        ItemStack copy = driver.copy();
        copy.set(DexComponents.LENT_BY.get(), owner.getUUID().toString());
        copy.set(DexComponents.LENT_OWNER.get(), owner.getGameProfile().name());
        copy.remove(DexComponents.LOADED_CARD.get());
        if (!target.getInventory().add(copy)) return false;
        driver.setCount(0);
        LENT.put(owner.getUUID(), target.getUUID());
        FX.ring(level, target, FX.MAGENTA, 1.2, 6);
        owner.sendSystemMessage(Component.translatable("message.dexrider.lend_out", target.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
        target.sendSystemMessage(Component.translatable("message.dexrider.lend_in", owner.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
        return true;
    }

    /** V sin Driver: si lo prestaste, lo revocas. Devuelve true si habia un prestamo. */
    public static boolean revoke(ServerPlayer owner) {
        UUID b = LENT.get(owner.getUUID());
        if (b == null) return false;
        ServerPlayer bearer = owner.level().getServer().getPlayerList().getPlayer(b);
        if (bearer == null) {
            owner.sendSystemMessage(Component.translatable("message.dexrider.lend_offline"), true);
            return true;
        }
        giveBack(bearer, owner, true);
        return true;
    }

    /** El Driver vuelve a su duenio (revocado o devuelto). */
    public static void giveBack(ServerPlayer bearer, ServerPlayer owner, boolean revoked) {
        if (Transformation.isTransformed(bearer)) Transformation.detransform(bearer, true);
        ItemStack driver = DexDriverItem.findDriver(bearer);
        if (driver.isEmpty() || !isLent(driver)) return;
        ItemStack back = driver.copy();
        back.remove(DexComponents.LENT_BY.get());
        back.remove(DexComponents.LENT_OWNER.get());
        driver.setCount(0);
        if (!owner.getInventory().add(back)) owner.drop(back, false);
        LENT.remove(owner.getUUID());
        owner.sendSystemMessage(Component.translatable(revoked ? "message.dexrider.lend_revoked" : "message.dexrider.lend_returned", bearer.getDisplayName()), true);
        bearer.sendSystemMessage(Component.translatable(revoked ? "message.dexrider.lend_revoked_bearer" : "message.dexrider.lend_returned_bearer"), true);
    }

    /** El portador devuelve el Driver (clic derecho con el). */
    public static boolean returnIt(ServerPlayer bearer, ItemStack driver) {
        String by = driver.get(DexComponents.LENT_BY.get());
        if (by == null) return false;
        ServerPlayer owner = bearer.level().getServer().getPlayerList().getPlayer(UUID.fromString(by));
        if (owner == null) {
            bearer.sendSystemMessage(Component.translatable("message.dexrider.lend_owner_offline"), true);
            return true;
        }
        giveBack(bearer, owner, false);
        return true;
    }

    /** La paleta del Driver para este portador: sale de quien es (siempre la misma para la misma persona). */
    public static SuitConfig paletteFor(UUID who) {
        long h = who.getMostSignificantBits() ^ who.getLeastSignificantBits();
        float hue = (Math.floorMod(h, 360)) / 360.0F;
        int main = java.awt.Color.HSBtoRGB(hue, 0.72F, 0.78F) & 0xFFFFFF;
        int eyes = java.awt.Color.HSBtoRGB((hue + 0.5F) % 1.0F, 0.8F, 1.0F) & 0xFFFFFF;
        int accent = (h & 1) == 0 ? 0xE8B630 : 0xC8CCD8;
        SuitConfig.Horns horns = SuitConfig.Horns.values()[(int) Math.floorMod(h >> 3, 4)];
        return new SuitConfig(main, eyes, accent, horns, (h & 2) == 0, true);
    }

    /** Al transformarse un portador: su traje propio, y la carta nace en el mazo del Driver. */
    public static void onBearerTransform(ServerPlayer bearer, ItemStack driver) {
        SuitConfig own = paletteFor(bearer.getUUID());
        bearer.getItemBySlot(EquipmentSlot.CHEST).set(DexComponents.SUIT.get(), own);
        DriverDeck deck = DexDriverItem.getDeck(driver);
        driver.set(DexComponents.BEARER_SUIT.get(), own);
        driver.set(DexComponents.BEARER_NAME.get(), bearer.getGameProfile().name());
        if (!deck.has(DexCard.KAMEN_RIDE_BEARER)) {
            DexDriverItem.setDeck(driver, deck.withCard(DexCard.KAMEN_RIDE_BEARER));
            bearer.sendSystemMessage(Component.translatable("message.dexrider.bearer_card", bearer.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
            String by = driver.get(DexComponents.LENT_BY.get());
            ServerPlayer owner = by == null ? null : bearer.level().getServer().getPlayerList().getPlayer(UUID.fromString(by));
            if (owner != null) {
                owner.sendSystemMessage(Component.translatable("message.dexrider.bearer_card", bearer.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
                ItemStack card = new ItemStack(DexItems.card(DexCard.KAMEN_RIDE_BEARER).get());
                if (!owner.getInventory().add(card)) owner.drop(card, false);
            }
        }
    }

    /** Pelear con un Driver prestado drena mucho. */
    public static void tick(ServerPlayer p) {
        ItemStack driver = DexDriverItem.findDriver(p);
        if (driver.isEmpty() || !isLent(driver) || p.isCreative()) return;
        p.causeFoodExhaustion(0.012F);
        if (p.getFoodData().getFoodLevel() <= 0 && p.tickCount % 20 == 0) {
            Transformation.detransform(p, true);
            p.sendSystemMessage(Component.translatable("message.dexrider.lend_exhausted"), true);
        }
    }

    /** Usar la carta "Kamen Ride: Portador" (transformado como Dex): tomas la forma del ultimo portador. */
    public static void useBearerCard(ServerLevel level, ServerPlayer p) {
        if (!Transformation.isTransformed(p) || KamenRide.current(p) != KamenRide.DEX) {
            p.sendSystemMessage(Component.translatable("message.dexrider.need_transform"), true);
            return;
        }
        ItemStack driver = DexDriverItem.findDriver(p);
        SuitConfig bearer = driver.get(DexComponents.BEARER_SUIT.get());
        if (bearer == null) return;
        ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
        SuitConfig cur = chest.get(DexComponents.SUIT.get());
        SuitConfig own = driver.get(DexComponents.SUIT.get());
        boolean toBearer = !bearer.equals(cur);
        chest.set(DexComponents.SUIT.get(), toBearer ? bearer : (own != null ? own : SuitConfig.DEFAULT));
        FX.ring(level, p, toBearer ? bearer.mainColor() : FX.MAGENTA, 1.3, 7);
        com.draxer.dexrider.registry.DexSounds.play(level, p, com.draxer.dexrider.registry.DexSounds.HENSHIN, 1.0F, 1.2F);
        String name = driver.get(DexComponents.BEARER_NAME.get());
        p.sendSystemMessage(Component.translatable(toBearer ? "message.dexrider.bearer_form" : "message.dexrider.bearer_form_off",
                name == null ? "?" : name), true);
    }
}
