package com.draxer.dexrider.rider;

import com.draxer.dexrider.registry.DexSounds;
import com.draxer.dexrider.item.DexDriverItem;
import com.draxer.dexrider.item.SuitConfig;
import com.draxer.dexrider.registry.DexComponents;
import com.draxer.dexrider.registry.DexItems;
import com.draxer.dexrider.vehicle.DexMachineEntity;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.List;

/**
 * Transformacion y destransformacion.
 *
 * La armadura original del jugador se guarda DENTRO del peto de Dex (componente CONTAINER),
 * asi no hacen falta datos extra en el jugador y sobrevive a cerrar el mundo.
 */
public final class Transformation {
    private Transformation() {}

    /** Orden fijo en que se guardan las piezas originales dentro del peto. */
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD
    };

    // Efectos de la Dex Form base (mejora moderada: "nivel estudiante fuerte").
    private static final List<Holder<MobEffect>> FORM_EFFECTS =
            List.of(MobEffects.STRENGTH, MobEffects.SPEED, MobEffects.JUMP_BOOST, MobEffects.RESISTANCE);

    public static boolean isTransformed(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(DexItems.DEX_HELMET.get())
                && player.getItemBySlot(EquipmentSlot.CHEST).is(DexItems.DEX_CHESTPLATE.get())
                && player.getItemBySlot(EquipmentSlot.LEGS).is(DexItems.DEX_LEGGINGS.get())
                && player.getItemBySlot(EquipmentSlot.FEET).is(DexItems.DEX_BOOTS.get());
    }

    /** Lo que hace la tecla V. */
    public static void toggle(ServerPlayer player) {
        if (RiderKick.isKicking(player) || Scheduler.busy(player)) return;
        if (isTransformed(player)) {
            detransform(player, true);
        } else {
            tryTransform(player);
        }
    }

    private static void tryTransform(ServerPlayer player) {
        ItemStack driver = DexDriverItem.findDriver(player);
        if (driver.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.dexrider.need_driver"), true);
            return;
        }
        if (!"kamen_ride_dex".equals(driver.get(DexComponents.LOADED_CARD.get()))) {
            player.sendSystemMessage(Component.translatable("message.dexrider.need_card"), true);
            return;
        }
        transform(player);
        // La carta vuelve al mazo al transformarse; para la proxima vez hay que insertarla de nuevo.
        driver.remove(DexComponents.LOADED_CARD.get());
    }

    private static void transform(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();

        // 1. Guardar la armadura que llevaba.
        List<ItemStack> originals = new ArrayList<>();
        for (EquipmentSlot slot : SLOTS) {
            originals.add(player.getItemBySlot(slot).copy());
        }

        // 2. Crear las piezas de Dex. Curse of Binding impide quitarlas en supervivencia
        //    y Curse of Vanishing evita que caigan al morir.
        ItemStack helmet = sealed(level, new ItemStack(DexItems.DEX_HELMET.get()));
        ItemStack chest = sealed(level, new ItemStack(DexItems.DEX_CHESTPLATE.get()));
        ItemStack legs = sealed(level, new ItemStack(DexItems.DEX_LEGGINGS.get()));
        ItemStack boots = sealed(level, new ItemStack(DexItems.DEX_BOOTS.get()));
        chest.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(originals));
        // personalizacion del traje: viaja en el peto para que todos la vean
        ItemStack driverForSuit = DexDriverItem.findDriver(player);
        SuitConfig suit = driverForSuit.isEmpty() ? null : driverForSuit.get(DexComponents.SUIT.get());
        chest.set(DexComponents.SUIT.get(), suit != null ? suit : SuitConfig.DEFAULT);

        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        player.setItemSlot(EquipmentSlot.LEGS, legs);
        player.setItemSlot(EquipmentSlot.FEET, boots);

        applyFormEffects(player);

        // 3. Espectaculo: anillo de "cartas" magenta y dorado que se cierra sobre el jugador.
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                DexSounds.HENSHIN.get(), SoundSource.PLAYERS, 1.2F, 1.0F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                DexSounds.VOICE_KAMEN_RIDE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        FX.ring(level, player, FX.MAGENTA, 1.6, 9);
        FX.ring(level, player, FX.GOLD, 1.1, 9);

        player.sendSystemMessage(Component.translatable("message.dexrider.kamen_ride_dex"), true);
    }

    /**
     * Quita la armadura de Dex y devuelve la original.
     * Busca el peto (con la armadura guardada) en las ranuras de armadura, el inventario y el cursor,
     * por si algo raro paso (modo creativo, comandos, etc.).
     */
    public static void detransform(ServerPlayer player, boolean withEffects) {
        removeSuitBody(player);
        CipherModes.clear(player);
        RiderKick.cancel(player);
        Scheduler.cancel(player);
        ArmedRide.removeWeapons(player);
        if (player.getItemBySlot(EquipmentSlot.CHEST).is(DexItems.DEX_CHESTPLATE.get())) FormRide.clearForm(player);
        FormRide.revokeFlight(player);
        RiderPowers.clear(player);
        DexMachineEntity.dismissFor(player);
        List<ItemStack> originals = new ArrayList<>();

        // Ranuras de armadura
        for (EquipmentSlot slot : SLOTS) {
            ItemStack worn = player.getItemBySlot(slot);
            if (DexItems.isDexArmor(worn.getItem())) {
                collectOriginals(worn, originals);
                player.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        // Inventario
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (DexItems.isDexArmor(stack.getItem())) {
                collectOriginals(stack, originals);
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        // Cursor (pieza agarrada con el raton)
        ItemStack carried = player.containerMenu.getCarried();
        if (DexItems.isDexArmor(carried.getItem())) {
            collectOriginals(carried, originals);
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }

        // Devolver cada pieza original a su ranura; si esta ocupada, al inventario.
        for (int i = 0; i < originals.size() && i < SLOTS.length; i++) {
            ItemStack original = originals.get(i);
            if (original.isEmpty()) continue;
            if (player.getItemBySlot(SLOTS[i]).isEmpty()) {
                player.setItemSlot(SLOTS[i], original);
            } else if (!player.getInventory().add(original)) {
                player.drop(original, false);
            }
        }

        for (Holder<MobEffect> effect : FORM_EFFECTS) {
            player.removeEffect(effect);
        }

        if (withEffects) {
            ServerLevel level = (ServerLevel) player.level();
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    DexSounds.DETRANSFORM.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            FX.ring(level, player, FX.MAGENTA, 0.8, 5);
            player.sendSystemMessage(Component.translatable("message.dexrider.detransform"), true);
        }
    }

    // ------------------------------------------------------------------ el cuerpo del traje (cualquier identidad)
    /** Dano recibido transformado (el traje absorbe el 25 %). */
    public static final float SUIT_DAMAGE_TAKEN = 0.75F;
    /** Dano de tus golpes transformado (+25 %). */
    public static final float SUIT_DAMAGE_DEALT = 1.25F;
    private static final net.minecraft.resources.Identifier SUIT_HEALTH_ID = com.draxer.dexrider.DexRider.id("suit_health");

    /** +5 corazones de vida maxima mientras estas transformado, y recuperacion lenta fuera de combate. */
    private static void suitBody(ServerPlayer player) {
        var health = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (health != null && health.getModifier(SUIT_HEALTH_ID) == null) {
            health.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(SUIT_HEALTH_ID, 10.0,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
        }
        // el traje cierra heridas leves si llevas 5 s sin recibir golpes
        if (player.tickCount - player.getLastHurtByMobTimestamp() > 100 && player.getHealth() < player.getMaxHealth()) {
            player.heal(1.0F);
        }
    }

    public static void removeSuitBody(ServerPlayer player) {
        var health = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (health == null || health.getModifier(SUIT_HEALTH_ID) == null) return;
        health.removeModifier(SUIT_HEALTH_ID);
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    /** Se llama cada 20 ticks mientras estas transformado. */
    public static void applyFormEffects(ServerPlayer player) {
        suitBody(player);
        KamenRide identity = KamenRide.current(player);
        if (identity != KamenRide.DEX) {
            RiderPowers.applyEffects(player, identity);
            return;
        }
        FormRide form = FormRide.current(player);
        if (form != null) {
            form.applyEffects(player, FormRide.level(player, form));
            return;
        }
        for (Holder<MobEffect> effect : FORM_EFFECTS) {
            // Dex Form base: Fuerza II, Velocidad I, Salto I y Resistencia I; sin particulas, con icono
            player.addEffect(new MobEffectInstance(effect, 60, effect == MobEffects.STRENGTH ? 1 : 0, false, false, true));
        }
    }

    private static void collectOriginals(ItemStack dexPiece, List<ItemStack> out) {
        ItemContainerContents contents = dexPiece.get(DataComponents.CONTAINER);
        if (contents == null || !out.isEmpty()) return;
        NonNullList<ItemStack> list = NonNullList.withSize(SLOTS.length, ItemStack.EMPTY);
        contents.copyInto(list);
        out.addAll(list);
    }

    private static ItemStack sealed(ServerLevel level, ItemStack stack) {
        var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Holder<Enchantment> binding = enchantments.getOrThrow(Enchantments.BINDING_CURSE);
        Holder<Enchantment> vanishing = enchantments.getOrThrow(Enchantments.VANISHING_CURSE);
        stack.enchant(binding, 1);
        stack.enchant(vanishing, 1);
        // Oculta el brillo de encantamiento para que la armadura se vea limpia.
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
        return stack;
    }
}
