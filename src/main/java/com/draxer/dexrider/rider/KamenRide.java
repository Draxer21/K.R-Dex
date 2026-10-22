package com.draxer.dexrider.rider;

import com.draxer.dexrider.item.DexCard;
import com.draxer.dexrider.registry.DexComponents;
import com.draxer.dexrider.registry.DexSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Identidades de Rider (familia Kamen Ride). Dex puede convertirse en los Riders cuyas cartas tiene.
 * La identidad activa se guarda en el peto (componente RIDER), asi todos la ven.
 *
 *  implemented = ya tiene armadura 3D y poderes. Las demas se van agregando por tandas;
 *  mientras tanto su carta existe (cuenta para el K-Touch) pero todavia no transforma.
 *
 *  COMPLETE no tiene carta: se entra con el K-Touch.
 */
public enum KamenRide {
    DEX(DexCard.KAMEN_RIDE_DEX, "dex_armor", true, 0xCD1C28),
    NINE(DexCard.KAMEN_RIDE_NINE, "rider_nine", true, 0x2EA046),
    FORGE(DexCard.KAMEN_RIDE_FORGE, "rider_forge", true, 0x8C1422),
    PSYGA(DexCard.KAMEN_RIDE_PSYGA, "rider_psyga", true, 0xECECF4),
    CIPHER(DexCard.KAMEN_RIDE_CIPHER, "rider_cipher", true, 0x1E2C5C),
    WARDEN(DexCard.KAMEN_RIDE_WARDEN, "rider_warden", true, 0x1A1A20),
    ORGA(DexCard.KAMEN_RIDE_ORGA, "rider_orga", true, 0xFAC83C),
    SPORE(DexCard.KAMEN_RIDE_SPORE, "rider_spore", true, 0xBCA8D6),
    MIRAGE(DexCard.KAMEN_RIDE_MIRAGE, "rider_mirage", true, 0xDCE4F0),
    PROTO(DexCard.KAMEN_RIDE_PROTO, "rider_proto", true, 0xFF8C1E),
    KAIXA(DexCard.KAMEN_RIDE_KAIXA, "rider_kaixa", true, 0x385CBE),
    COMPLETE(null, "complete_form", true, 0x96101E);

    /** Orden de los nueve blasones del K-Touch (en anillo; Dex va al centro). */
    public static final KamenRide[] RING = {NINE, FORGE, PSYGA, CIPHER, WARDEN, ORGA, SPORE, MIRAGE, PROTO};
    /** Segunda carta de Complete Form: Kaixa ocupa el hueco de Proto. */
    public static final KamenRide[] RING_2 = {NINE, FORGE, PSYGA, CIPHER, WARDEN, ORGA, SPORE, MIRAGE, KAIXA};

    /** Anillo de la carta de Complete Form que lleva insertada el K-Touch de este jugador. */
    public static KamenRide[] ring(net.minecraft.world.entity.player.Player player) {
        ItemStack driver = com.draxer.dexrider.item.DexDriverItem.findDriver(player);
        Integer r = driver.isEmpty() ? null : driver.get(DexComponents.KTOUCH_RING.get());
        return r != null && r == 1 ? RING_2 : RING;
    }

    @Nullable public final DexCard card;
    public final String model;
    public final boolean implemented;
    public final int color;

    KamenRide(@Nullable DexCard card, String model, boolean implemented, int color) {
        this.card = card;
        this.model = model;
        this.implemented = implemented;
        this.color = color;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable("rider.dexrider." + id());
    }

    public static KamenRide byId(@Nullable String id) {
        if (id != null) for (KamenRide k : values()) if (k.id().equals(id)) return k;
        return DEX;
    }

    @Nullable
    public static KamenRide byCard(DexCard card) {
        for (KamenRide k : values()) if (k.card == card) return k;
        return null;
    }

    /** Identidad activa (DEX si no hay ninguna guardada). */
    public static KamenRide current(LivingEntity entity) {
        return byId(entity.getItemBySlot(EquipmentSlot.CHEST).get(DexComponents.RIDER.get()));
    }

    // ------------------------------------------------------------------ cambiar de identidad

    /** Usar una carta Kamen Ride estando transformado. */
    public static void use(ServerLevel level, ServerPlayer player, KamenRide target) {
        KamenRide active = current(player);
        if (active == COMPLETE) {
            player.sendSystemMessage(Component.translatable("message.dexrider.complete_locked"), true);
            return;
        }
        if (!target.implemented) {
            player.sendSystemMessage(Component.translatable("message.dexrider.rider_not_ready", target.displayName()), true);
            return;
        }
        // La misma carta otra vez (o la de Dex) vuelve a Dex
        if (target == active || target == DEX) {
            if (active == DEX) {
                player.sendSystemMessage(Component.translatable("message.dexrider.already_transformed"), true);
                return;
            }
            set(level, player, DEX);
            return;
        }
        set(level, player, target);
        DexAdvancements.award(player, "kamen_ride");
    }

    /** Cambia la identidad sin comprobaciones (tambien la usa el K-Touch). */
    public static void set(ServerLevel level, ServerPlayer player, KamenRide target) {
        FormRide.clearForm(player);
        CipherModes.clear(player);
        RiderPowers.clear(player);
        ArmedRide.removeWeapons(player);
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        chest.remove(DexComponents.UPGRADE.get());
        if (target == DEX) chest.remove(DexComponents.RIDER.get());
        else chest.set(DexComponents.RIDER.get(), target.id());

        DexSounds.play(level, player, DexSounds.CARD_INSERT, 1.0F, 1.0F);
        DexSounds.play(level, player, DexSounds.HENSHIN, 1.0F, target == COMPLETE ? 0.7F : 1.1F);
        DexSounds.play(level, player, DexSounds.VOICE_KAMEN_RIDE, 1.0F, 1.0F);
        FX.ring(level, player, target.color, 1.5, 9);
        FX.ring(level, player, FX.MAGENTA, 1.0, 9);
        player.sendSystemMessage(Component.translatable("message.dexrider.kamen_ride", target.displayName()), true);
        Transformation.applyFormEffects(player);
    }
}
