package com.draxer.dexrider.rider;

import com.draxer.dexrider.item.DexCard;
import com.draxer.dexrider.item.DexDriverItem;
import com.draxer.dexrider.item.DriverDeck;
import com.draxer.dexrider.registry.DexComponents;
import com.draxer.dexrider.registry.DexItems;
import com.draxer.dexrider.registry.DexSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Upgrade Form (del fanfic, dex_biblia 3.9): la "forma sin miedo", solo de Dex.
 * Escalera: Base -> Form Ride -> Upgrade -> Complete.
 *
 *  - Nace cuando el Driver ya sintio las dos Emotion (Violent y Heroic): Yusuke aprende a fiarse de su forma.
 *  - Es una carta normal: clic derecho (o desde el Ride Booker) la activa y la desactiva.
 *  - Excluyente con Violent / Heroic Emotion (en Upgrade las cartas se insertan a mano).
 *  - Las Form Ride se apilan encima y se amplifican (cuentan un nivel mas, hasta Nv3).
 *  - Sola: Fuerza III, Velocidad II, Salto II, Resistencia II, y tus golpes x1.2.
 *  - Traje propio: casco rojo con espolones dorados, cruz X blanca sobre el torso negro, puas rojas en los hombros.
 */
public final class UpgradeForm {
    private UpgradeForm() {}

    public static boolean isActive(LivingEntity e) {
        return Boolean.TRUE.equals(e.getItemBySlot(EquipmentSlot.CHEST).get(DexComponents.UPGRADE.get()));
    }

    public static void toggle(ServerLevel level, ServerPlayer p) {
        if (!Transformation.isTransformed(p)) {
            p.sendSystemMessage(Component.translatable("message.dexrider.need_transform"), true);
            return;
        }
        if (KamenRide.current(p) != KamenRide.DEX) {
            p.sendSystemMessage(Component.translatable("message.dexrider.upgrade_need_dex"), true);
            return;
        }
        ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
        if (isActive(p)) {
            chest.remove(DexComponents.UPGRADE.get());
            FX.ring(level, p, FX.MAGENTA, 1.0, 5);
            p.sendSystemMessage(Component.translatable("message.dexrider.upgrade_off"), true);
        } else {
            DexEmotions.clear(p);
            chest.set(DexComponents.UPGRADE.get(), true);
            FX.ring(level, p, 0xCD1C28, 1.6, 9);
            FX.ring(level, p, FX.WHITE, 1.1, 7);
            FX.particle(level, ParticleTypes.END_ROD, FX.center(p), 30, 0.6, 0.08);
            DexSounds.play(level, p, DexSounds.HENSHIN, 1.2F, 0.9F);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.2F, 0.8F);
            p.sendSystemMessage(Component.translatable("message.dexrider.upgrade_on"), true);
            DexAdvancements.award(p, "upgrade_form");
        }
        Transformation.applyFormEffects(p);
    }

    /** Llamado al despertar una Emotion: con las dos, el Driver forja la carta. */
    public static void onEmotion(ServerPlayer p, String emotion) {
        ItemStack driver = DexDriverItem.findDriver(p);
        if (driver.isEmpty()) return;
        Integer seen = driver.get(DexComponents.EMOTIONS_SEEN.get());
        int bits = (seen == null ? 0 : seen) | (DexEmotions.VIOLENT.equals(emotion) ? 1 : 2);
        driver.set(DexComponents.EMOTIONS_SEEN.get(), bits);
        DriverDeck deck = DexDriverItem.getDeck(driver);
        if (bits != 3 || deck.has(DexCard.UPGRADE_FORM)) return;
        DexDriverItem.setDeck(driver, deck.withCard(DexCard.UPGRADE_FORM));
        ItemStack card = new ItemStack(DexItems.card(DexCard.UPGRADE_FORM).get());
        if (!p.getInventory().add(card)) p.drop(card, false);
        p.sendSystemMessage(Component.translatable("message.dexrider.upgrade_forged").withStyle(ChatFormatting.RED));
    }
}
