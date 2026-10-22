package com.draxer.dexrider.entity;

import com.draxer.dexrider.registry.DexEntities;
import com.draxer.dexrider.rider.FX;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.constant.DefaultAnimations;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Dai-Shocker (del fanfic): humanos modificados sin su consentimiento. Disenos propios.
 *
 *  SOLDIER : combatiente con mascara de hueso y visor de una ranura. Van en grupo.
 *  MANTIS  : Kaijin Mantis, guadanas en los antebrazos; salta sobre su objetivo y sus cortes hacen sangrar.
 *  RHINO   : Kaijin Rinoceronte, lento y blindado; embiste en linea recta y lanza por los aires.
 *  CHIMERA : la Quimera (jefe): mezcla de las dos, con alas. Salta y aplasta, embiste, barre con la guadana
 *            y, herida, llama a combatientes. Barra de jefe.
 */
public class KaijinEntity extends Monster implements GeoEntity {
    public enum Kind {
        SOLDIER("kaijin_soldier", 26, 5, 0.28, 4, 1.0F),
        MANTIS("kaijin_mantis", 40, 7, 0.32, 6, 1.0F),
        RHINO("kaijin_rhino", 70, 9, 0.24, 12, 1.15F),
        CHIMERA("kaijin_chimera", 360, 14, 0.29, 16, 1.5F),
        /** El Arquitecto: la Dai-Shocker que no es la casa de la montania (ver ArchitectEntity). */
        ARCHITECT("kaijin_architect", 560, 15, 0.27, 18, 1.35F);

        public final String id;
        final double health, damage, speed, armor;
        public final float scale;

        Kind(String id, double health, double damage, double speed, double armor, float scale) {
            this.id = id; this.health = health; this.damage = damage; this.speed = speed; this.armor = armor; this.scale = scale;
        }

        public boolean boss() { return this == CHIMERA || this == ARCHITECT; }
    }

    private static final EntityDataAccessor<Boolean> DATA_LEAP = SynchedEntityData.defineId(KaijinEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Kind kind;
    private final ServerBossEvent boss;
    protected int special = 60;
    private int charge;          // ticks de embestida en curso
    private Vec3 chargeDir = Vec3.ZERO;
    private int summonCooldown;
    private int leapAge;

    public KaijinEntity(EntityType<? extends Monster> type, Level level, Kind kind) {
        super(type, level);
        this.kind = kind;
        this.xpReward = kind == Kind.ARCHITECT ? 300 : kind == Kind.CHIMERA ? 120 : kind == Kind.SOLDIER ? 6 : 15;
        this.boss = kind.boss()
                ? new ServerBossEvent(java.util.UUID.randomUUID(), Component.translatable("entity.dexrider." + kind.id),
                        kind == Kind.ARCHITECT ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.PURPLE,
                        kind == Kind.ARCHITECT ? BossEvent.BossBarOverlay.NOTCHED_6 : BossEvent.BossBarOverlay.NOTCHED_10)
                : null;
    }

    public Kind kind() { return kind; }

    public static AttributeSupplier.Builder createAttributes(Kind kind) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, kind.health)
                .add(Attributes.ATTACK_DAMAGE, kind.damage)
                .add(Attributes.MOVEMENT_SPEED, kind.speed)
                .add(Attributes.ARMOR, kind.armor)
                .add(Attributes.FOLLOW_RANGE, kind.boss() ? 48 : 32)
                .add(Attributes.KNOCKBACK_RESISTANCE, kind == Kind.ARCHITECT ? 1.0 : kind == Kind.CHIMERA ? 0.9 : kind == Kind.RHINO ? 0.7 : 0.1)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LEAP, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, false));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, KaijinEntity.class).setAlertOthers());
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, RiderAllyEntity.class, true));
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, false));
    }

    // ------------------------------------------------------------------ jefe

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (boss != null) boss.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        if (boss != null) boss.removePlayer(player);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return !kind.boss() && super.removeWhenFarAway(distance);
    }

    // ------------------------------------------------------------------ comportamiento

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (boss != null) boss.setProgress(getHealth() / getMaxHealth());
        if (summonCooldown > 0) summonCooldown--;
        if (charge > 0) { chargeTick(level); return; }
        if (entityData.get(DATA_LEAP) && ++leapAge > 6 && onGround()) land(level);
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) return;
        if (--special > 0) return;
        double d = distanceTo(target);
        switch (kind) {
            case SOLDIER -> special = 200;
            case MANTIS -> {
                if (d > 3.5 && d < 12) leap(target, 0.9); else special = 20;
                special = 80 + random.nextInt(40);
            }
            case RHINO -> {
                if (d > 5 && d < 16) startCharge(level, target);
                special = d > 5 && d < 16 ? 140 + random.nextInt(40) : 20;
            }
            case CHIMERA -> chimeraSpecial(level, target, d);
            case ARCHITECT -> bossSpecial(level, target, d);
        }
    }

    /** Ataques especiales de los jefes con clase propia. */
    protected void bossSpecial(ServerLevel level, LivingEntity target, double d) { special = 40; }

    protected boolean bossBarVisible() { return boss != null; }
    protected void setBossName(Component name) { if (boss != null) boss.setName(name); }

    /** La Quimera custodiaba la frecuencia: deja caer el Rastreador de la Senal. */
    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        super.die(source);
        if (kind == Kind.CHIMERA && level() instanceof ServerLevel level) {
            level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, getX(), getY() + 0.5, getZ(),
                    new net.minecraft.world.item.ItemStack(com.draxer.dexrider.registry.DexItems.SIGNAL_TRACKER.get())));
        }
    }

    protected void leap(LivingEntity target, double power) {
        Vec3 to = target.position().subtract(position());
        Vec3 h = new Vec3(to.x, 0, to.z).normalize();
        setDeltaMovement(h.scale(power + to.horizontalDistance() * 0.06).add(0, 0.55 + power * 0.15, 0));
        hurtMarked = true;
        entityData.set(DATA_LEAP, true);
        leapAge = 0;
        playSound(SoundEvents.PHANTOM_FLAP, 1.0F, 0.8F);
    }

    /** Al caer de un salto: la Mantis corta, la Quimera aplasta en area. */
    private void land(ServerLevel level) {
        entityData.set(DATA_LEAP, false);
        double r = kind == Kind.CHIMERA ? 4.5 : 2.2;
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * (kind == Kind.CHIMERA ? 1.0F : 0.8F);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r, 1.5, r),
                e -> e != this && e.isAlive() && !(e instanceof KaijinEntity))) {
            e.hurtServer(level, damageSources().mobAttack(this), dmg);
            if (kind == Kind.MANTIS) e.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
            else FX.push(e, position(), 0.9, 0.6);
        }
        if (kind == Kind.CHIMERA) {
            level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.2, getZ(), 3, 1.5, 0.2, 1.5, 0);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2F, 0.7F);
        } else {
            FX.particle(level, ParticleTypes.SWEEP_ATTACK, FX.center(this), 3, 0.8, 0);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.0F, 1.3F);
        }
    }

    protected void startCharge(ServerLevel level, LivingEntity target) {
        Vec3 to = target.position().subtract(position());
        chargeDir = new Vec3(to.x, 0, to.z).normalize();
        charge = kind == Kind.CHIMERA ? 24 : 30;
        getNavigation().stop();
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.0F, kind == Kind.CHIMERA ? 0.7F : 1.1F);
    }

    private void chargeTick(ServerLevel level) {
        charge--;
        setYRot((float) (Math.atan2(-chargeDir.x, chargeDir.z) * 180 / Math.PI));
        yBodyRot = getYRot();
        setDeltaMovement(chargeDir.scale(kind == Kind.CHIMERA ? 0.75 : 0.65).add(0, getDeltaMovement().y, 0));
        hurtMarked = true;
        if (tickCount % 2 == 0) FX.particle(level, ParticleTypes.CLOUD, position(), 3, 0.4, 0.02);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.6),
                e -> e != this && e.isAlive() && !(e instanceof KaijinEntity))) {
            e.hurtServer(level, damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.3F);
            FX.push(e, position(), 1.8, 0.8);
            charge = 0;
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.8F, 0.6F);
        }
        if (horizontalCollision) {
            charge = 0;
            addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 3));   // se aturde contra la pared
            FX.particle(level, ParticleTypes.CRIT, FX.center(this), 12, 0.5, 0.2);
        }
    }

    private void chimeraSpecial(ServerLevel level, LivingEntity target, double d) {
        boolean wounded = getHealth() < getMaxHealth() * 0.5F;
        if (wounded && summonCooldown <= 0) {
            summonCooldown = 600;
            for (int i = 0; i < 3; i++) {
                KaijinEntity s = DexEntities.KAIJIN.get(Kind.SOLDIER).get().create(level, EntitySpawnReason.REINFORCEMENT);
                if (s == null) continue;
                double a = i * Math.PI * 2 / 3;
                s.setPos(getX() + Math.cos(a) * 3, getY(), getZ() + Math.sin(a) * 3);
                s.setTarget(target);
                level.addFreshEntity(s);
                FX.particle(level, ParticleTypes.LARGE_SMOKE, s.position().add(0, 1, 0), 15, 0.4, 0.02);
            }
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.5F, 0.7F);
            special = 40;
            return;
        }
        int pick = random.nextInt(3);
        if (pick == 0 && d > 4 && d < 18) { leap(target, 1.1); special = wounded ? 60 : 90; }
        else if (pick == 1 && d > 5 && d < 18) { startCharge(level, target); special = wounded ? 70 : 100; }
        else if (d < 5) {   // barrido de guadana
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(4, 1, 4),
                    e -> e != this && e.isAlive() && !(e instanceof KaijinEntity))) {
                e.hurtServer(level, damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
                e.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1));
                FX.push(e, position(), 1.0, 0.3);
            }
            FX.particle(level, ParticleTypes.SWEEP_ATTACK, FX.center(this), 8, 2.0, 0);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.5F, 0.6F);
            special = wounded ? 40 : 60;
        } else special = 20;
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float multiplier, net.minecraft.world.damagesource.DamageSource source) {
        return false;   // saltan desde muy alto sin hacerse dano
    }

    // ------------------------------------------------------------------ GeckoLib

    private static final com.geckolib.animation.RawAnimation WALK = com.geckolib.animation.RawAnimation.begin().thenLoop("move.walk");
    private static final com.geckolib.animation.RawAnimation IDLE = com.geckolib.animation.RawAnimation.begin().thenLoop("misc.idle.weapon");
    private static final com.geckolib.animation.RawAnimation LEAP = com.geckolib.animation.RawAnimation.begin().thenLoop("attack.kick");
    private static final com.geckolib.animation.RawAnimation HIT = com.geckolib.animation.RawAnimation.begin().thenPlay("misc.hit");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new com.geckolib.animation.AnimationController<>("main", 4, test -> {
            if (this.entityData.get(DATA_LEAP)) return test.setAndContinue(LEAP);
            if (this.hurtTime > 0) return test.setAndContinue(HIT);
            if (test.isMoving()) return test.setAndContinue(WALK);
            return test.setAndContinue(IDLE);
        }), DefaultAnimations.genericAttackAnimation(DefaultAnimations.ATTACK_PUNCH));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
