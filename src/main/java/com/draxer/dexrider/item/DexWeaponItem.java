package com.draxer.dexrider.item;

import com.draxer.dexrider.registry.DexSounds;
import com.draxer.dexrider.registry.DexComponents;
import com.draxer.dexrider.rider.FX;
import com.draxer.dexrider.rider.RiderWeapons;
import com.draxer.dexrider.rider.Transformation;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Arma materializada por una carta Armed Ride. Solo existe mientras Dex esta transformado.
 *
 *  Ride Booker  : espada; Shift + clic derecho cambia a pistola (clic derecho dispara).
 *  Shock Baton  : golpes electricos que ralentizan (efecto en DexEvents).
 *  Blast Shot   : clic derecho dispara.
 *  Sword        : la de mas dano cuerpo a cuerpo.
 *  Volt Chain   : clic derecho lanza la cadena y atrae al primer objetivo.
 *  Power Knuckle: golpes lentos y muy pesados con mucho empuje (efecto en DexEvents).
 *
 *  Armas de los Riders (se escanean viendo al duenio pelear con ellas):
 *  Cipher Edge   : espada; clic derecho = corte de codigo (avance corto y corte)
 *  Prism Edge    : hoja espejada; clic derecho = destello que ciega en cono
 *  Module Driver : clic derecho dispara el modulo cargado; Shift + clic derecho cambia (taladro / gancho / soplete)
 *  Forge Lance   : lanza; clic derecho = estocada con avance
 *  Warden Baton  : porra; los golpes inmovilizan un instante; clic derecho = contencion a un objetivo
 *  Orga Halberd  : alabarda lenta y demoledora; clic derecho = barrido en arco que empuja
 *  Spore Rod     : no corta; clic derecho = estallido de esporas; Shift + clic derecho = clavarla (la red sensorial
 *                  revela todo en 48 bloques)
 */
public class DexWeaponItem extends Item {

    public enum WeaponKind {
        RIDE_BOOKER, SHOCK_BATON, BLAST_SHOT, SWORD, VOLT_CHAIN, POWER_KNUCKLE,
        // armas de los Riders
        CIPHER_EDGE, PRISM_EDGE, MODULE_DRIVER, FORGE_LANCE, WARDEN_BATON, ORGA_HALBERD, SPORE_ROD, KAIXA_GUNBLADE
    }

    private final WeaponKind kind;

    public DexWeaponItem(Properties properties, WeaponKind kind) {
        super(properties);
        this.kind = kind;
    }

    public WeaponKind kind() {
        return kind;
    }

    public static boolean isGunMode(ItemStack stack) {
        return stack.has(DexComponents.GUN_MODE.get());
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
            return kind == WeaponKind.SHOCK_BATON || kind == WeaponKind.SWORD || kind == WeaponKind.POWER_KNUCKLE
                    ? InteractionResult.PASS : InteractionResult.SUCCESS;
        }
        if (!Transformation.isTransformed(sp) || sp.getCooldowns().isOnCooldown(stack)) return InteractionResult.PASS;

        switch (kind) {
            case RIDE_BOOKER -> {
                if (sp.isShiftKeyDown()) {
                    toggleMode(server, sp, stack);
                } else if (isGunMode(stack)) {
                    shoot(server, sp, 7.0F, FX.MAGENTA);
                    sp.getCooldowns().addCooldown(stack, 6);
                } else {
                    return InteractionResult.PASS;
                }
            }
            case BLAST_SHOT -> {
                shoot(server, sp, 6.0F, FX.RED);
                sp.getCooldowns().addCooldown(stack, 8);
            }
            case VOLT_CHAIN -> {
                lash(server, sp);
                sp.getCooldowns().addCooldown(stack, 20);
            }
            case CIPHER_EDGE, PRISM_EDGE, MODULE_DRIVER, FORGE_LANCE, WARDEN_BATON, ORGA_HALBERD, SPORE_ROD, KAIXA_GUNBLADE -> {
                int cd = RiderWeapons.use(server, sp, kind, stack);
                if (cd <= 0) return InteractionResult.PASS;
                sp.getCooldowns().addCooldown(stack, cd);
            }
            default -> {
                return InteractionResult.PASS;
            }
        }
        return InteractionResult.SUCCESS;
    }

    private static void toggleMode(ServerLevel level, ServerPlayer player, ItemStack stack) {
        boolean toGun = !isGunMode(stack);
        if (toGun) stack.set(DexComponents.GUN_MODE.get(), true);
        else stack.remove(DexComponents.GUN_MODE.get());
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                DexSounds.MODE_SWITCH.get(), SoundSource.PLAYERS, 1.0F, toGun ? 1.2F : 0.9F);
        player.sendSystemMessage(Component.translatable(toGun
                ? "message.dexrider.booker_gun" : "message.dexrider.booker_sword"), true);
    }

    private static void shoot(ServerLevel level, ServerPlayer player, float damage, int color) {
        FX.beam(level, player, player.getLookAngle(), 32.0, damage, color, 1.0F, false);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                DexSounds.SHOT.get(), SoundSource.PLAYERS, 0.9F, 1.0F);
    }

    /** Cadena: golpea al primero en 9 bloques y lo arrastra hacia Dex. */
    private static void lash(ServerLevel level, ServerPlayer player) {
        Vec3 start = player.getEyePosition().add(0, -0.2, 0);
        Vec3 end = FX.rayEnd(level, player, start, player.getLookAngle(), 9.0);
        List<LivingEntity> hits = FX.alongLine(level, player, start, end, 0.5);
        Vec3 tip = hits.isEmpty() ? end : FX.center(hits.get(0));
        FX.line(level, start, tip, FX.GOLD, 0.8F, 0.3);
        FX.line(level, start, tip, FX.VOLT, 0.5F, 0.6);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 1.2F, 0.6F);
        if (!hits.isEmpty()) {
            LivingEntity target = hits.get(0);
            FX.hit(level, player, target, 6.0F);
            Vec3 pull = player.position().subtract(target.position()).normalize().scale(1.1);
            target.setDeltaMovement(pull.x, 0.35, pull.z);
            target.hurtMarked = true;
            FX.particle(level, ParticleTypes.ELECTRIC_SPARK, FX.center(target), 15, 0.3, 0.2);
        }
    }
}
