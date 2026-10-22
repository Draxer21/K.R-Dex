package com.draxer.dexrider.vehicle;

import com.draxer.dexrider.registry.DexEntities;
import com.draxer.dexrider.registry.DexSounds;
import com.draxer.dexrider.rider.FX;
import com.draxer.dexrider.rider.Transformation;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Vehicle Ride: Dex Machine. Moto que aparece con la carta y se maneja con WASD.
 * Basada en el ejemplo oficial de moto de GeckoLib para 26.1.2.
 *
 *  - Solo la puede montar quien la invoco.
 *  - Es invulnerable y desaparece si nadie la usa durante 30 s, o cuando su duenio se destransforma.
 *  - Una sola Dex Machine por jugador: invocar otra hace desaparecer la anterior.
 *  - Como dice el fanfic, el Vehicle Ride da el conocimiento de conduccion: no hay que aprender nada.
 */
public class DexMachineEntity extends Animal implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // ------------------------------------------------------------------ Vehicle Ride: Scarab (fusion)
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_SCARAB =
            net.minecraft.network.syncher.SynchedEntityData.defineId(DexMachineEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    public static final int SCARAB_TICKS = 2400, SCARAB_REST = 3600;
    /** Cuando puede volver a fusionarse el escarabajo de cada jugador (necesita descansar). */
    private static final Map<UUID, Long> SCARAB_READY = new HashMap<>();
    private int scarabTicks;

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SCARAB, false);
    }

    public boolean scarab() { return this.entityData.get(DATA_SCARAB); }

    /** Solo para fotos/pruebas: muestra el caparazon sin gastar el escarabajo. */
    public void showScarab() {
        this.entityData.set(DATA_SCARAB, true);
        scarabTicks = 200;
    }

    /** Fusiona el escarabajo con esta moto. Devuelve false si todavia esta descansando. */
    public boolean fuseScarab(ServerLevel level, ServerPlayer p) {
        long now = level.getGameTime();
        Long ready = SCARAB_READY.get(p.getUUID());
        if (ready != null && ready > now) {
            p.sendSystemMessage(Component.translatable("message.dexrider.scarab_resting", (ready - now + 19) / 20), true);
            return false;
        }
        this.entityData.set(DATA_SCARAB, true);
        scarabTicks = SCARAB_TICKS;
        FX.dust(level, 0x3CC882, 1.6F, position().add(0, 1, 0), 40, 1.0);
        FX.particle(level, ParticleTypes.END_ROD, position().add(0, 1.2, 0), 20, 0.8, 0.05);
        level.playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.ELYTRA_FLYING, net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.6F);
        level.playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_NETHERITE.value(), net.minecraft.sounds.SoundSource.PLAYERS, 1.2F, 0.8F);
        p.sendSystemMessage(Component.translatable("message.dexrider.scarab_fuse"), true);
        return true;
    }

    private void scarabTick(ServerLevel level) {
        LivingEntity rider = getControllingPassenger();
        if (--scarabTicks <= 0) {
            this.entityData.set(DATA_SCARAB, false);
            if (owner != null) SCARAB_READY.put(owner, level.getGameTime() + SCARAB_REST);
            FX.dust(level, 0x3CC882, 1.2F, position().add(0, 1, 0), 25, 0.8);
            if (rider instanceof ServerPlayer sp) sp.sendSystemMessage(Component.translatable("message.dexrider.scarab_tired"), true);
            return;
        }
        // planeo: cae despacio
        if (!onGround() && getDeltaMovement().y < -0.12) setDeltaMovement(getDeltaMovement().multiply(1, 0.6, 1));
        // protege al piloto
        if (rider != null && tickCount % 20 == 0) rider.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.RESISTANCE, 40, 1, false, false, true));
        // blindaje de embestida
        if (getDeltaMovement().horizontalDistanceSqr() > 0.08 && tickCount % 5 == 0) {
            Vec3 fwd = getLookAngle().multiply(1, 0, 1).normalize();
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().move(fwd.scale(1.2)).inflate(0.4),
                    e -> e != this && e != rider && e.isAlive() && !(e instanceof com.draxer.dexrider.entity.RiderAllyEntity))) {
                e.hurtServer(level, rider instanceof Player pl ? damageSources().playerAttack(pl) : damageSources().mobAttack(this), 6.0F);
                FX.push(e, position(), 1.2, 0.5);
            }
        }
        if (tickCount % 6 == 0) FX.dust(level, 0x3CC882, 0.8F, position().add(0, 0.9, 0), 2, 0.6);
    }

    private static final Map<UUID, DexMachineEntity> ACTIVE = new HashMap<>();
    private static final int IDLE_DESPAWN_TICKS = 600;

    @Nullable private UUID owner;
    private int idleTicks;
    private final BikeKind kind;
    private int boostTicks;

    public DexMachineEntity(EntityType<? extends Animal> type, Level level, BikeKind kind) {
        super(type, level);
        this.kind = kind;
        setInvulnerable(true);
    }

    public BikeKind kind() {
        return kind;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    /** Impulso temporal (habilidades de moto). */
    public void boost(int ticks) {
        this.boostTicks = ticks;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40)
                .add(Attributes.MOVEMENT_SPEED, 0.35)
                .add(Attributes.STEP_HEIGHT, 1.1);
    }

    // ------------------------------------------------------------------ invocar / retirar

    public static void summon(ServerLevel level, ServerPlayer player) {
        summon(level, player, BikeKind.DEX_MACHINE);
    }

    public static void summon(ServerLevel level, ServerPlayer player, BikeKind kind) {
        dismissFor(player);
        DexMachineEntity machine = DexEntities.bike(kind).get().create(level, EntitySpawnReason.TRIGGERED);
        if (machine == null) return;
        Vec3 look = player.getLookAngle();
        Vec3 pos = player.position().add(look.x * 1.5, 0, look.z * 1.5);
        machine.setPos(pos.x, player.getY(), pos.z);
        machine.setYRot(player.getYRot());
        machine.yBodyRot = player.getYRot();
        machine.owner = player.getUUID();
        level.addFreshEntity(machine);
        ACTIVE.put(player.getUUID(), machine);

        player.startRiding(machine);
        FX.cardPath(level, player.position().add(0, 1, 0), pos.add(0, 0.6, 0), 2, FX.GOLD);
        FX.particle(level, ParticleTypes.CLOUD, pos.add(0, 0.5, 0), 20, 0.6, 0.05);
        DexSounds.play(level, player, DexSounds.ARMED_RIDE, 1.2F, 0.8F);
        DexSounds.play(level, player, DexSounds.VOICE_ARMED_RIDE, 1.0F, 1.0F);
        player.sendSystemMessage(Component.translatable("message.dexrider.vehicle_ride",
                kind.displayName()), true);
    }

    @Nullable
    public static DexMachineEntity activeFor(ServerPlayer player) {
        DexMachineEntity m = ACTIVE.get(player.getUUID());
        return m != null && m.isAlive() ? m : null;
    }

    public static void dismissFor(ServerPlayer player) {
        DexMachineEntity old = ACTIVE.remove(player.getUUID());
        if (old != null && old.isAlive()) old.vanish();
    }

    private void vanish() {
        if (level() instanceof ServerLevel server) {
            FX.particle(server, ParticleTypes.CLOUD, position().add(0, 0.5, 0), 15, 0.5, 0.05);
        }
        ejectPassengers();
        discard();
    }

    // ------------------------------------------------------------------ comportamiento

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel)) return;
        if (boostTicks > 0) boostTicks--;
        if (scarab() && level() instanceof ServerLevel sl) scarabTick(sl);
        if (isVehicle()) {
            idleTicks = 0;
            if (level() instanceof ServerLevel server) BikeAbilities.ridingTick(server, this);
            if (getDeltaMovement().horizontalDistanceSqr() > 0.02 && level() instanceof ServerLevel server && tickCount % 2 == 0) {
                FX.particle(server, ParticleTypes.SMOKE, position().add(0, 0.3, 0).subtract(getLookAngle().scale(1.0)), 1, 0.05, 0.01);
            }
            return;
        }
        idleTicks++;
        boolean ownerGone = owner == null || !(level().getPlayerByUUID(owner) instanceof Player p) || !Transformation.isTransformed(p);
        if (idleTicks > IDLE_DESPAWN_TICKS || (ownerGone && idleTicks > 40)) {
            if (owner != null) ACTIVE.remove(owner, this);
            vanish();
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isVehicle() && (owner == null || owner.equals(player.getUUID()))) {
            player.startRiding(this);
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    /** Conduccion: gira con la camara del piloto, W acelera, S frena/retrocede lento. */
    @Override
    public void travel(Vec3 pos) {
        if (!isAlive()) return;
        if (isVehicle() && getControllingPassenger() instanceof LivingEntity rider) {
            this.yRotO = getYRot();
            this.xRotO = getXRot();
            setYRot(rider.getYRot());
            setXRot(0);
            setRot(getYRot(), getXRot());
            this.yBodyRot = getYRot();
            this.yHeadRot = this.yBodyRot;

            float x = rider.xxa * 0.3F;
            float z = rider.zza;
            if (z <= 0) z *= 0.25F;
            setSpeed(kind.speed * (boostTicks > 0 ? 1.8F : 1.0F));
            super.travel(new Vec3(x, pos.y, z));
        } else {
            super.travel(pos);
        }
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof LivingEntity entity ? entity : null;
    }

    @Override
    public boolean canSimulateMovement() {
        return true;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState block) {}

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // ruedas girando mientras se mueve
        controllers.add(new com.geckolib.animation.AnimationController<>("Drive", 0, test ->
                test.isMoving() ? test.setAndContinue(com.geckolib.constant.DefaultAnimations.DRIVE) : com.geckolib.animation.object.PlayState.STOP));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
