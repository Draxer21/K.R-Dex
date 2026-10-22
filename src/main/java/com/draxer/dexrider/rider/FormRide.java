package com.draxer.dexrider.rider;

import com.draxer.dexrider.item.DexCard;
import com.draxer.dexrider.item.DexDriverItem;
import com.draxer.dexrider.item.DriverDeck;
import com.draxer.dexrider.registry.DexComponents;
import com.draxer.dexrider.registry.DexSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Form Ride: cambia los poderes de Dex. Solo una forma activa a la vez (sin combinaciones, como en el fanfic).
 * Usar la carta de la forma activa vuelve a la Dex Form base.
 *
 *  SPEED  : velocidad muy alta (Nv1 ~ Velocidad III, Nv3 ~ Velocidad V), prisa.
 *  GAS    : los proyectiles lo atraviesan, sin dano de caida, cae lento. Nv2 inmune al fuego. Nv3 invisible.
 *  TERRA  : resistencia, mineria rapida; los golpes levantan al enemigo. Nv1 algo lento.
 *  SKY    : forma nocturna. Vuela en la oscuridad (Nv1), con poca luz (Nv2), siempre (Nv3). Vision nocturna.
 *  FROST  : congela el agua al caminar, inmune a congelarse, golpes que congelan y ralentizan. Fuego x1.5.
 *  CINDER : inmune al fuego y la lava, golpes que queman. El agua y la lluvia lo debilitan.
 *  HALF   : Half-Cold Half-Hot. Inmune al fuego y al congelamiento, golpes que queman Y ralentizan.
 *
 * Las habilidades activas (tecla R) estan en FormAbilities.
 */
public enum FormRide {
    SPEED(DexCard.FORM_RIDE_SPEED),
    GAS(DexCard.FORM_RIDE_GAS),
    TERRA(DexCard.FORM_RIDE_TERRA),
    SKY(DexCard.FORM_RIDE_SKY),
    FROST(DexCard.FORM_RIDE_FROST),
    CINDER(DexCard.FORM_RIDE_CINDER),
    HALF(DexCard.FORM_RIDE_HALF);

    public final DexCard card;

    FormRide(DexCard card) {
        this.card = card;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static FormRide byCard(DexCard card) {
        for (FormRide f : values()) if (f.card == card) return f;
        return null;
    }

    public static FormRide byId(String id) {
        for (FormRide f : values()) if (f.id().equals(id)) return f;
        return null;
    }

    // ------------------------------------------------------------------ estado

    /** Forma activa (o null = Dex Form base). Se guarda en el peto. */
    public static FormRide current(LivingEntity entity) {
        String id = entity.getItemBySlot(EquipmentSlot.CHEST).get(DexComponents.FORM.get());
        return id == null ? null : byId(id);
    }

    public static int level(ServerPlayer player, FormRide form) {
        DriverDeck deck = DexDriverItem.getDeck(DexDriverItem.findDriver(player));
        return Math.max(1, deck.level(form.card));
    }

    /** Usar una carta de Form Ride. */
    public static void use(ServerLevel level, ServerPlayer player, FormRide form) {
        if (KamenRide.current(player) != KamenRide.DEX) {
            // Las Form Ride de Dex solo funcionan en la forma de Dex
            player.sendSystemMessage(Component.translatable("message.dexrider.form_need_dex"), true);
            return;
        }
        FormRide active = current(player);
        DexAdvancements.award(player, "form_ride");
        clearForm(player);
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (active == form) {
            // misma carta: vuelve a la forma base
            DexSounds.play(level, player, DexSounds.HENSHIN, 0.8F, 1.3F);
            player.sendSystemMessage(Component.translatable("message.dexrider.form_base"), true);
        } else {
            chest.set(DexComponents.FORM.get(), form.id());
            DexSounds.play(level, player, DexSounds.HENSHIN, 1.0F, 1.15F);
            DexSounds.play(level, player, DexSounds.VOICE_KAMEN_RIDE, 1.0F, 1.0F);
            player.sendSystemMessage(Component.translatable("message.dexrider.form_ride",
                    Component.translatable("form.dexrider." + form.id()), level(player, form)), true);
        }
        FX.ring(level, player, form.particleColor(), 1.3, 8);
        Transformation.applyFormEffects(player);
    }

    /** Quita los efectos propios de la forma activa (antes de cambiar de forma o al destransformarse). */
    public static void clearForm(ServerPlayer player) {
        player.getItemBySlot(EquipmentSlot.CHEST).remove(DexComponents.FORM.get());
        for (Holder<MobEffect> e : ALL_FORM_EFFECTS) player.removeEffect(e);
        revokeFlight(player);
        FormAbilities.clear(player);
    }

    private static final List<Holder<MobEffect>> ALL_FORM_EFFECTS = List.of(
            MobEffects.STRENGTH, MobEffects.SPEED, MobEffects.JUMP_BOOST, MobEffects.HASTE, MobEffects.SLOW_FALLING,
            MobEffects.FIRE_RESISTANCE, MobEffects.INVISIBILITY, MobEffects.RESISTANCE, MobEffects.SLOWNESS,
            MobEffects.NIGHT_VISION, MobEffects.WEAKNESS);

    // ------------------------------------------------------------------ efectos (cada 20 ticks)

    private static void effect(ServerPlayer p, Holder<MobEffect> e, int amplifier) {
        p.addEffect(new MobEffectInstance(e, 60, amplifier, false, false, true));
    }

    /** Efectos de la forma. Llamado por Transformation.applyFormEffects. */
    public void applyEffects(ServerPlayer p, int lvl) {
        switch (this) {
            case SPEED -> {
                effect(p, MobEffects.SPEED, 1 + lvl);          // Nv1 III, Nv2 IV, Nv3 V
                effect(p, MobEffects.HASTE, 0);
                effect(p, MobEffects.JUMP_BOOST, 0);
            }
            case GAS -> {
                effect(p, MobEffects.SLOW_FALLING, 0);
                effect(p, MobEffects.SPEED, lvl - 1);
                // el gas arde: si esta en llamas se vuelve mas fuerte
                if (p.isOnFire()) effect(p, MobEffects.STRENGTH, lvl);
            }
            case TERRA -> {
                effect(p, MobEffects.RESISTANCE, lvl - 1);      // Nv1 I, Nv3 III
                effect(p, MobEffects.HASTE, lvl);
                effect(p, MobEffects.STRENGTH, 0);
                if (lvl == 1) effect(p, MobEffects.SLOWNESS, 0);
            }
            case SKY -> {
                effect(p, MobEffects.SLOW_FALLING, 0);
                effect(p, MobEffects.NIGHT_VISION, 0);
                effect(p, MobEffects.JUMP_BOOST, 1);
            }
            case FROST -> {
                effect(p, MobEffects.STRENGTH, 0);
                effect(p, MobEffects.SPEED, 0);
            }
            case CINDER -> {
                effect(p, MobEffects.FIRE_RESISTANCE, 0);
                if (p.isInWaterOrRain()) {
                    // el agua apaga a Cinder
                    effect(p, MobEffects.WEAKNESS, 0);
                    effect(p, MobEffects.SLOWNESS, 0);
                } else {
                    effect(p, MobEffects.STRENGTH, lvl >= 3 ? 1 : 0);
                }
            }
            case HALF -> {
                effect(p, MobEffects.FIRE_RESISTANCE, 0);
                effect(p, MobEffects.STRENGTH, lvl >= 2 ? 1 : 0);
                effect(p, MobEffects.SPEED, 0);
            }
        }
    }

    // ------------------------------------------------------------------ cada tick

    private static final Set<UUID> FLIGHT_GRANTED = new HashSet<>();

    /** Comportamiento continuo de la forma activa. */
    public static void tick(ServerLevel level, ServerPlayer p) {
        FormRide form = current(p);
        if (form != SKY && KamenRide.current(p) != KamenRide.PSYGA) revokeFlight(p);
        if (form == null) return;
        int lvl = level(p, form);

        switch (form) {
            case SKY -> {
                if (p.tickCount % 10 == 0) {
                    int light = level.getMaxLocalRawBrightness(p.blockPosition());
                    int maxLight = lvl == 1 ? 7 : lvl == 2 ? 11 : 15;
                    if (light <= maxLight) grantFlight(p);
                    else revokeFlight(p);
                }
                if (p.tickCount % 4 == 0) FX.particle(level, ParticleTypes.SQUID_INK, p.position().add(0, 1, 0), 2, 0.4, 0.01);
            }
            case FROST -> {
                if (p.onGround()) freezeWater(level, p, 1 + lvl);
                if (p.tickCount % 5 == 0) FX.particle(level, ParticleTypes.SNOWFLAKE, p.position().add(0, 1, 0), 3, 0.4, 0.02);
                p.setTicksFrozen(0);
            }
            case CINDER -> {
                if (p.tickCount % 3 == 0) FX.particle(level, ParticleTypes.FLAME, p.position().add(0, 0.2, 0), 3, 0.3, 0.01);
                if (p.isInWaterOrRain() && p.tickCount % 4 == 0)
                    FX.particle(level, ParticleTypes.CLOUD, p.position().add(0, 1.2, 0), 2, 0.3, 0.02);   // vapor
                p.setTicksFrozen(0);
            }
            case HALF -> {
                if (p.onGround()) freezeWater(level, p, 1 + lvl);
                p.setTicksFrozen(0);
                if (p.tickCount % 4 == 0) {
                    // nieve del lado derecho, fuego del izquierdo
                    Vec3 right = rightSide(p);
                    FX.particle(level, ParticleTypes.SNOWFLAKE, p.position().add(right.scale(0.35)).add(0, 1, 0), 2, 0.15, 0.01);
                    FX.particle(level, ParticleTypes.FLAME, p.position().add(right.scale(-0.35)).add(0, 1, 0), 2, 0.15, 0.01);
                }
            }
            case GAS -> {
                boolean smoke = FormAbilities.isSmoke(p);
                if (p.tickCount % (smoke ? 1 : 3) == 0)
                    FX.particle(level, ParticleTypes.CLOUD, p.position().add(0, 1, 0), smoke ? 4 : 2, 0.4, 0.01);
                p.resetFallDistance();
            }
            case SPEED -> {
                Vec3 v = p.getDeltaMovement();
                if (v.horizontalDistanceSqr() > 0.04 && p.tickCount % 2 == 0)
                    FX.dust(level, 0x3C8CFF, 1.2F, p.position().add(0, 0.6, 0), 3, 0.2);
            }
            case TERRA -> { }
        }
    }

    private static void freezeWater(ServerLevel level, ServerPlayer p, int radius) {
        BlockPos below = p.blockPosition().below();
        for (BlockPos pos : BlockPos.betweenClosed(below.offset(-radius, 0, -radius), below.offset(radius, 0, radius))) {
            if (pos.distSqr(below) > radius * radius) continue;
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.WATER) && state.getFluidState().isSource() && level.getBlockState(pos.above()).isAir()) {
                level.setBlockAndUpdate(pos, Blocks.FROSTED_ICE.defaultBlockState());
                level.scheduleTick(pos.immutable(), Blocks.FROSTED_ICE, 60 + level.getRandom().nextInt(60));
            }
        }
    }

    /** Vector horizontal hacia la derecha del jugador. */
    private static Vec3 rightSide(ServerPlayer p) {
        double yaw = Math.toRadians(p.getYRot());
        return new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
    }

    public static void grantFlight(ServerPlayer p) {
        if (p.isCreative() || p.isSpectator() || FLIGHT_GRANTED.contains(p.getUUID())) return;
        FLIGHT_GRANTED.add(p.getUUID());
        p.getAbilities().mayfly = true;
        p.onUpdateAbilities();
    }

    public static void revokeFlight(ServerPlayer p) {
        if (!FLIGHT_GRANTED.remove(p.getUUID())) return;
        if (p.isCreative() || p.isSpectator()) return;
        p.getAbilities().mayfly = false;
        p.getAbilities().flying = false;
        p.onUpdateAbilities();
    }

    /** Color principal del traje en esta forma (las Form Ride mantienen su color). */
    public int suitColor() {
        return switch (this) {
            case SPEED -> 0x1E69EB;
            case GAS -> 0x8264A5;
            case TERRA -> 0xA06228;
            case SKY -> 0x46226E;
            case FROST -> 0x6ECDEB;
            case CINDER -> 0xF06912;
            case HALF -> 0xE6F5FF;   // el renderer pinta el lado izquierdo con HALF_FIRE_COLOR
        };
    }

    /** Half-Cold Half-Hot: lado derecho hielo (suitColor), lado izquierdo fuego. */
    public static final int HALF_FIRE_COLOR = 0xD7261E;

    public int particleColor() {
        return switch (this) {
            case SPEED -> 0x3C8CFF;
            case GAS -> 0xA08CC8;
            case TERRA -> 0xB4783C;
            case SKY -> 0x5A2A8C;
            case FROST -> 0x8CE6FF;
            case CINDER -> 0xFF7814;
            case HALF -> 0xFFFFFF;
        };
    }
}
