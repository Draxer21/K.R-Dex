package com.draxer.dexrider.rider;

import com.draxer.dexrider.item.DexWeaponItem;
import com.draxer.dexrider.item.DexWeaponItem.WeaponKind;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Movesets por arma (0.56): en vez de un golpe que se repite, cada arma (y Dex sin arma) tiene un juego entero:
 *
 *  - CADENA LIGERA: 4-5 golpes seguidos (clic izquierdo con el golpe cargado); el ultimo es un remate de la cadena.
 *  - GOLPE PESADO: atacar con Shift pulsado (gasta aguante).
 *  - EN CARRERA: atacar corriendo (gasta aguante).
 *  - AEREO: atacar cayendo (gasta aguante).
 *  - TRAS ESQUIVA: el primer golpe justo despues de esquivar (tecla de esquiva).
 *  - CONTRAATAQUE: el golpe tras una parada perfecta (Guard).
 *
 * La logica es del servidor (forma, alcance, empuje, efectos); la animacion (Player Animation Library) es solo
 * cosmetica. El dano base es el del arma: cada movimiento lo multiplica y los golpes extra (arco, estocada, anillo)
 * usan el mismo dano.
 */
public final class Moveset {
    private Moveset() {}

    public enum Shape { SINGLE, ARC, THRUST, RING, SLAM }

    public enum Fx { SHOCK, STUN, LAUNCH, PULL, BURST, DOUBLE, FLASH, SPORE, SHOT, DASH, DIVE, KNOCKDOWN, BURN, CARDS }

    public enum Kind { LIGHT, HEAVY, DASH, AIR, EVADE, COUNTER }

    public record Move(String id, float mult, Shape shape, float reach, float arc, float knock, float lift,
                       String anim, int color, EnumSet<Fx> fx) {
        public String key(String set) { return "move.dexrider." + set + "." + id; }
    }

    public record Set(String id, List<Move> light, Move heavy, Move dash, Move air, Move evade, Move counter, SoundEvent swing) {
        public Move of(Kind k) {
            return switch (k) {
                case HEAVY -> heavy; case DASH -> dash; case AIR -> air; case EVADE -> evade; case COUNTER -> counter;
                default -> light.get(0);
            };
        }
    }

    public static final int COST_HEAVY = 25, COST_DASH = 15, COST_AIR = 15;

    private static Move m(String id, float mult, Shape shape, float reach, float arc, float knock, float lift, String anim, int color, Fx... fx) {
        EnumSet<Fx> s = EnumSet.noneOf(Fx.class);
        for (Fx f : fx) s.add(f);
        return new Move(id, mult, shape, reach, arc, knock, lift, anim, color, s);
    }

    // ---------------------------------------------------------------- los juegos
    static final Map<WeaponKind, Set> SETS = new EnumMap<>(WeaponKind.class);
    static final Set UNARMED, BOOKER_GUN;
    static {
        int M = FX.MAGENTA, G = FX.GOLD, R = FX.RED, W = FX.WHITE, V = FX.VOLT;
        UNARMED = new Set("dex", List.of(
                m("jab", 0.9F, Shape.SINGLE, 0, 0, 0.2F, 0, "m_jab", M),
                m("cross", 1.0F, Shape.SINGLE, 0, 0, 0.3F, 0, "m_cross", M),
                m("high_kick", 1.15F, Shape.ARC, 2.6F, 90, 0.5F, 0.15F, "m_kick_high", M),
                m("dex_rush", 1.5F, Shape.RING, 2.4F, 360, 0.9F, 0.25F, "m_kick_spin", G, Fx.DOUBLE, Fx.CARDS)),
                m("rider_punch", 2.2F, Shape.SINGLE, 0, 0, 1.6F, 0.3F, "m_heavy", G, Fx.BURST),
                m("flying_knee", 1.6F, Shape.THRUST, 3.5F, 0, 1.0F, 0.35F, "m_dash", M, Fx.DASH),
                m("drop_kick", 1.8F, Shape.SLAM, 2.8F, 360, 0.8F, 0.4F, "m_air", G, Fx.DIVE),
                m("low_sweep", 1.2F, Shape.RING, 2.6F, 360, 0.3F, 0, "m_evade", M, Fx.KNOCKDOWN),
                m("palm_strike", 1.4F, Shape.SINGLE, 0, 0, 1.4F, 0.2F, "m_counter", W, Fx.STUN),
                SoundEvents.PLAYER_ATTACK_STRONG);
        SETS.put(WeaponKind.RIDE_BOOKER, new Set("ride_booker", List.of(
                m("cut", 1.0F, Shape.ARC, 3.0F, 100, 0.25F, 0, "m_slash_r", M),
                m("return_cut", 1.05F, Shape.ARC, 3.0F, 100, 0.3F, 0, "m_slash_l", M),
                m("card_thrust", 1.15F, Shape.THRUST, 3.6F, 0, 0.5F, 0, "m_thrust", M),
                m("rising", 1.2F, Shape.SINGLE, 0, 0, 0.2F, 0.7F, "m_slash_up", G, Fx.LAUNCH),
                m("deck_cut", 1.6F, Shape.ARC, 3.4F, 160, 0.9F, 0.2F, "m_spin", G, Fx.CARDS, Fx.BURST)),
                m("book_cleave", 2.1F, Shape.ARC, 3.2F, 70, 1.3F, 0.2F, "m_slash_down", G, Fx.BURST),
                m("page_rush", 1.5F, Shape.THRUST, 4.5F, 0, 0.8F, 0.1F, "m_dash", M, Fx.DASH),
                m("falling_page", 1.7F, Shape.SLAM, 2.6F, 360, 0.6F, 0.2F, "m_air", M, Fx.DIVE),
                m("turn_cut", 1.3F, Shape.RING, 2.8F, 360, 0.5F, 0, "m_evade", M),
                m("bookmark", 1.5F, Shape.SINGLE, 0, 0, 1.0F, 0.2F, "m_counter", W, Fx.STUN, Fx.CARDS),
                SoundEvents.PLAYER_ATTACK_SWEEP));
        BOOKER_GUN = new Set("booker_gun", List.of(
                m("grip_bash", 0.8F, Shape.SINGLE, 0, 0, 0.4F, 0, "m_cross", M),
                m("close_shot", 0.9F, Shape.SINGLE, 0, 0, 0.6F, 0, "m_shot", M, Fx.SHOT),
                m("turn_bash", 0.9F, Shape.ARC, 2.6F, 100, 0.5F, 0, "m_slash_l", M),
                m("point_blank", 1.4F, Shape.SINGLE, 0, 0, 1.3F, 0.15F, "m_shot", G, Fx.SHOT, Fx.BURST)),
                m("charged_page", 1.6F, Shape.SINGLE, 0, 0, 1.0F, 0.1F, "m_shot", G, Fx.SHOT, Fx.BURST),
                m("slide_shot", 1.2F, Shape.SINGLE, 0, 0, 0.7F, 0, "m_dash", M, Fx.DASH, Fx.SHOT),
                m("rain_shot", 1.3F, Shape.SLAM, 2.6F, 360, 0.5F, 0, "m_air", M, Fx.SHOT),
                m("roll_shot", 1.2F, Shape.SINGLE, 0, 0, 0.6F, 0, "m_evade", M, Fx.SHOT),
                m("gun_kata", 1.5F, Shape.SINGLE, 0, 0, 1.2F, 0.2F, "m_counter", W, Fx.SHOT, Fx.STUN),
                SoundEvents.PLAYER_ATTACK_KNOCKBACK);
        SETS.put(WeaponKind.SHOCK_BATON, new Set("shock_baton", List.of(
                m("tap", 0.85F, Shape.SINGLE, 0, 0, 0.15F, 0, "m_jab", V, Fx.SHOCK),
                m("tap_back", 0.85F, Shape.SINGLE, 0, 0, 0.15F, 0, "m_slash_l", V, Fx.SHOCK),
                m("crack", 1.0F, Shape.ARC, 2.6F, 90, 0.3F, 0, "m_slash_r", V, Fx.SHOCK),
                m("jolt", 1.05F, Shape.SINGLE, 0, 0, 0.3F, 0.1F, "m_thrust", V, Fx.SHOCK, Fx.DOUBLE),
                m("discharge", 1.4F, Shape.RING, 3.0F, 360, 0.7F, 0.1F, "m_slash_down", V, Fx.STUN, Fx.BURST)),
                m("overload", 1.9F, Shape.SINGLE, 0, 0, 1.0F, 0.2F, "m_heavy", V, Fx.STUN, Fx.BURST),
                m("spark_dash", 1.3F, Shape.THRUST, 4.0F, 0, 0.5F, 0, "m_dash", V, Fx.DASH, Fx.SHOCK),
                m("ground_bolt", 1.5F, Shape.SLAM, 3.0F, 360, 0.4F, 0.1F, "m_air", V, Fx.DIVE, Fx.SHOCK),
                m("static_spin", 1.2F, Shape.RING, 2.6F, 360, 0.4F, 0, "m_evade", V, Fx.SHOCK),
                m("ground_out", 1.4F, Shape.SINGLE, 0, 0, 0.6F, 0, "m_counter", V, Fx.STUN, Fx.SHOCK),
                SoundEvents.PLAYER_ATTACK_SWEEP));
        Set gun = new Set("blast_shot", List.of(
                m("butt_strike", 0.8F, Shape.SINGLE, 0, 0, 0.4F, 0, "m_cross", R),
                m("hip_shot", 0.9F, Shape.SINGLE, 0, 0, 0.5F, 0, "m_shot", R, Fx.SHOT),
                m("knee_shot", 1.0F, Shape.SINGLE, 0, 0, 0.6F, 0.1F, "m_kick_high", R, Fx.SHOT),
                m("full_burst_kata", 1.5F, Shape.ARC, 3.0F, 120, 1.0F, 0.15F, "m_spin", G, Fx.SHOT, Fx.BURST)),
                m("heavy_round", 1.8F, Shape.SINGLE, 0, 0, 1.4F, 0.2F, "m_shot", G, Fx.SHOT, Fx.BURST),
                m("run_and_gun", 1.2F, Shape.SINGLE, 0, 0, 0.8F, 0, "m_dash", R, Fx.DASH, Fx.SHOT),
                m("down_volley", 1.4F, Shape.SLAM, 2.8F, 360, 0.5F, 0, "m_air", R, Fx.SHOT, Fx.DIVE),
                m("roll_shot", 1.2F, Shape.SINGLE, 0, 0, 0.6F, 0, "m_evade", R, Fx.SHOT),
                m("muzzle_check", 1.5F, Shape.SINGLE, 0, 0, 1.3F, 0.2F, "m_counter", W, Fx.SHOT, Fx.STUN),
                SoundEvents.PLAYER_ATTACK_KNOCKBACK);
        SETS.put(WeaponKind.BLAST_SHOT, gun);
        SETS.put(WeaponKind.SWORD, new Set("sword", List.of(
                m("draw_cut", 1.0F, Shape.ARC, 3.2F, 110, 0.3F, 0, "m_slash_r", R),
                m("cross_cut", 1.05F, Shape.ARC, 3.2F, 110, 0.3F, 0, "m_slash_l", R),
                m("overhead", 1.2F, Shape.ARC, 3.0F, 60, 0.6F, 0, "m_slash_down", R),
                m("cross_slash", 1.7F, Shape.ARC, 3.6F, 150, 1.0F, 0.2F, "m_spin", G, Fx.DOUBLE, Fx.BURST)),
                m("great_cleave", 2.3F, Shape.ARC, 3.4F, 80, 1.4F, 0.2F, "m_slash_down", G, Fx.BURST),
                m("lunge_cut", 1.6F, Shape.THRUST, 4.5F, 0, 0.8F, 0, "m_dash", R, Fx.DASH),
                m("helm_splitter", 1.9F, Shape.SLAM, 2.6F, 360, 0.7F, 0.3F, "m_air", G, Fx.DIVE),
                m("wheel_cut", 1.3F, Shape.RING, 3.0F, 360, 0.5F, 0, "m_evade", R),
                m("riposte", 1.6F, Shape.SINGLE, 0, 0, 1.0F, 0.2F, "m_counter", W, Fx.STUN, Fx.DOUBLE),
                SoundEvents.PLAYER_ATTACK_SWEEP));
        SETS.put(WeaponKind.VOLT_CHAIN, new Set("volt_chain", List.of(
                m("lash", 0.9F, Shape.THRUST, 4.5F, 0, 0.2F, 0, "m_whip", V, Fx.SHOCK),
                m("back_lash", 0.9F, Shape.THRUST, 4.5F, 0, 0.2F, 0, "m_slash_l", V, Fx.SHOCK),
                m("coil", 1.0F, Shape.SINGLE, 0, 0, 0, 0, "m_whip", G, Fx.PULL),
                m("volt_wheel", 1.4F, Shape.RING, 4.5F, 360, 0.8F, 0.2F, "m_spin", V, Fx.SHOCK, Fx.BURST)),
                m("chain_slam", 1.9F, Shape.THRUST, 5.0F, 0, 0.4F, 0.6F, "m_slash_down", V, Fx.LAUNCH, Fx.SHOCK),
                m("drag_run", 1.2F, Shape.THRUST, 5.5F, 0, 0, 0, "m_dash", V, Fx.PULL, Fx.DASH),
                m("sky_lash", 1.4F, Shape.SLAM, 3.5F, 360, 0.3F, 0, "m_air", V, Fx.SHOCK, Fx.DIVE),
                m("snap_back", 1.2F, Shape.RING, 4.0F, 360, 0.5F, 0, "m_evade", V, Fx.SHOCK),
                m("bind", 1.3F, Shape.SINGLE, 0, 0, 0, 0, "m_counter", W, Fx.STUN, Fx.PULL),
                SoundEvents.PLAYER_ATTACK_SWEEP));
        SETS.put(WeaponKind.POWER_KNUCKLE, new Set("power_knuckle", List.of(
                m("hook", 1.0F, Shape.SINGLE, 0, 0, 0.7F, 0, "m_jab", G),
                m("body_blow", 1.1F, Shape.SINGLE, 0, 0, 0.8F, 0.1F, "m_cross", G),
                m("uppercut", 1.25F, Shape.SINGLE, 0, 0, 0.3F, 0.8F, "m_slash_up", G, Fx.LAUNCH),
                m("double_impact", 1.9F, Shape.ARC, 3.0F, 70, 1.8F, 0.3F, "m_heavy", G, Fx.BURST, Fx.DOUBLE)),
                m("crusher", 2.5F, Shape.SINGLE, 0, 0, 2.2F, 0.3F, "m_heavy", G, Fx.BURST),
                m("shoulder_charge", 1.7F, Shape.THRUST, 3.5F, 0, 1.6F, 0.2F, "m_dash", G, Fx.DASH),
                m("meteor_fist", 2.0F, Shape.SLAM, 3.4F, 360, 1.2F, 0.4F, "m_air", G, Fx.DIVE, Fx.BURST),
                m("pivot_hook", 1.4F, Shape.SINGLE, 0, 0, 1.2F, 0, "m_evade", G),
                m("cross_counter", 1.8F, Shape.SINGLE, 0, 0, 1.8F, 0.2F, "m_counter", W, Fx.STUN, Fx.BURST),
                SoundEvents.PLAYER_ATTACK_KNOCKBACK));
        SETS.put(WeaponKind.CIPHER_EDGE, new Set("cipher_edge", List.of(
                m("code_cut", 0.9F, Shape.ARC, 3.0F, 90, 0.2F, 0, "m_slash_r", 0x1E6CFF),
                m("code_return", 0.9F, Shape.ARC, 3.0F, 90, 0.2F, 0, "m_slash_l", 0x1E6CFF),
                m("parse", 1.0F, Shape.THRUST, 3.4F, 0, 0.3F, 0, "m_thrust", 0x1E6CFF),
                m("compile", 1.05F, Shape.ARC, 3.0F, 120, 0.3F, 0, "m_slash_r", 0x1E6CFF, Fx.DOUBLE),
                m("execute", 1.5F, Shape.ARC, 3.4F, 160, 0.9F, 0.2F, "m_spin", R, Fx.BURST, Fx.DOUBLE)),
                m("hard_reset", 2.0F, Shape.ARC, 3.0F, 70, 1.2F, 0.2F, "m_slash_down", R, Fx.BURST),
                m("start_up", 1.5F, Shape.THRUST, 5.0F, 0, 0.6F, 0, "m_dash", 0x1E6CFF, Fx.DASH, Fx.DOUBLE),
                m("crimson_drop", 1.7F, Shape.SLAM, 2.8F, 360, 0.6F, 0.2F, "m_air", R, Fx.DIVE),
                m("seven_back", 1.3F, Shape.RING, 2.8F, 360, 0.4F, 0, "m_evade", 0x1E6CFF),
                m("patch", 1.5F, Shape.SINGLE, 0, 0, 0.9F, 0.2F, "m_counter", W, Fx.STUN, Fx.DOUBLE),
                SoundEvents.PLAYER_ATTACK_SWEEP));
        SETS.put(WeaponKind.PRISM_EDGE, new Set("prism_edge", List.of(
                m("glint", 0.85F, Shape.THRUST, 3.4F, 0, 0.15F, 0, "m_thrust", W),
                m("refract", 0.9F, Shape.ARC, 2.8F, 80, 0.2F, 0, "m_slash_r", W),
                m("glint_twice", 0.95F, Shape.THRUST, 3.4F, 0, 0.2F, 0, "m_thrust", W, Fx.DOUBLE),
                m("mirror_turn", 1.0F, Shape.ARC, 2.8F, 120, 0.3F, 0, "m_slash_l", W),
                m("full_spectrum", 1.45F, Shape.THRUST, 4.2F, 0, 0.8F, 0.1F, "m_dash", 0xA060FF, Fx.FLASH, Fx.BURST)),
                m("focus", 2.0F, Shape.THRUST, 4.0F, 0, 1.0F, 0, "m_thrust", 0xA060FF, Fx.FLASH),
                m("light_step", 1.5F, Shape.THRUST, 5.0F, 0, 0.5F, 0, "m_dash", W, Fx.DASH, Fx.FLASH),
                m("falling_star", 1.6F, Shape.SLAM, 2.6F, 360, 0.5F, 0.1F, "m_air", W, Fx.DIVE, Fx.FLASH),
                m("afterimage", 1.4F, Shape.SINGLE, 0, 0, 0.6F, 0, "m_evade", W, Fx.DOUBLE),
                m("reflection", 1.6F, Shape.SINGLE, 0, 0, 1.0F, 0.1F, "m_counter", W, Fx.FLASH, Fx.STUN),
                SoundEvents.PLAYER_ATTACK_SWEEP));
        SETS.put(WeaponKind.MODULE_DRIVER, new Set("module_driver", List.of(
                m("wrench", 0.9F, Shape.SINGLE, 0, 0, 0.4F, 0, "m_slash_r", 0xFF8C1E),
                m("ratchet", 0.95F, Shape.SINGLE, 0, 0, 0.4F, 0, "m_slash_l", 0xFF8C1E),
                m("rivet", 1.05F, Shape.THRUST, 3.2F, 0, 0.5F, 0, "m_thrust", 0xFF8C1E, Fx.DOUBLE),
                m("drill_finish", 1.5F, Shape.THRUST, 3.6F, 0, 1.0F, 0.1F, "m_heavy", 0xFF8C1E, Fx.DOUBLE, Fx.BURST)),
                m("torch_blast", 1.9F, Shape.ARC, 3.0F, 70, 1.0F, 0.1F, "m_heavy", 0xFF8C1E, Fx.BURN, Fx.BURST),
                m("thruster_ram", 1.5F, Shape.THRUST, 5.0F, 0, 1.2F, 0.2F, "m_dash", 0xFF8C1E, Fx.DASH),
                m("anchor_drop", 1.7F, Shape.SLAM, 3.0F, 360, 0.7F, 0.3F, "m_air", 0xFF8C1E, Fx.DIVE),
                m("hook_swing", 1.3F, Shape.RING, 3.0F, 360, 0.5F, 0, "m_evade", 0xFF8C1E, Fx.PULL),
                m("prototype", 1.5F, Shape.SINGLE, 0, 0, 1.0F, 0.2F, "m_counter", W, Fx.STUN, Fx.BURN),
                SoundEvents.PLAYER_ATTACK_KNOCKBACK));
        SETS.put(WeaponKind.FORGE_LANCE, new Set("forge_lance", List.of(
                m("prod", 0.95F, Shape.THRUST, 4.2F, 0, 0.3F, 0, "m_thrust", 0xB0303A),
                m("prod_twice", 1.0F, Shape.THRUST, 4.2F, 0, 0.3F, 0, "m_thrust", 0xB0303A),
                m("haft_sweep", 1.1F, Shape.ARC, 3.6F, 140, 0.6F, 0, "m_slash_r", 0xB0303A),
                m("formula", 1.6F, Shape.THRUST, 5.2F, 0, 1.2F, 0.2F, "m_dash", G, Fx.BURST)),
                m("piercing_plan", 2.2F, Shape.THRUST, 5.0F, 0, 1.4F, 0, "m_thrust", G, Fx.BURST),
                m("cavalry", 1.7F, Shape.THRUST, 6.0F, 0, 1.2F, 0.1F, "m_dash", 0xB0303A, Fx.DASH),
                m("pole_vault", 1.7F, Shape.SLAM, 3.0F, 360, 0.8F, 0.3F, "m_air", 0xB0303A, Fx.DIVE),
                m("spin_guard", 1.2F, Shape.RING, 3.6F, 360, 0.6F, 0, "m_evade", 0xB0303A),
                m("concluded", 1.6F, Shape.THRUST, 4.5F, 0, 1.0F, 0.1F, "m_counter", W, Fx.STUN),
                SoundEvents.PLAYER_ATTACK_SWEEP));
        SETS.put(WeaponKind.WARDEN_BATON, new Set("warden_baton", List.of(
                m("check", 0.9F, Shape.SINGLE, 0, 0, 0.2F, 0, "m_jab", 0x5A6A80),
                m("block_strike", 0.95F, Shape.SINGLE, 0, 0, 0.3F, 0, "m_slash_l", 0x5A6A80),
                m("joint_lock", 1.0F, Shape.SINGLE, 0, 0, 0, 0, "m_cross", 0x5A6A80, Fx.STUN),
                m("containment", 1.4F, Shape.RING, 3.0F, 360, 0.5F, 0, "m_slash_down", W, Fx.STUN, Fx.BURST)),
                m("detain", 1.8F, Shape.SINGLE, 0, 0, 0.4F, 0, "m_heavy", W, Fx.STUN, Fx.KNOCKDOWN),
                m("intercept", 1.4F, Shape.THRUST, 4.0F, 0, 0.6F, 0, "m_dash", 0x5A6A80, Fx.DASH, Fx.STUN),
                m("pin_down", 1.6F, Shape.SLAM, 2.6F, 360, 0.2F, 0, "m_air", 0x5A6A80, Fx.DIVE, Fx.KNOCKDOWN),
                m("perimeter", 1.2F, Shape.RING, 2.8F, 360, 0.4F, 0, "m_evade", 0x5A6A80, Fx.STUN),
                m("protocol", 1.5F, Shape.SINGLE, 0, 0, 0.6F, 0, "m_counter", W, Fx.STUN, Fx.KNOCKDOWN),
                SoundEvents.PLAYER_ATTACK_KNOCKBACK));
        SETS.put(WeaponKind.ORGA_HALBERD, new Set("orga_halberd", List.of(
                m("reap", 1.0F, Shape.ARC, 4.0F, 150, 0.8F, 0, "m_slash_r", G),
                m("return_reap", 1.05F, Shape.ARC, 4.0F, 150, 0.8F, 0, "m_slash_l", G),
                m("fifty_years", 1.8F, Shape.ARC, 4.4F, 200, 1.6F, 0.3F, "m_spin", G, Fx.BURST, Fx.KNOCKDOWN)),
                m("cross_of_orga", 2.6F, Shape.ARC, 4.0F, 80, 2.0F, 0.3F, "m_slash_down", G, Fx.BURST),
                m("old_charge", 1.8F, Shape.THRUST, 5.0F, 0, 1.6F, 0.2F, "m_dash", G, Fx.DASH),
                m("judgement", 2.1F, Shape.SLAM, 3.6F, 360, 1.2F, 0.4F, "m_air", G, Fx.DIVE, Fx.BURST),
                m("bad_knee", 1.4F, Shape.RING, 3.6F, 360, 0.8F, 0, "m_evade", G),
                m("read_the_tone", 1.9F, Shape.SINGLE, 0, 0, 1.6F, 0.2F, "m_counter", W, Fx.STUN, Fx.BURST),
                SoundEvents.PLAYER_ATTACK_SWEEP));
        SETS.put(WeaponKind.SPORE_ROD, new Set("spore_rod", List.of(
                m("tap", 0.8F, Shape.SINGLE, 0, 0, 0.5F, 0, "m_jab", 0xBCA8D6, Fx.SPORE),
                m("sweep", 0.85F, Shape.ARC, 3.0F, 120, 0.6F, 0, "m_slash_r", 0xBCA8D6, Fx.SPORE),
                m("trip", 0.9F, Shape.SINGLE, 0, 0, 0.2F, 0, "m_evade", 0xBCA8D6, Fx.KNOCKDOWN),
                m("bloom_ring", 1.2F, Shape.RING, 3.6F, 360, 0.9F, 0.1F, "m_slash_down", 0xBCA8D6, Fx.SPORE, Fx.BURST)),
                m("anchor", 1.5F, Shape.RING, 4.0F, 360, 0.4F, 0, "m_heavy", 0xBCA8D6, Fx.SPORE, Fx.STUN),
                m("mycelium_run", 1.2F, Shape.THRUST, 4.5F, 0, 0.6F, 0, "m_dash", 0xBCA8D6, Fx.DASH, Fx.SPORE),
                m("cap_drop", 1.3F, Shape.SLAM, 3.4F, 360, 0.5F, 0, "m_air", 0xBCA8D6, Fx.DIVE, Fx.SPORE),
                m("drift", 1.0F, Shape.RING, 3.0F, 360, 0.5F, 0, "m_evade", 0xBCA8D6, Fx.SPORE),
                m("enough", 1.3F, Shape.SINGLE, 0, 0, 0.8F, 0, "m_counter", W, Fx.SPORE, Fx.STUN),
                SoundEvents.PLAYER_ATTACK_WEAK));
        SETS.put(WeaponKind.KAIXA_GUNBLADE, new Set("kaixa_gunblade", List.of(
                m("x_cut", 0.95F, Shape.ARC, 3.0F, 100, 0.3F, 0, "m_slash_r", 0x385CBE),
                m("x_return", 1.0F, Shape.ARC, 3.0F, 100, 0.3F, 0, "m_slash_l", 0x385CBE),
                m("trigger_cut", 1.05F, Shape.SINGLE, 0, 0, 0.5F, 0, "m_shot", 0x385CBE, Fx.SHOT),
                m("little_brother", 1.1F, Shape.THRUST, 3.4F, 0, 0.4F, 0, "m_thrust", 0x385CBE),
                m("x_finish", 1.5F, Shape.ARC, 3.4F, 150, 0.9F, 0.2F, "m_spin", G, Fx.SHOT, Fx.BURST)),
                m("blade_round", 2.0F, Shape.ARC, 3.0F, 70, 1.2F, 0.2F, "m_slash_down", G, Fx.SHOT, Fx.BURST),
                m("sidecar", 1.5F, Shape.THRUST, 4.5F, 0, 0.8F, 0, "m_dash", 0x385CBE, Fx.DASH, Fx.SHOT),
                m("drop_shot", 1.6F, Shape.SLAM, 2.8F, 360, 0.6F, 0.2F, "m_air", 0x385CBE, Fx.DIVE, Fx.SHOT),
                m("read_back", 1.3F, Shape.RING, 2.8F, 360, 0.4F, 0, "m_evade", 0x385CBE),
                m("taught_by_dad", 1.6F, Shape.SINGLE, 0, 0, 1.0F, 0.2F, "m_counter", W, Fx.STUN, Fx.SHOT),
                SoundEvents.PLAYER_ATTACK_SWEEP));
    }

    @Nullable
    public static Set setFor(ServerPlayer p) {
        ItemStack main = p.getMainHandItem();
        if (main.getItem() instanceof DexWeaponItem w) {
            if (w.kind() == WeaponKind.RIDE_BOOKER && DexWeaponItem.isGunMode(main)) return BOOKER_GUN;
            return SETS.get(w.kind());
        }
        return main.isEmpty() ? UNARMED : null;
    }

    public static Set set(WeaponKind k) { return SETS.get(k); }

    // ---------------------------------------------------------------- estado por jugador
    static final class State {
        String set = "";
        int step = -1;
        long lastHit = -1000;
        long lastAttack = -1000;
        float strength = 1.0F;
        int pendingTarget = -1;
        Kind lastKind = Kind.LIGHT;
        String lastMove = "";
        long shownUntil = 0;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static boolean busy = false;

    static State state(ServerPlayer p) { return STATES.computeIfAbsent(p.getUUID(), u -> new State()); }

    public static boolean busy() { return busy; }

    /** AttackEntityEvent: antes del golpe, guarda cuanto estaba cargado. */
    public static void beforeAttack(ServerPlayer p, net.minecraft.world.entity.Entity target) {
        State s = state(p);
        s.strength = testFullStrength ? 1.0F : p.getAttackStrengthScale(0.5F);
        s.lastAttack = p.level().getGameTime();
        s.pendingTarget = target.getId();
    }

    /** Pruebas: el jugador simulado no carga el golpe. */
    public static boolean testFullStrength = false;

    /** Ventana para seguir la cadena: un poco mas que lo que tarda el arma en recargar. */
    static int window(ServerPlayer p) {
        return (int) Math.ceil(p.getCurrentItemAttackStrengthDelay()) + 14;
    }

    /** Que movimiento toca ahora (sin aplicarlo). */
    public static Kind kindFor(ServerPlayer p) {
        if (Guard.counterReady(p)) return Kind.COUNTER;
        if (Dodge.justDodged(p)) return Kind.EVADE;
        if (!p.onGround() && !p.isInWater() && p.getDeltaMovement().y < -0.08 && Stamina.has(p, COST_AIR)) return Kind.AIR;
        if (p.isSprinting() && Stamina.has(p, COST_DASH)) return Kind.DASH;
        if (p.isShiftKeyDown() && Stamina.has(p, COST_HEAVY)) return Kind.HEAVY;
        return Kind.LIGHT;
    }

    /**
     * Golpe cuerpo a cuerpo de un jugador transformado: elige el movimiento, devuelve el multiplicador del dano y
     * aplica alcance y efectos. 1.0 si no hay juego para lo que lleva en la mano o el golpe no estaba cargado.
     */
    public static float onPlayerHit(ServerLevel level, ServerPlayer p, LivingEntity victim, float amount) {
        if (busy) return 1.0F;
        Set set = setFor(p);
        if (set == null) return 1.0F;
        State s = state(p);
        long now = level.getGameTime();
        // solo el golpe cuerpo a cuerpo de verdad (clic izquierdo), no el dano de habilidades que usan el mismo tipo
        if (s.pendingTarget != victim.getId() || s.lastAttack < now - 1) return 1.0F;
        s.pendingTarget = -1;
        if (s.strength < 0.6F) { s.step = -1; return 1.0F; }   // apretar sin cargar no encadena

        Kind kind = kindFor(p);
        Move move;
        if (kind == Kind.LIGHT) {
            boolean chain = s.set.equals(set.id) && s.lastKind == Kind.LIGHT && now - s.lastHit <= window(p);
            s.step = chain ? (s.step + 1) % set.light.size() : 0;
            move = set.light.get(s.step);
        } else {
            move = set.of(kind);
            int cost = switch (kind) { case HEAVY -> COST_HEAVY; case DASH -> COST_DASH; case AIR -> COST_AIR; default -> 0; };
            if (cost > 0) Stamina.spend(p, cost);
            if (kind == Kind.EVADE) Dodge.consume(p);
            s.step = -1;
        }
        s.set = set.id;
        s.lastKind = kind;
        s.lastHit = now;
        s.lastMove = move.key(set.id);
        s.shownUntil = now + 30;
        Stamina.pause(p, 12);

        boolean chainEnd = kind == Kind.LIGHT && s.step == set.light.size() - 1;
        if (chainEnd) DexAdvancements.award(p, "full_chain");
        perform(level, p, victim, set, move, amount * move.mult, chainEnd || kind != Kind.LIGHT);
        return move.mult;
    }

    private static void perform(ServerLevel level, ServerPlayer p, LivingEntity victim, Set set, Move move, float damage, boolean big) {
        com.draxer.dexrider.network.PosePayload.send(p, move.anim);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), set.swing, SoundSource.PLAYERS, big ? 1.0F : 0.7F, big ? 0.8F : 1.0F + p.getRandom().nextFloat() * 0.2F);
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z).normalize();

        // el que mueve: avance o picada
        if (move.fx.contains(Fx.DASH)) {
            p.setDeltaMovement(flat.x * 0.9, Math.max(0.05, p.getDeltaMovement().y), flat.z * 0.9);
            p.hurtMarked = true;
        }
        if (move.fx.contains(Fx.DIVE)) {
            p.setDeltaMovement(p.getDeltaMovement().x * 0.3, -1.1, p.getDeltaMovement().z * 0.3);
            p.hurtMarked = true;
            p.fallDistance = 0;
        }

        // a quien alcanza ademas del objetivo
        List<LivingEntity> extra = new ArrayList<>();
        Vec3 origin = move.shape == Shape.SLAM ? victim.position() : p.position();
        if (move.shape != Shape.SINGLE) {
            double r = move.reach;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r + 2), e -> e != p && e != victim && e.isAlive())) {
                if (!hostile(p, e)) continue;
                Vec3 to = e.position().subtract(move.shape == Shape.SLAM ? origin : p.position());
                double dist = new Vec3(to.x, 0, to.z).length();
                switch (move.shape) {
                    case ARC -> {
                        if (dist > r) continue;
                        Vec3 dir = new Vec3(to.x, 0, to.z);
                        if (dir.lengthSqr() > 0.01 && Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dir.normalize().dot(flat))))) > move.arc / 2) continue;
                    }
                    case THRUST -> {
                        double along = to.dot(flat);
                        Vec3 side = to.subtract(flat.scale(along));
                        if (along < 0 || along > r || new Vec3(side.x, 0, side.z).length() > 1.0 || Math.abs(to.y) > 2) continue;
                    }
                    default -> { if (dist > r || Math.abs(to.y) > 2.5) continue; }
                }
                extra.add(e);
            }
        }

        // efectos visuales de la forma
        Vec3 c = FX.center(victim);
        switch (move.shape) {
            case ARC -> {
                for (int i = -2; i <= 2; i++) {
                    Vec3 d = FX.yaw(flat, i * move.arc / 5.0).scale(Math.min(move.reach, 2.6));
                    FX.dust(level, move.color, 1.2F, p.position().add(0, 1.1, 0).add(d), 2, 0.05);
                }
            }
            case THRUST -> FX.line(level, p.getEyePosition().add(0, -0.3, 0), p.getEyePosition().add(0, -0.3, 0).add(flat.scale(move.reach)), move.color, 0.9F, 0.4);
            case RING -> FX.ring(level, p, move.color, move.reach * 0.8, 2);
            case SLAM -> {
                FX.particle(level, ParticleTypes.EXPLOSION, victim.position(), 1, 0, 0);
                FX.ring(level, victim, move.color, move.reach * 0.8, 1);
                level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 0.9F, 1.0F);
            }
            default -> FX.dust(level, move.color, 1.0F, c, 4, 0.2);
        }

        busy = true;
        try {
            for (LivingEntity e : extra) FX.hit(level, p, e, damage * 0.75F);
        } finally {
            busy = false;
        }
        List<LivingEntity> all = new ArrayList<>(extra);
        all.add(0, victim);
        for (LivingEntity e : all) effects(level, p, e, move, damage);

        // remates: cartas, estallido, segundo golpe, disparo
        if (move.fx.contains(Fx.CARDS)) FX.card(level, c, flat, move.color);
        if (move.fx.contains(Fx.BURST)) {
            FX.particle(level, ParticleTypes.EXPLOSION, c, 1, 0, 0);
            FX.dust(level, move.color, 1.6F, c, 14, 0.5);
            level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5F, 1.4F);
        }
        if (move.fx.contains(Fx.SHOT)) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), com.draxer.dexrider.registry.DexSounds.SHOT.get(), SoundSource.PLAYERS, 0.8F, 1.1F);
            FX.line(level, p.getEyePosition().add(0, -0.2, 0), c, move.color, 0.9F, 0.3);
            Scheduler.soft(p, 2, () -> again(level, p, victim, damage * 0.4F, move.color));
        }
        if (move.fx.contains(Fx.DOUBLE)) Scheduler.soft(p, 4, () -> again(level, p, victim, damage * 0.45F, move.color));
        if (big) FX.particle(level, ParticleTypes.CRIT, c, 10, 0.3, 0.3);
    }

    private static void again(ServerLevel level, ServerPlayer p, LivingEntity victim, float damage, int color) {
        if (!victim.isAlive() || victim.distanceToSqr(p) > 64) return;
        busy = true;
        try {
            FX.hit(level, p, victim, damage);
        } finally {
            busy = false;
        }
        FX.dust(level, color, 1.2F, FX.center(victim), 6, 0.3);
        FX.particle(level, ParticleTypes.CRIT, FX.center(victim), 6, 0.3, 0.2);
    }

    private static void effects(ServerLevel level, ServerPlayer p, LivingEntity e, Move move, float damage) {
        if (move.knock > 0 || move.lift > 0) FX.push(e, p.position(), move.knock, move.lift);
        for (Fx f : move.fx) {
            switch (f) {
                case SHOCK -> {
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
                    FX.particle(level, ParticleTypes.ELECTRIC_SPARK, FX.center(e), 10, 0.3, 0.2);
                }
                case STUN -> {
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 5));
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 1));
                    FX.particle(level, ParticleTypes.WAX_OFF, e.getEyePosition().add(0, 0.4, 0), 6, 0.25, 0.02);
                }
                case LAUNCH -> { e.setDeltaMovement(e.getDeltaMovement().x, Math.max(e.getDeltaMovement().y, 0.75), e.getDeltaMovement().z); e.hurtMarked = true; }
                case PULL -> {
                    Vec3 d = p.position().subtract(e.position());
                    if (d.lengthSqr() > 2.5) { Vec3 n = d.normalize().scale(0.9); e.setDeltaMovement(n.x, 0.25, n.z); e.hurtMarked = true; }
                }
                case FLASH -> {
                    e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
                    if (e instanceof Mob mob) mob.getNavigation().stop();
                    FX.particle(level, ParticleTypes.END_ROD, FX.center(e), 12, 0.3, 0.15);
                }
                case SPORE -> {
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2));
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
                    FX.particle(level, ParticleTypes.SPORE_BLOSSOM_AIR, FX.center(e), 12, 0.5, 0.02);
                }
                case KNOCKDOWN -> {
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 25, 3));
                    e.setDeltaMovement(e.getDeltaMovement().multiply(0.2, 1, 0.2));
                    e.hurtMarked = true;
                }
                case BURN -> e.igniteForSeconds(3.0F);
                default -> { }
            }
        }
    }

    /** A quien le alcanzan los golpes de area: enemigos y lo que te ataca; nunca jugadores, amigos ni aldeanos. */
    static boolean hostile(ServerPlayer p, LivingEntity e) {
        if (e instanceof net.minecraft.world.entity.player.Player) return false;
        if (e instanceof com.draxer.dexrider.entity.RiderMob rm && rm.isFriendlyTo(p)) return false;
        if (e instanceof Enemy) return true;
        return e instanceof Mob m && m.getTarget() == p;
    }

    // ---------------------------------------------------------------- HUD
    @Nullable
    public static String shownMove(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        return s != null && s.shownUntil >= p.level().getGameTime() ? s.lastMove : null;
    }

    public static int shownStep(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        if (s == null || s.lastKind != Kind.LIGHT || s.step < 0) return 0;
        return s.step + 1;
    }

    public static int chainLength(ServerPlayer p) {
        Set set = setFor(p);
        return set == null ? 0 : set.light.size();
    }

    public static Component describe(String set, Move m) {
        return Component.translatable(m.key(set));
    }

    public static void forget(UUID id) { STATES.remove(id); }
}
