package com.draxer.dexrider.rider;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Habilidades activas de las Form Ride (tecla R, se cambia en Controles).
 *
 *   R          -> la habilidad del nivel mas alto que tengas de esa forma
 *   Shift + R  -> la habilidad basica (Nv1) / de apoyo
 *
 * El enfriamiento baja con el nivel de la forma (Nv2 -20 %, Nv3 -40 %).
 *
 *  FROST  : Nv1 muro de hielo · Nv2 rayo congelante (12 bloques) · Nv3 glaciar (cono de picos de hielo)
 *  CINDER : Nv1 bola de fuego · Nv2 lanzallamas · Nv3 erupcion (anillo de fuego)
 *  GAS    : cuerpo de humo (intangible, no puede atacar) · Nv2 + nube cegadora · Nv3 mas largo y rapido
 *  TERRA  : Nv1 pilar que lanza al enemigo · Nv2 rampa de tierra · Nv3 terremoto
 *  SKY    : salto de sombra (te mueves en linea recta y danas a lo que atraviesas)
 *  SPEED  : rafaga (2 s a velocidad extrema arrollando enemigos) · Nv3 deja una estela cortante
 *  HALF   : rayo de hielo y, medio segundo despues, choque termico (explosion de fuego en los congelados).
 *           Shift + R: muro de hielo.
 */
public final class FormAbilities {
    private FormAbilities() {}

    private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> SMOKE_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN_TOTAL = new HashMap<>();

    /** Para el HUD: {ticks que faltan, ticks totales} de la recarga de la tecla R. */
    public static long[] cooldown(ServerPlayer p, long now) {
        Long until = COOLDOWN_UNTIL.get(p.getUUID());
        if (until == null || until <= now) return new long[]{0, 1};
        return new long[]{until - now, Math.max(1, COOLDOWN_TOTAL.getOrDefault(p.getUUID(), until - now))};
    }

    /** Lo que hace la tecla R. */
    public static void use(ServerPlayer player) {
        if (!Transformation.isTransformed(player) || RiderKick.isKicking(player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        Long until = COOLDOWN_UNTIL.get(player.getUUID());
        if (until != null && until > now) {
            player.sendSystemMessage(Component.translatable("message.dexrider.ability_cooldown",
                    String.format("%.1f", (until - now) / 20.0)), true);
            return;
        }
        // Conduciendo una moto: la habilidad es la de la moto
        if (player.getVehicle() instanceof com.draxer.dexrider.vehicle.DexMachineEntity bike) {
            RiderPowers.Used used = com.draxer.dexrider.vehicle.BikeAbilities.use(level, player, bike);
            if (used == null) return;
            COOLDOWN_UNTIL.put(player.getUUID(), now + used.cooldownSeconds() * 20L);
            COOLDOWN_TOTAL.put(player.getUUID(), used.cooldownSeconds() * 20L);
            player.sendSystemMessage(Component.translatable("message.dexrider.ability_used",
                    Component.translatable("ability.dexrider." + used.name())), true);
            return;
        }
        // Otra identidad (Nine, Cipher, Spore, Complete Form...): sus propios poderes
        KamenRide identity = KamenRide.current(player);
        if (identity != KamenRide.DEX) {
            RiderPowers.Used used = RiderPowers.ability(level, player, identity, player.isShiftKeyDown());
            if (used == null) return;
            COOLDOWN_UNTIL.put(player.getUUID(), now + used.cooldownSeconds() * 20L);
            COOLDOWN_TOTAL.put(player.getUUID(), used.cooldownSeconds() * 20L);
            player.sendSystemMessage(Component.translatable("message.dexrider.ability_used",
                    Component.translatable("ability.dexrider." + used.name())), true);
            return;
        }
        FormRide form = FormRide.current(player);
        if (form == null) {
            player.sendSystemMessage(Component.translatable("message.dexrider.ability_no_form"), true);
            return;
        }

        int lvl = FormRide.level(player, form);
        int tier = player.isShiftKeyDown() ? 1 : lvl;
        String name = activate(level, player, form, tier, lvl);
        if (name == null) return;

        int seconds = baseCooldown(form, tier);
        long ticks = Math.round(seconds * 20 * (1.0 - 0.2 * (lvl - 1)));
        COOLDOWN_UNTIL.put(player.getUUID(), now + ticks);
        COOLDOWN_TOTAL.put(player.getUUID(), ticks);
        player.sendSystemMessage(Component.translatable("message.dexrider.ability_used",
                Component.translatable("ability.dexrider." + name)), true);
    }

    private static int baseCooldown(FormRide form, int tier) {
        return switch (form) {
            case FROST -> tier == 1 ? 8 : tier == 2 ? 6 : 14;
            case CINDER -> tier == 1 ? 3 : tier == 2 ? 8 : 14;
            case GAS -> 14;
            case TERRA -> tier == 1 ? 6 : tier == 2 ? 10 : 14;
            case SKY -> 5;
            case SPEED -> 10;
            case HALF -> 9;
        };
    }

    /** Ejecuta la habilidad y devuelve su nombre (para el mensaje), o null si no se pudo usar. */
    private static String activate(ServerLevel level, ServerPlayer p, FormRide form, int tier, int lvl) {
        return switch (form) {
            case FROST -> switch (tier) {
                case 1 -> iceWall(level, p, lvl) ? "ice_wall" : null;
                case 2 -> { freezeRay(level, p, 12, 5.0F, lvl); yield "freeze_ray"; }
                default -> { glacier(level, p, lvl); yield "glacier"; }
            };
            case CINDER -> switch (tier) {
                case 1 -> { fireBolt(level, p, lvl); yield "fire_bolt"; }
                case 2 -> { flamethrower(level, p, lvl); yield "flamethrower"; }
                default -> { eruption(level, p, lvl); yield "eruption"; }
            };
            case GAS -> { smokeBody(level, p, tier); yield "smoke_body"; }
            case TERRA -> switch (tier) {
                case 1 -> pillar(level, p, lvl) ? "pillar" : null;
                case 2 -> ramp(level, p) ? "ramp" : null;
                default -> { quake(level, p, lvl); yield "quake"; }
            };
            case SKY -> shadowDash(level, p, lvl) ? "shadow_dash" : null;
            case SPEED -> { speedBurst(level, p, lvl); yield "speed_burst"; }
            case HALF -> {
                if (tier == 1 && p.isShiftKeyDown()) yield iceWall(level, p, lvl) ? "ice_wall" : null;
                thermalShock(level, p, lvl);
                yield "thermal_shock";
            }
        };
    }

    // ================================================================== FROST

    /** Muro de hielo perpendicular a la mirada, 3 bloques delante. Ancho 5/7/9, alto 3. Dura 8 s. */
    private static boolean iceWall(ServerLevel level, ServerPlayer p, int lvl) {
        Direction facing = p.getDirection();
        Direction side = facing.getClockWise();
        BlockPos base = p.blockPosition().relative(facing, 3);
        int half = 1 + lvl;
        int placed = 0;
        for (int w = -half; w <= half; w++) {
            BlockPos column = ground(level, base.relative(side, w));
            for (int h = 0; h < 3; h++) {
                if (TempBlocks.place(level, column.above(h), Blocks.PACKED_ICE.defaultBlockState(), 160)) placed++;
            }
        }
        if (placed == 0) return false;
        sound(level, base.getCenter(), SoundEvents.GLASS_PLACE, 1.5F, 0.6F);
        FX.particle(level, ParticleTypes.SNOWFLAKE, base.getCenter().add(0, 1, 0), 40, 1.5, 0.05);
        return true;
    }

    /** Rayo congelante: atraviesa a todos, los congela y ralentiza. */
    private static List<LivingEntity> freezeRay(ServerLevel level, ServerPlayer p, double range, float damage, int lvl) {
        List<LivingEntity> hits = FX.beam(level, p, p.getLookAngle(), range, damage, 0x9BE8FF, 1.6F, true);
        Vec3 start = p.getEyePosition();
        Vec3 end = FX.rayEnd(level, p, start, p.getLookAngle(), range);
        for (double d = 1; d < start.distanceTo(end); d += 0.6) {
            FX.particle(level, ParticleTypes.SNOWFLAKE, start.add(p.getLookAngle().scale(d)), 1, 0.1, 0.01);
        }
        for (LivingEntity e : hits) chill(level, e, lvl, 1.0);
        // Congela el agua donde termina el rayo
        BlockPos hitPos = BlockPos.containing(end);
        for (BlockPos pos : BlockPos.betweenClosed(hitPos.offset(-1, -1, -1), hitPos.offset(1, 1, 1))) {
            if (level.getBlockState(pos).is(Blocks.WATER) && level.getBlockState(pos).getFluidState().isSource()) {
                TempBlocks.place(level, pos, Blocks.ICE.defaultBlockState(), 200);
            }
        }
        sound(level, p.position(), SoundEvents.PLAYER_HURT_FREEZE, 1.2F, 0.7F);
        sound(level, p.position(), SoundEvents.GLASS_BREAK, 0.8F, 1.6F);
        return hits;
    }

    /** Glaciar: un cono de picos de hielo (8 bloques) que dana, congela y levanta. Los picos duran 5 s. */
    private static void glacier(ServerLevel level, ServerPlayer p, int lvl) {
        Vec3 look = horizontal(p.getLookAngle());
        Vec3 origin = p.position();
        for (int d = 2; d <= 8; d++) {
            int spread = d / 2;
            for (int s = -spread; s <= spread; s++) {
                Vec3 at = origin.add(look.scale(d)).add(FX.yaw(look, 90).scale(s));
                BlockPos column = ground(level, BlockPos.containing(at));
                int height = 1 + (d + Math.abs(s)) % 3;
                final int delay = d;
                Scheduler.later(p, delay, () -> {
                    for (int h = 0; h < height; h++) {
                        TempBlocks.place(level, column.above(h), Blocks.PACKED_ICE.defaultBlockState(), 100);
                    }
                    FX.particle(level, ParticleTypes.SNOWFLAKE, column.getCenter().add(0, 1, 0), 4, 0.3, 0.05);
                });
            }
        }
        for (LivingEntity e : inCone(level, p, 9.0, 0.55)) {
            FX.hit(level, p, e, 9.0F);
            chill(level, e, lvl, 1.5);
            FX.push(e, origin, 0.3, 0.5);
        }
        sound(level, p.position(), SoundEvents.GLASS_BREAK, 2.0F, 0.5F);
        sound(level, p.position(), SoundEvents.PLAYER_HURT_FREEZE, 1.5F, 0.5F);
    }

    private static void chill(ServerLevel level, LivingEntity e, int lvl, double scale) {
        int freeze = (int) (80 * lvl * scale);
        e.setTicksFrozen(Math.min(e.getTicksRequiredToFreeze() + 100, e.getTicksFrozen() + freeze));
        e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, (int) (60 * scale) + 20 * lvl, 1 + lvl));
        FX.particle(level, ParticleTypes.SNOWFLAKE, FX.center(e), 15, 0.4, 0.05);
    }

    // ================================================================== CINDER

    private static void fireBolt(ServerLevel level, ServerPlayer p, int lvl) {
        List<LivingEntity> hits = FX.beam(level, p, p.getLookAngle(), 20, 5.0F + 1.5F * lvl, 0xFF7814, 1.8F, false);
        Vec3 start = p.getEyePosition();
        Vec3 end = FX.rayEnd(level, p, start, p.getLookAngle(), 20);
        for (double d = 1; d < start.distanceTo(end); d += 0.5) {
            FX.particle(level, ParticleTypes.FLAME, start.add(p.getLookAngle().scale(d)), 1, 0.05, 0.01);
        }
        for (LivingEntity e : hits) {
            e.igniteForSeconds(4.0F + lvl);
            FX.particle(level, ParticleTypes.LAVA, FX.center(e), 6, 0.3, 0.1);
        }
        sound(level, p.position(), SoundEvents.BLAZE_SHOOT, 1.2F, 1.0F);
    }

    /** Lanzallamas durante 1 s: cono de 6 bloques. */
    private static void flamethrower(ServerLevel level, ServerPlayer p, int lvl) {
        Set<UUID> burned = new HashSet<>();
        for (int t = 0; t < 20; t += 2) {
            final boolean last = t >= 18;
            Scheduler.later(p, t + 1, () -> {
                Vec3 look = p.getLookAngle();
                Vec3 mouth = p.getEyePosition().add(0, -0.2, 0).add(look.scale(0.6));
                for (int i = 0; i < 12; i++) {
                    Vec3 dir = look.add((level.getRandom().nextDouble() - 0.5) * 0.35,
                            (level.getRandom().nextDouble() - 0.5) * 0.25,
                            (level.getRandom().nextDouble() - 0.5) * 0.35).normalize();
                    // count 0: los valores de "spread" se usan como direccion y la velocidad como modulo
                    level.sendParticles(ParticleTypes.FLAME, mouth.x, mouth.y, mouth.z, 0, dir.x, dir.y, dir.z, 0.45);
                }
                for (LivingEntity e : inCone(level, p, 6.5, 0.8)) {
                    e.igniteForSeconds(4.0F + lvl);
                    if (burned.add(e.getUUID()) || last) FX.hit(level, p, e, 2.0F + lvl);
                }
                sound(level, p.position(), SoundEvents.FIRECHARGE_USE, 0.6F, 0.8F + level.getRandom().nextFloat() * 0.3F);
            });
        }
    }

    /** Erupcion: anillo de fuego alrededor (radio 6) que lanza y quema. */
    private static void eruption(ServerLevel level, ServerPlayer p, int lvl) {
        Vec3 c = p.position();
        for (int ring = 1; ring <= 6; ring++) {
            final double r = ring;
            Scheduler.later(p, ring, () -> {
                for (int a = 0; a < 360; a += 12) {
                    Vec3 at = c.add(Math.cos(Math.toRadians(a)) * r, 0.1, Math.sin(Math.toRadians(a)) * r);
                    level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 0, 0, 1, 0, 0.35);
                    if (a % 36 == 0) FX.particle(level, ParticleTypes.LAVA, at, 1, 0.1, 0.1);
                }
            });
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(6, 3, 6),
                e -> e != p && e.isAlive() && e.distanceToSqr(c) <= 36)) {
            FX.hit(level, p, e, 8.0F + lvl);
            e.igniteForSeconds(6.0F);
            FX.push(e, c, 0.8, 0.8);
        }
        FX.particle(level, ParticleTypes.EXPLOSION, c.add(0, 0.5, 0), 3, 1.0, 0);
        FX.particle(level, ParticleTypes.LARGE_SMOKE, c.add(0, 0.5, 0), 30, 2.0, 0.05);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.5F, 1.2F);
        sound(level, c, SoundEvents.BLAZE_SHOOT, 1.5F, 0.6F);
    }

    // ================================================================== GAS

    /** Cuerpo de humo: intangible (nada lo dana) y no puede atacar. Nv2 ciega alrededor, Nv3 dura mas y corre. */
    private static void smokeBody(ServerLevel level, ServerPlayer p, int tier) {
        int ticks = tier >= 3 ? 120 : 60;
        SMOKE_UNTIL.put(p.getUUID(), level.getGameTime() + ticks);
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, false, true));
        if (tier >= 3) p.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, 2, false, false, true));
        if (tier >= 2) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(5),
                    e -> e != p && e.isAlive())) {
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
                e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 1));
            }
            FX.particle(level, ParticleTypes.LARGE_SMOKE, FX.center(p), 80, 2.5, 0.02);
        }
        FX.particle(level, ParticleTypes.CLOUD, FX.center(p), 40, 0.6, 0.05);
        sound(level, p.position(), SoundEvents.FIRE_EXTINGUISH, 1.0F, 0.6F);
    }

    /** Esta en cuerpo de humo ahora mismo. */
    public static boolean isSmoke(ServerPlayer p) {
        Long until = SMOKE_UNTIL.get(p.getUUID());
        if (until == null) return false;
        if (until <= p.level().getGameTime() || FormRide.current(p) != FormRide.GAS) {
            SMOKE_UNTIL.remove(p.getUUID());
            return false;
        }
        return true;
    }

    // ================================================================== TERRA

    /** Pilar de piedra bajo el objetivo que lo lanza por los aires. */
    private static boolean pillar(ServerLevel level, ServerPlayer p, int lvl) {
        LivingEntity target = FX.findTarget(level, p, 16, 0.85);
        if (target == null) {
            p.sendSystemMessage(Component.translatable("message.dexrider.ability_no_target"), true);
            return false;
        }
        BlockPos column = ground(level, target.blockPosition());
        FX.hit(level, p, target, 6.0F + lvl);
        target.setDeltaMovement(target.getDeltaMovement().x, 1.0 + 0.2 * lvl, target.getDeltaMovement().z);
        target.hurtMarked = true;
        BlockState stone = Blocks.STONE.defaultBlockState();
        FX.particle(level, new BlockParticleOption(ParticleTypes.BLOCK, stone), column.getCenter(), 30, 0.6, 0.1);
        // el pilar sube cuando el objetivo ya salio disparado (asi no queda atrapado dentro)
        Scheduler.later(p, 3, () -> {
            for (int h = 0; h < 2 + lvl; h++) TempBlocks.place(level, column.above(h), stone, 100);
        });
        sound(level, target.position(), SoundEvents.STONE_BREAK, 2.0F, 0.5F);
        return true;
    }

    /** Rampa de escaleras de piedra hacia delante (6 escalones). Dura 10 s. */
    private static boolean ramp(ServerLevel level, ServerPlayer p) {
        Direction facing = p.getDirection();
        BlockState step = Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing);
        BlockState fill = Blocks.DIRT.defaultBlockState();
        BlockPos start = p.blockPosition().relative(facing, 1);
        int placed = 0;
        for (int i = 0; i < 6; i++) {
            BlockPos top = start.relative(facing, i).above(i);
            for (int h = 0; h < i; h++) TempBlocks.place(level, top.below(i - h), fill, 200);
            if (TempBlocks.place(level, top, step, 200)) placed++;
        }
        if (placed == 0) return false;
        FX.particle(level, new BlockParticleOption(ParticleTypes.BLOCK, fill), start.getCenter(), 30, 1.0, 0.1);
        sound(level, p.position(), SoundEvents.STONE_PLACE, 1.5F, 0.7F);
        return true;
    }

    /** Terremoto: dana y lanza a todos los que esten en el suelo cerca (radio 7). */
    private static void quake(ServerLevel level, ServerPlayer p, int lvl) {
        Vec3 c = p.position();
        BlockState below = level.getBlockState(p.blockPosition().below());
        if (below.isAir()) below = Blocks.DIRT.defaultBlockState();
        BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, below);
        for (int ring = 1; ring <= 7; ring++) {
            final double r = ring;
            Scheduler.later(p, ring, () -> {
                for (int a = 0; a < 360; a += 15) {
                    Vec3 at = c.add(Math.cos(Math.toRadians(a)) * r, 0.2, Math.sin(Math.toRadians(a)) * r);
                    FX.particle(level, dust, at, 3, 0.2, 0.15);
                }
            });
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(7, 2, 7),
                e -> e != p && e.isAlive() && e.onGround() && e.distanceToSqr(c) <= 49)) {
            FX.hit(level, p, e, 10.0F);
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2));
            FX.push(e, c, 0.4, 0.9);
        }
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2F, 0.5F);
        sound(level, c, SoundEvents.STONE_BREAK, 2.0F, 0.4F);
    }

    // ================================================================== SKY

    /** Salto de sombra: avanza en linea recta (8/10/12 bloques) atravesando y danando a todo lo del camino. */
    private static boolean shadowDash(ServerLevel level, ServerPlayer p, int lvl) {
        Vec3 dir = p.getLookAngle();
        double range = 6 + 2 * lvl;
        Vec3 feet = p.position().add(0, 0.2, 0);
        // distancia libre: el camino de los pies y el de la cabeza
        double free = Math.min(FX.rayEnd(level, p, feet, dir, range).distanceTo(feet),
                FX.rayEnd(level, p, p.getEyePosition(), dir, range).distanceTo(p.getEyePosition())) - 0.6;
        Vec3 delta = dir.scale(Math.max(0, free));
        while (delta.length() > 0.5 && !level.noCollision(p, p.getBoundingBox().move(delta))) {
            delta = delta.scale((delta.length() - 0.5) / delta.length());
        }
        if (delta.length() <= 0.5) return false;

        Vec3 from = p.position();
        Vec3 to = from.add(delta);
        for (LivingEntity e : FX.alongLine(level, p, from.add(0, 1, 0), to.add(0, 1, 0), 0.8)) {
            FX.hit(level, p, e, 6.0F + 2 * lvl);
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
        }
        for (double d = 0; d < delta.length(); d += 0.4) {
            FX.particle(level, ParticleTypes.SQUID_INK, from.add(dir.scale(d)).add(0, 1, 0), 2, 0.25, 0.01);
        }
        FX.dust(level, 0x5A2A8C, 1.5F, to.add(0, 1, 0), 20, 0.5);
        p.teleportTo(to.x, to.y, to.z);
        p.setDeltaMovement(dir.scale(0.3));
        p.hurtMarked = true;
        p.resetFallDistance();
        sound(level, from, SoundEvents.PHANTOM_FLAP, 1.5F, 0.6F);
        sound(level, to, SoundEvents.ENDERMAN_TELEPORT, 0.8F, 0.5F);
        return true;
    }

    // ================================================================== SPEED

    /** Rafaga: 2 s a velocidad extrema arrollando enemigos. Nv3 deja una estela cortante. */
    private static void speedBurst(ServerLevel level, ServerPlayer p, int lvl) {
        p.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 6 + lvl, false, false, true));
        Set<UUID> rammed = new HashSet<>();
        List<Vec3> trail = new ArrayList<>();
        Set<UUID> cut = new HashSet<>();
        for (int t = 1; t <= 40; t++) {
            final int tick = t;
            Scheduler.later(p, t, () -> {
                Vec3 pos = p.position();
                FX.dust(level, 0x3C8CFF, 1.6F, pos.add(0, 0.8, 0), 4, 0.3);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(1.2),
                        e -> e != p && e.isAlive())) {
                    if (rammed.add(e.getUUID())) {
                        FX.hit(level, p, e, 4.0F + 2 * lvl);
                        FX.push(e, pos, 1.2, 0.4);
                        sound(level, e.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0F, 1.2F);
                    }
                }
                if (lvl >= 3) {
                    if (tick % 2 == 0) trail.add(pos.add(0, 0.8, 0));
                    for (Vec3 point : trail) FX.dust(level, 0x9BD2FF, 0.8F, point, 1, 0.1);
                    for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(16),
                            e -> e != p && e.isAlive() && !cut.contains(e.getUUID()))) {
                        for (Vec3 point : trail) {
                            if (e.getBoundingBox().inflate(0.4).contains(point)) {
                                cut.add(e.getUUID());
                                FX.hit(level, p, e, 6.0F);
                                FX.particle(level, ParticleTypes.SWEEP_ATTACK, FX.center(e), 1, 0, 0);
                                break;
                            }
                        }
                    }
                }
            });
        }
        sound(level, p.position(), SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0F, 1.6F);
    }

    // ================================================================== HALF-COLD HALF-HOT

    /** Rayo de hielo y, 10 ticks despues, explosion de fuego en los que siguen congelados (choque termico). */
    private static void thermalShock(ServerLevel level, ServerPlayer p, int lvl) {
        List<LivingEntity> frozen = freezeRay(level, p, 14, 4.0F, lvl);
        Scheduler.later(p, 10, () -> {
            for (LivingEntity e : frozen) {
                if (!e.isAlive()) continue;
                Vec3 c = FX.center(e);
                e.setTicksFrozen(0);
                FX.hit(level, p, e, 6.0F + 3.0F * lvl);
                e.igniteForSeconds(5.0F);
                FX.push(e, p.position(), 0.8, 0.4);
                FX.particle(level, ParticleTypes.EXPLOSION, c, 1, 0, 0);
                FX.particle(level, ParticleTypes.FLAME, c, 25, 0.5, 0.1);
                FX.particle(level, ParticleTypes.CLOUD, c, 15, 0.5, 0.05);   // vapor
                // salpica a los que esten cerca del congelado
                for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().inflate(2.5),
                        n -> n != p && n != e && n.isAlive())) {
                    FX.hit(level, p, near, 3.0F + lvl);
                    near.igniteForSeconds(3.0F);
                }
                level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 1.4F);
            }
            if (!frozen.isEmpty()) sound(level, p.position(), SoundEvents.FIRE_EXTINGUISH, 1.2F, 0.8F);
        });
    }

    // ================================================================== comun

    /** Entidades delante del jugador dentro de un cono. */
    private static List<LivingEntity> inCone(ServerLevel level, ServerPlayer p, double range, double minDot) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range),
                e -> e != p && e.isAlive())) {
            Vec3 to = FX.center(e).subtract(eye);
            if (to.length() <= range && to.normalize().dot(look) >= minDot && p.hasLineOfSight(e)) out.add(e);
        }
        return out;
    }

    /** Baja (o sube un poco) hasta encontrar el primer bloque libre sobre suelo firme. */
    private static BlockPos ground(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos m = pos.mutable();
        for (int i = 0; i < 3 && !level.getBlockState(m).canBeReplaced(); i++) m.move(Direction.UP);
        for (int i = 0; i < 4 && level.getBlockState(m.below()).canBeReplaced(); i++) m.move(Direction.DOWN);
        return m.immutable();
    }

    private static Vec3 horizontal(Vec3 v) {
        Vec3 h = new Vec3(v.x, 0, v.z);
        return h.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : h.normalize();
    }

    private static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    public static void clear(ServerPlayer p) {
        SMOKE_UNTIL.remove(p.getUUID());
    }
}
