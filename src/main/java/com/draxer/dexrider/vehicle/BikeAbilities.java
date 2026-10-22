package com.draxer.dexrider.vehicle;

import com.draxer.dexrider.rider.FX;
import com.draxer.dexrider.rider.TempBlocks;
import com.draxer.dexrider.rider.RiderPowers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Habilidades de las motos (tecla R mientras conduces) y efectos continuos.
 *
 *  DEX_MACHINE    : Card Boost   - acelera y atraviesa una fila de cartas que empuja a todo lo que toca
 *  NINE_STRIKER   : Smash Charge - embestida con onda de choque
 *  CIPHER_RUNNER  : Code Lock    - fija con un cono de codigo a los enemigos de delante
 *  MACHINE_FORGER : Puente       - crea un puente/rampa temporal delante (cruza rios y barrancos)
 *  MYCO_HURRICANE : (pasiva) estela de esporas que revela a todo lo que pasa cerca · R = nube de esporas
 *  MIRROR_SHOOTER : Espejismo    - tu y la moto os volveis invisibles y los monstruos pierden el rastro
 *  PROTO_RACER    : Propulsor    - salto largo con planeo
 */
public final class BikeAbilities {
    private BikeAbilities() {}

    @Nullable
    public static RiderPowers.Used use(ServerLevel level, ServerPlayer p, DexMachineEntity bike) {
        Vec3 look = horizontal(p.getLookAngle());
        if (bike.scarab()) {   // Scarab Rush: el caparazon embiste con todo
            bike.boost(30);
            ram(level, p, bike, look, 16.0F, 2.6);
            FX.particle(level, ParticleTypes.EXPLOSION, bike.position().add(look.scale(2.5)).add(0, 1, 0), 2, 0.5, 0);
            FX.dust(level, 0x3CC882, 1.8F, bike.position().add(0, 1, 0), 40, 1.2);
            sound(level, bike, SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 1.2F);
            return new RiderPowers.Used("scarab_rush", 12);
        }
        switch (bike.kind()) {
            case DEX_MACHINE -> {
                bike.boost(40);
                for (int i = 1; i <= 4; i++) FX.card(level, bike.position().add(look.scale(2.5 * i)).add(0, 1, 0), look, FX.MAGENTA);
                ram(level, p, bike, look, 6.0F, 1.4);
                sound(level, bike, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0F, 0.9F);
                return new RiderPowers.Used("card_boost", 8);
            }
            case NINE_STRIKER -> {
                bike.boost(25);
                ram(level, p, bike, look, 10.0F, 2.0);
                FX.particle(level, ParticleTypes.EXPLOSION, bike.position().add(look.scale(2)).add(0, 1, 0), 1, 0, 0);
                FX.dust(level, 0xAAFF96, 1.4F, bike.position().add(0, 1, 0), 25, 0.8);
                sound(level, bike, SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 1.5F);
                return new RiderPowers.Used("smash_charge", 8);
            }
            case CIPHER_RUNNER -> {
                int n = 0;
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, bike.getBoundingBox().inflate(16),
                        e -> e != p && e != bike && e.isAlive() && e instanceof Enemy)) {
                    Vec3 to = e.position().subtract(bike.position());
                    if (horizontal(to).dot(look) < 0.5) continue;
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 9));
                    e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0));
                    FX.line(level, bike.position().add(0, 1, 0), FX.center(e), 0xDCF0FF, 0.7F, 0.5);
                    n++;
                }
                if (n == 0) return null;
                sound(level, bike, SoundEvents.BEACON_ACTIVATE, 0.8F, 2.0F);
                return new RiderPowers.Used("code_lock", 10);
            }
            case MACHINE_FORGER -> {
                Direction d = p.getDirection();
                BlockPos start = bike.blockPosition().below().relative(d, 2);
                int placed = 0;
                for (int i = 0; i < 14; i++) {
                    BlockPos c = start.relative(d, i);
                    for (int s = -1; s <= 1; s++) {
                        if (TempBlocks.place(level, c.relative(d.getClockWise(), s), Blocks.IRON_BLOCK.defaultBlockState(), 300)) placed++;
                    }
                }
                if (placed == 0) return null;
                FX.dust(level, 0x78BEFF, 1.2F, bike.position().add(look.scale(6)), 40, 3.0);
                sound(level, bike, SoundEvents.ANVIL_PLACE, 0.7F, 1.3F);
                return new RiderPowers.Used("forge_bridge", 12);
            }
            case MYCO_HURRICANE -> {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, bike.getBoundingBox().inflate(6),
                        e -> e != p && e != bike && e.isAlive())) {
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 1));
                    e.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 80, 0));
                }
                FX.particle(level, ParticleTypes.SPORE_BLOSSOM_AIR, bike.position().add(0, 1, 0), 100, 3.0, 0.02);
                sound(level, bike, SoundEvents.FIRE_EXTINGUISH, 0.8F, 1.4F);
                return new RiderPowers.Used("spore_cloud", 10);
            }
            case MIRROR_SHOOTER -> {
                p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 160, 0, false, false, true));
                bike.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 160, 0, false, false));
                for (Mob m : level.getEntitiesOfClass(Mob.class, bike.getBoundingBox().inflate(24), m -> m.getTarget() == p)) {
                    m.setTarget(null);
                }
                FX.particle(level, ParticleTypes.END_ROD, bike.position().add(0, 1, 0), 40, 1.2, 0.1);
                sound(level, bike, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F, 1.6F);
                return new RiderPowers.Used("mirage_ride", 16);
            }
            case WARDEN_INTERCEPTOR -> {
                bike.boost(60);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, bike.getBoundingBox().inflate(14),
                        e -> e != p && e != bike && e.isAlive() && e instanceof Enemy)) {
                    if (horizontal(e.position().subtract(bike.position())).dot(look) < 0.4) continue;
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 3));
                }
                sound(level, bike, SoundEvents.BELL_BLOCK, 1.0F, 1.8F);
                return new RiderPowers.Used("pursuit", 10);
            }
            case PSYGA_GLIDER -> {
                bike.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 30, 2, false, false));
                bike.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 140, 0, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160, 0, false, false, true));
                bike.boost(80);
                FX.particle(level, ParticleTypes.CLOUD, bike.position(), 30, 0.6, 0.05);
                sound(level, bike, SoundEvents.PHANTOM_FLAP, 1.2F, 1.4F);
                return new RiderPowers.Used("sky_glide", 12);
            }
            case COBALT_SIDEWINDER -> {   // el canon del sidecar: rafaga de seis disparos dorados hacia delante
                for (int i = 0; i < 6; i++) {
                    final int k = i;
                    com.draxer.dexrider.rider.Scheduler.later(p, 1 + i * 3, () -> {
                        Vec3 dir = FX.yaw(horizontal(p.getLookAngle()), (k % 2 == 0 ? -1 : 1) * (2 + k));
                        FX.beam(level, p, dir, 26.0, 6.0F, 0xFFC434, 1.0F, false);
                    });
                }
                sound(level, bike, SoundEvents.FIREWORK_ROCKET_BLAST, 1.2F, 1.2F);
                return new RiderPowers.Used("sidecar_barrage", 9);
            }
            case ORGA_MONARCH -> {
                bike.boost(20);
                ram(level, p, bike, look, 16.0F, 3.0);
                FX.dust(level, 0xF2C230, 1.6F, bike.position().add(look.scale(2)).add(0, 1, 0), 30, 1.0);
                sound(level, bike, SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 0.6F);
                return new RiderPowers.Used("royal_charge", 10);
            }
            case PROTO_RACER -> {
                Vec3 v = bike.getDeltaMovement();
                bike.setDeltaMovement(v.x + look.x * 1.2, 1.1, v.z + look.z * 1.2);
                bike.hurtMarked = true;
                bike.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false, true));
                FX.particle(level, ParticleTypes.FLAME, bike.position(), 30, 0.4, 0.05);
                sound(level, bike, SoundEvents.FIRECHARGE_USE, 1.0F, 0.8F);
                return new RiderPowers.Used("proto_jump", 6);
            }
        }
        return null;
    }

    /** Cada tick mientras alguien conduce. */
    public static void ridingTick(ServerLevel level, DexMachineEntity bike) {
        boolean moving = bike.getDeltaMovement().horizontalDistanceSqr() > 0.02;
        bike.resetFallDistance();
        switch (bike.kind()) {
            case MYCO_HURRICANE -> {
                if (moving && bike.tickCount % 3 == 0)
                    FX.particle(level, ParticleTypes.SPORE_BLOSSOM_AIR, bike.position().add(0, 0.4, 0), 3, 0.3, 0.01);
                if (bike.tickCount % 20 == 0) {
                    // la estela extiende la red sensorial: lo que pasa cerca brilla
                    for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, bike.getBoundingBox().inflate(10),
                            e -> e != bike && e.isAlive() && e != bike.getControllingPassenger())) {
                        e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false));
                    }
                }
            }
            case MIRROR_SHOOTER -> {
                if (moving && bike.tickCount % 2 == 0)
                    FX.particle(level, ParticleTypes.END_ROD, bike.position().add(0, 0.6, 0), 1, 0.3, 0.01);
            }
            case PROTO_RACER -> {
                if (moving && bike.tickCount % 3 == 0)
                    FX.particle(level, ParticleTypes.SMALL_FLAME, bike.position().add(0, 0.7, 0).subtract(bike.getLookAngle().scale(1.2)), 1, 0.05, 0.01);
            }
            case NINE_STRIKER -> {
                if (moving && bike.tickCount % 4 == 0) FX.dust(level, 0xAAFF96, 0.7F, bike.position().add(0, 0.5, 0), 2, 0.3);
            }
            case CIPHER_RUNNER -> {
                if (moving && bike.tickCount % 4 == 0) FX.dust(level, 0xDCF0FF, 0.6F, bike.position().add(0, 0.5, 0), 2, 0.3);
            }
            default -> {
                if (moving && bike.tickCount % 2 == 0)
                    FX.particle(level, ParticleTypes.SMOKE, bike.position().add(0, 0.3, 0).subtract(bike.getLookAngle().scale(1.0)), 1, 0.05, 0.01);
            }
        }
    }

    private static void ram(ServerLevel level, ServerPlayer p, DexMachineEntity bike, Vec3 look, float damage, double push) {
        damage *= bike.levelScale();
        Vec3 from = bike.position().add(0, 0.8, 0);
        Vec3 to = from.add(look.scale(7));
        for (LivingEntity e : FX.alongLine(level, p, from, to, 1.0)) {
            if (e == bike) continue;
            FX.hit(level, p, e, damage);
            FX.push(e, bike.position(), push, 0.5);
        }
    }

    private static Vec3 horizontal(Vec3 v) {
        Vec3 h = new Vec3(v.x, 0, v.z);
        return h.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : h.normalize();
    }

    private static void sound(ServerLevel level, DexMachineEntity bike, net.minecraft.sounds.SoundEvent s, float vol, float pitch) {
        level.playSound(null, bike.getX(), bike.getY(), bike.getZ(), s, SoundSource.PLAYERS, vol, pitch);
    }
}
