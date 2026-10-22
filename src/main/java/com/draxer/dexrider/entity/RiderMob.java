package com.draxer.dexrider.entity;

import com.draxer.dexrider.registry.DexSounds;
import com.draxer.dexrider.rider.FX;
import com.draxer.dexrider.rider.KamenRide;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.constant.DefaultAnimations;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import com.draxer.dexrider.rider.RiderDialogue;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Base de los Riders que no son el jugador (ecos hostiles y aliados): pelean como un Rider de verdad.
 *
 *  - Cuerpo a cuerpo normal (MeleeAttackGoal) y, cada pocos segundos, su ataque especial.
 *  - ARMA: los que tienen arma (Cipher Edge, Prism Edge, Module Driver, Forge Lance) la sacan y la guardan
 *    por fases; con el arma pegan mas y sus golpes tienen efecto propio. El arma se ve en el modelo (hueso "weapon").
 *  - RIDER KICK: cuando el objetivo esta tocado (o cada tanto), salta, se queda un instante en el aire con
 *    una fila de cartas apuntando al objetivo y cae en picada atravesandolas. Impacto con explosion.
 *
 * Las subclases deciden a quien NO danar (isFriendlyTo) y cuanto escala su poder (powerScale).
 */
public abstract class RiderMob extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<Boolean> DATA_WEAPON = SynchedEntityData.defineId(RiderMob.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_CIVILIAN = SynchedEntityData.defineId(RiderMob.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_FORM = SynchedEntityData.defineId(RiderMob.class, EntityDataSerializers.INT);
    /** En plena Rider Kick (para la animacion de patada, que corre en el cliente). */
    private static final EntityDataAccessor<Boolean> DATA_KICK = SynchedEntityData.defineId(RiderMob.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    protected final KamenRide rider;

    protected int specialCooldown = 80;
    protected int weaponTimer = 200;
    protected int kickCooldown = 300;
    private int kickPhase;      // 0 nada, 1 subiendo/en el aire, 2 cayendo
    private int kickTicks;
    @Nullable private LivingEntity kickTarget;
    @Nullable private LivingEntity lastTarget;
    private boolean saidHurt;
    private int calmTicks;
    private int idleTalk = 600;

    protected RiderMob(EntityType<? extends PathfinderMob> type, Level level, KamenRide rider) {
        super(type, level);
        this.rider = rider;
    }

    public KamenRide rider() {
        return rider;
    }

    public static AttributeSupplier.Builder createRiderAttributes(double health, double damage) {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, health)
                .add(Attributes.ATTACK_DAMAGE, damage)
                .add(Attributes.ARMOR, 10)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_WEAPON, false);
        builder.define(DATA_CIVILIAN, false);
        builder.define(DATA_FORM, 0);
        builder.define(DATA_KICK, false);
    }

    /** Forma propia de Momo o Izuku (ForgeForms / NineForms; 0 = base). El jugador no las usa. */
    public int riderForm() {
        return this.entityData.get(DATA_FORM);
    }

    protected void setRiderForm(int form) {
        this.entityData.set(DATA_FORM, form);
    }

    private int forgeTimer = 160;

    /** Forge cambia de mitades durante la pelea (y vuelve a la base cuando se calma). */
    private void forgeShift(ServerLevel level, boolean fighting) {
        if (rider != KamenRide.FORGE || isCivilian()) return;
        if (!fighting) {
            if (riderForm() != 0 && tickCount % 200 == 0) setRiderForm(0);
            return;
        }
        if (--forgeTimer > 0) return;
        forgeTimer = 240 + getRandom().nextInt(160);
        int next;
        do { next = getRandom().nextInt(9); } while (next == riderForm());
        setRiderForm(next);
        int r = com.draxer.dexrider.rider.ForgeForms.rightColor(com.draxer.dexrider.rider.ForgeForms.right(next));
        int l = com.draxer.dexrider.rider.ForgeForms.leftColor(com.draxer.dexrider.rider.ForgeForms.left(next));
        FX.ring(level, this, r, 1.2, 4);
        FX.ring(level, this, l, 0.8, 4);
        level.playSound(null, getX(), getY(), getZ(), DexSounds.HENSHIN.get(), SoundSource.NEUTRAL, 0.8F, 1.3F);
        RiderDialogue.say(level, this, rider, RiderDialogue.Line.FORM_CHANGE, 60,
                com.draxer.dexrider.rider.ForgeForms.formName(next));
    }

    // ------------------------------------------------------------------ Cipher (Takumi): Accel y Blaster
    private int accelTicks;
    /** Uso el Accel en esta pelea (al vencerlo asi, Takumi te presta el reloj). */
    protected boolean usedAccel;

    /** El Blaster lo guarda Genzo: solo el Takumi amigo lo usa, y solo si Genzo tambien es tu amigo. */
    protected boolean blasterAllowed() {
        return false;
    }

    /** Cuando se le acaba el Accel (el amigo te presta el reloj). */
    protected void onAccelEnd(ServerLevel level) {
    }

    private void cipherShift(ServerLevel level, boolean fighting) {
        if (rider != KamenRide.CIPHER || isCivilian()) return;
        int v = riderForm();
        if (v == 1) {
            if (--accelTicks <= 0) {
                setRiderForm(fighting && blasterAllowed() ? 2 : 0);
                addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 1));
                addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
                FX.particle(level, ParticleTypes.LARGE_SMOKE, FX.center(this), 20, 0.4, 0.03);
                RiderDialogue.force(level, this, rider, RiderDialogue.Line.ACCEL_END);
                onAccelEnd(level);
                return;
            }
            addEffect(new MobEffectInstance(MobEffects.SPEED, 10, 5, false, false));
            if (specialCooldown > 12) specialCooldown = 12;
            if (tickCount % 5 == 0) {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(20),
                        e -> e != this && e.isAlive() && !isFriendlyTo(e))) {
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, 4, false, false));
                }
            }
            if (tickCount % 2 == 0) FX.dust(level, 0xFF463C, 1.0F, position().add(0, 1, 0), 3, 0.3);
            return;
        }
        if (!fighting) {
            if (v != 0 && tickCount % 100 == 0) setRiderForm(0);
            usedAccel = usedAccel && getTarget() != null;
            return;
        }
        // Accel: cuando la pelea se pone fea (una vez por pelea)
        if (!usedAccel && getHealth() < getMaxHealth() * 0.5F) {
            usedAccel = true;
            accelTicks = 200;
            setRiderForm(1);
            FX.ring(level, this, 0xFF463C, 1.3, 7);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.2F, 1.8F);
            RiderDialogue.force(level, this, rider, RiderDialogue.Line.ACCEL);
            return;
        }
        if (v == 0 && blasterAllowed()) {
            setRiderForm(2);
            setWeaponOut(true);
            FX.ring(level, this, 0xFF3628, 1.5, 8);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 1.2F, 0.8F);
            RiderDialogue.say(level, this, rider, RiderDialogue.Line.BLASTER, 200);
        }
        if (v == 2 && !hasWeaponOut()) setWeaponOut(true);
    }

    private int nineTimer = 120;
    private int risingTicks;
    private int risingCooldown = 200;

    /**
     * Izuku cambia de forma segun la pelea (Dragon, Pegasus, Titan) y, apurado, activa One For All: RISING.
     * Las formas con arma la llevan fuera mientras pelea. Al calmarse vuelve a la base.
     */
    private void nineShift(ServerLevel level, boolean fighting) {
        if (rider != KamenRide.NINE) return;
        int v = riderForm();
        if (risingCooldown > 0) risingCooldown--;
        if (tickCount % 20 == 0) NineForms.effects(this, v);
        // fin del Rising
        if (NineForms.rising(v) && --risingTicks <= 0) {
            v = NineForms.encode(NineForms.form(v), false);
            setRiderForm(v);
            risingCooldown = 1200;
            FX.particle(level, ParticleTypes.CLOUD, FX.center(this), 12, 0.4, 0.05);
        }
        if (!fighting) {
            if (v != 0 && tickCount % 200 == 0 && !NineForms.rising(v)) {
                setRiderForm(0);
                setWeaponOut(false);
            }
            return;
        }
        LivingEntity target = getTarget();
        // One For All: Rising
        if (!NineForms.rising(v) && risingCooldown <= 0 && getHealth() < getMaxHealth() * 0.6F) {
            startRising(level);
            v = riderForm();
        }
        if (--nineTimer <= 0 && target != null) {
            nineTimer = 260 + getRandom().nextInt(160);
            NineForms.Form next = NineForms.choose(this, target, NineForms.form(v));
            if (next != NineForms.form(v)) {
                v = NineForms.encode(next, NineForms.rising(v));
                setRiderForm(v);
                FX.ring(level, this, NineForms.color(v), 1.2, 5);
                level.playSound(null, getX(), getY(), getZ(), DexSounds.HENSHIN.get(), SoundSource.NEUTRAL, 0.8F, 1.2F);
                RiderDialogue.say(level, this, rider, RiderDialogue.Line.FORM_CHANGE, 60, NineForms.formName(v));
            }
        }
        boolean armed = NineForms.form(v) != NineForms.Form.BASE;
        if (armed != hasWeaponOut() && kickPhase == 0) setWeaponOut(armed);
    }

    private void startRising(ServerLevel level) {
        setRiderForm(NineForms.encode(NineForms.form(riderForm()), true));
        risingTicks = 600;
        FX.ring(level, this, 0xF2C230, 1.6, 8);
        FX.ring(level, this, 0xAAFF96, 1.1, 8);
        FX.particle(level, ParticleTypes.ELECTRIC_SPARK, FX.center(this), 40, 0.6, 0.3);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 1.2F, 1.6F);
        RiderDialogue.force(level, this, rider, RiderDialogue.Line.RISING);
    }

    /** Multiplicador de las formas propias (Rising de Izuku, sintonia de Momo). */
    public float formScale() {
        int v = riderForm();
        if (rider == KamenRide.NINE) return NineForms.scale(v);
        if (rider == KamenRide.FORGE && v != 0 && com.draxer.dexrider.rider.ForgeForms.sync(v)) return 1.25F;
        if (rider == KamenRide.CIPHER) return v == 1 ? 1.5F : v == 2 ? 1.4F : 1.0F;
        return 1.0F;
    }

    private boolean isRising() {
        return rider == KamenRide.NINE && NineForms.rising(riderForm());
    }

    private int kickColor() {
        return rider == KamenRide.NINE ? NineForms.color(riderForm()) : rider.color;
    }

    public boolean hasWeaponOut() {
        return this.entityData.get(DATA_WEAPON);
    }

    protected void setWeaponOut(boolean out) {
        this.entityData.set(DATA_WEAPON, out);
    }

    public boolean isCivilian() {
        return this.entityData.get(DATA_CIVILIAN);
    }

    public void setCivilian(boolean civilian) {
        this.entityData.set(DATA_CIVILIAN, civilian);
    }

    /** Los OC de la familia Kagami pueden quitarse el traje en este mundo (su Rider System se lo permite). */
    public static boolean canDetransform(KamenRide rider) {
        return rider == KamenRide.CIPHER || rider == KamenRide.WARDEN || rider == KamenRide.PSYGA || rider == KamenRide.ORGA;
    }

    public static boolean hasWeapon(KamenRide rider) {
        return rider == KamenRide.CIPHER || rider == KamenRide.MIRAGE || rider == KamenRide.PROTO || rider == KamenRide.FORGE
                || rider == KamenRide.WARDEN || rider == KamenRide.ORGA || rider == KamenRide.SPORE || rider == KamenRide.KAIXA;
    }

    /** No dana a estos (al dueno, a otros aliados, a otros ecos...). */
    public abstract boolean isFriendlyTo(LivingEntity other);

    /** Multiplicador de dano de sus ataques especiales. */
    protected float powerScale() {
        return 1.0F;
    }

    // ================================================================== combate

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (!isCivilian()) ambientFx(level);
        talkAndForm(level);
        if (isCivilian()) return;
        if (kickPhase > 0) {
            kickTick(level);
            return;
        }
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive() || isFriendlyTo(target)) {
            if (hasWeaponOut() && tickCount % 40 == 0) setWeaponOut(false);
            return;
        }
        // arma por fases
        if (hasWeapon(rider) && --weaponTimer <= 0) {
            boolean out = !hasWeaponOut();
            setWeaponOut(out);
            weaponTimer = out ? 240 + getRandom().nextInt(120) : 120 + getRandom().nextInt(80);
            if (out) {
                FX.card(level, FX.center(this).add(getLookAngle()), getLookAngle(), rider.color);
                level.playSound(null, getX(), getY(), getZ(), DexSounds.ARMED_RIDE.get(), SoundSource.HOSTILE, 0.8F, 1.1F);
            }
        }
        // Rider Kick
        if (--kickCooldown <= 0 && distanceTo(target) < 18 && hasLineOfSight(target)) {
            boolean hurt = target.getHealth() < target.getMaxHealth() * 0.5F;
            if (hurt || getRandom().nextInt(4) == 0) {
                startKick(level, target);
                return;
            }
            kickCooldown = 60;
        }
        // especial
        if (--specialCooldown <= 0) {
            specialCooldown = 100 + getRandom().nextInt(60);
            RiderNpcAttacks.special(level, this, target);
        }
    }

    // ================================================================== voz y forma civil

    private void talkAndForm(ServerLevel level) {
        LivingEntity target = getTarget();
        boolean fighting = target != null && target.isAlive() && !isFriendlyTo(target);
        // Forma civil (solo los OC): se transforma al pelear y se relaja tras 10 s de calma
        if (canDetransform(rider)) {
            if (fighting && isCivilian()) {
                setCivilian(false);
                FX.ring(level, this, rider.color, 1.2, 6);
                level.playSound(null, getX(), getY(), getZ(), DexSounds.HENSHIN.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
                RiderDialogue.force(level, this, rider, RiderDialogue.Line.HENSHIN);
            } else if (!fighting && !isCivilian() && ++calmTicks > 200 && kickPhase == 0) {
                calmTicks = 0;
                setCivilian(true);
                setWeaponOut(false);
                FX.particle(level, ParticleTypes.END_ROD, FX.center(this), 15, 0.4, 0.05);
                RiderDialogue.say(level, this, rider, RiderDialogue.Line.RELAX, 100);
            }
            if (fighting) calmTicks = 0;
        }
        forgeShift(level, fighting);
        nineShift(level, fighting);
        cipherShift(level, fighting);
        // Al fijar un objetivo nuevo
        if (fighting && target != lastTarget) {
            onNewTarget(level, target);
            saidHurt = false;
        }
        lastTarget = fighting ? target : null;
        if (fighting && !saidHurt && getHealth() < getMaxHealth() * 0.4F) {
            saidHurt = true;
            RiderDialogue.say(level, this, rider, RiderDialogue.Line.HURT, 40);
        }
        // Charla tranquila
        if (!fighting && --idleTalk <= 0) {
            idleTalk = 900 + getRandom().nextInt(1200);
            idleChat(level);
        }
    }

    /** Lo que dice al empezar una pelea (las subclases pueden cambiarlo). */
    protected void onNewTarget(ServerLevel level, LivingEntity target) {
    }

    /** Charla cuando no pasa nada (las subclases deciden). */
    protected void idleChat(ServerLevel level) {
        RiderMob other = nearbyRider(level, 12);
        if (other != null && level.getNearestPlayer(this, 32) != null) {
            // Los que se conocen bien tienen sus propias conversaciones (y se llaman por su nombre)
            KamenRide starter = RiderDialogue.pairStarter(rider, other.rider());
            if (starter != null && getRandom().nextFloat() < 0.6F) {
                RiderMob first = starter == rider ? this : other;
                RiderMob second = first == this ? other : this;
                String key = RiderDialogue.pairLine(getRandom(), first.rider(), second.rider());
                if (key != null && RiderDialogue.sayKey(level, first, first.rider(), key, 200)) {
                    second.replyLater(first, key + ".r");
                }
                return;
            }
            if (RiderDialogue.say(level, this, rider, RiderDialogue.Line.CHAT, 200, RiderDialogue.address(rider, other.rider()))) {
                other.replyLater(this, null);
            }
        }
    }

    private int replyIn = -1;
    @Nullable private RiderMob replyTo;
    @Nullable private String replyKey;

    void replyLater(RiderMob to, @Nullable String key) {
        this.replyTo = to;
        this.replyKey = key;
        this.replyIn = 50;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (replyIn > 0 && --replyIn == 0 && replyTo != null && level() instanceof ServerLevel level) {
            if (replyKey != null) {
                RiderDialogue.forget(this);
                RiderDialogue.sayKey(level, this, rider, replyKey, 0);
            } else {
                RiderDialogue.force(level, this, rider, RiderDialogue.Line.REPLY, RiderDialogue.address(rider, replyTo.rider()));
            }
            replyTo = null;
            replyKey = null;
        }
    }

    @Nullable
    protected RiderMob nearbyRider(ServerLevel level, double range) {
        for (RiderMob m : level.getEntitiesOfClass(RiderMob.class, getBoundingBox().inflate(range),
                m -> m != this && m.isAlive() && m.rider() != rider && m.getClass() == getClass())) {
            return m;
        }
        return null;
    }

    protected void ambientFx(ServerLevel level) {
        if (tickCount % 8 != 0) return;
        switch (rider) {
            case NINE -> FX.dust(level, 0xAAFF96, 0.8F, position().add(0, 1, 0), 2, 0.4);
            case CIPHER -> FX.dust(level, 0xDCF0FF, 0.6F, position().add(0, 1, 0), 2, 0.4);
            case SPORE -> FX.particle(level, ParticleTypes.SPORE_BLOSSOM_AIR, position().add(0, 1.6, 0), 2, 0.5, 0.01);
            case FORGE -> FX.dust(level, 0x78BEFF, 0.6F, position().add(0, 1, 0), 1, 0.4);
            case MIRAGE -> FX.particle(level, ParticleTypes.END_ROD, position().add(0, 1.2, 0), 1, 0.4, 0.01);
            case WARDEN -> FX.dust(level, 0xFF9628, 0.6F, position().add(0, 1.6, 0), 1, 0.3);
            case PSYGA -> FX.particle(level, ParticleTypes.CLOUD, position().add(0, 1.0, 0).subtract(getLookAngle().scale(0.5)), 1, 0.1, 0.01);
            case ORGA -> FX.dust(level, 0xF2C230, 0.7F, position().add(0, 1, 0), 1, 0.4);
            case KAIXA -> FX.dust(level, 0xFFC434, 0.6F, position().add(0, 1, 0), 1, 0.4);
            case PROTO -> FX.particle(level, ParticleTypes.SMALL_FLAME, position().add(0, 1.0, 0).subtract(getLookAngle().scale(0.4)), 1, 0.1, 0.01);
            default -> { }
        }
    }

    /** Los golpes cuerpo a cuerpo con el arma fuera hacen mas y tienen efecto propio. */
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof LivingEntity living && hasWeaponOut()) {
            living.invulnerableTime = 0;
            living.hurtServer(level, damageSources().mobAttack(this), 4.0F * powerScale());
            switch (rider) {
                case CIPHER -> FX.dust(level, 0xDCF0FF, 1.0F, FX.center(living), 8, 0.3);
                case MIRAGE -> living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0));
                case PROTO -> living.igniteForSeconds(3.0F);
                case FORGE -> FX.push(living, position(), 1.0, 0.3);
                case SPORE -> living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2));
                case WARDEN -> living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 4));
                case ORGA -> FX.push(living, position(), 1.4, 0.4);
                case KAIXA -> { if (living.getHealth() < living.getMaxHealth() * 0.5F) living.hurtServer(level, damageSources().mobAttack(this), 3.0F * powerScale()); }
                default -> { }
            }
        }
        return hit;
    }

    // ================================================================== Rider Kick

    private void startKick(ServerLevel level, LivingEntity target) {
        // Izuku a veces enciende One For All para el golpe final
        if (rider == KamenRide.NINE && !isRising() && risingCooldown <= 0 && getRandom().nextFloat() < 0.4F) startRising(level);
        kickTarget = target;
        kickPhase = 1;
        this.entityData.set(DATA_KICK, true);
        kickTicks = 0;
        getNavigation().stop();
        setDeltaMovement(0, 1.1, 0);
        hurtMarked = true;
        FX.dust(level, kickColor(), 1.6F, position(), 25, 0.6);
        DexSounds.play(level, this, DexSounds.FAR_CHARGE, 1.0F, 1.1F);
    }

    private void kickTick(ServerLevel level) {
        kickTicks++;
        resetFallDistance();
        LivingEntity target = kickTarget;
        if (target == null || !target.isAlive()) {
            endKick();
            return;
        }
        getLookControl().setLookAt(target);
        if (kickPhase == 1) {
            if (kickTicks > 8) setDeltaMovement(getDeltaMovement().multiply(0.3, 0.2, 0.3));   // se queda arriba
            if (kickTicks == 14) {
                // fila de cartas hacia el objetivo
                FX.cardPath(level, position().add(0, 0.5, 0), FX.center(target), 4, kickColor());
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 1.4F, 1.3F);
            }
            if (kickTicks >= 20) {
                kickPhase = 2;
                kickTicks = 0;
            }
            return;
        }
        // Pegasus (Izuku): su final es un disparo de precision con el Pegasus Bow, no una patada
        if (rider == KamenRide.NINE && NineForms.form(riderForm()) == NineForms.Form.PEGASUS) {
            boolean hit = hasLineOfSight(target) && getRandom().nextFloat() < 0.8F;
            Vec3 at = hit ? FX.center(target)
                    : FX.center(target).add((getRandom().nextDouble() - 0.5) * 6, -1.0, (getRandom().nextDouble() - 0.5) * 6);
            FX.line(level, getEyePosition(), at, kickColor(), 1.6F, 0.3);
            impact(level, target, at, hit);
            endKick();
            return;
        }
        Vec3 dir = FX.center(target).subtract(position()).normalize();
        setDeltaMovement(dir.scale(1.7));
        hurtMarked = true;
        FX.dust(level, kickColor(), 1.3F, position().add(0, 0.6, 0), 5, 0.25);
        if (distanceTo(target) < 2.2 || (onGround() && kickTicks > 4) || kickTicks > 25) {
            boolean hit = distanceTo(target) < 3.5;
            impact(level, target, hit ? FX.center(target) : position(), hit);
            endKick();
        }
    }

    /** Impacto del golpe final. hit = alcanzo al objetivo; si fallo en Rising, rompe bloques donde cae. */
    private void impact(ServerLevel level, LivingEntity target, Vec3 c, boolean hit) {
        boolean rising = isRising();
        float dmg = 16.0F * powerScale() * formScale();
        double radius = rising ? 5.5 : 3.0;
        if (hit) {
            target.invulnerableTime = 0;
            target.hurtServer(level, damageSources().mobAttack(this), dmg);
            FX.push(target, position(), rising ? 2.6 : 1.6, rising ? 0.9 : 0.6);
        }
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(radius),
                e -> e != this && e != target && e.isAlive() && !isFriendlyTo(e));
        for (LivingEntity e : near) {
            e.invulnerableTime = 0;
            e.hurtServer(level, damageSources().mobAttack(this), dmg * (rising ? 0.6F : 0.4F));
            FX.push(e, c, rising ? 1.8 : 1.0, 0.4);
        }
        FX.particle(level, ParticleTypes.EXPLOSION_EMITTER, c, rising ? 4 : 1, rising ? 2.0 : 0, 0);
        FX.dust(level, kickColor(), 2.0F, c, rising ? 120 : 40, rising ? 3.0 : 1.4);
        if (rising) {
            FX.particle(level, ParticleTypes.ELECTRIC_SPARK, c, 60, 2.5, 0.4);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3.0F, 0.6F);
            if (!hit) breakBlocks(level, c, 3);
        }
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.6F, 0.9F);
        DexSounds.play(level, this, DexSounds.FAR_IMPACT, 1.2F, 1.0F);
        swing(InteractionHand.MAIN_HAND);
    }

    /** Rising que falla: el golpe abre un crater. Respeta mobGriefing; no rompe bloques muy duros (obsidiana, bedrock...). */
    private void breakBlocks(ServerLevel level, Vec3 at, int r) {
        if (!com.draxer.dexrider.config.DexConfig.blockBreaking()) return;
        if (!net.neoforged.neoforge.event.EventHooks.canEntityGrief(level, this)) return;
        net.minecraft.core.BlockPos c = net.minecraft.core.BlockPos.containing(at);
        for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
            if (pos.distSqr(c) > r * r) continue;
            net.minecraft.world.level.block.state.BlockState st = level.getBlockState(pos);
            if (st.isAir()) continue;
            float hard = st.getDestroySpeed(level, pos);
            if (hard < 0 || hard > 10.0F) continue;
            level.destroyBlock(pos, getRandom().nextInt(4) == 0, this);
        }
    }

    private void endKick() {
        kickPhase = 0;
        this.entityData.set(DATA_KICK, false);
        kickTarget = null;
        kickCooldown = 400 + getRandom().nextInt(200);
        setDeltaMovement(getDeltaMovement().scale(0.2).add(0, 0.3, 0));
        hurtMarked = true;
    }

    public boolean isKicking() {
        return kickPhase > 0;
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
        return false;   // son Riders: caen de pie
    }

    // ================================================================== GeckoLib: animaciones
    // Cada Rider tiene su pose de espera (Nine en guardia, Orga apoyado en la alabarda, Proto con las manos en un
    // modulo...), guardia con el arma fuera, patada en el aire durante la Rider Kick y un retroceso al recibir golpes.
    private static final com.geckolib.animation.RawAnimation WALK = com.geckolib.animation.RawAnimation.begin().thenLoop("move.walk");
    private static final com.geckolib.animation.RawAnimation KICK = com.geckolib.animation.RawAnimation.begin().thenLoop("attack.kick");
    private static final com.geckolib.animation.RawAnimation HIT = com.geckolib.animation.RawAnimation.begin().thenPlay("misc.hit");
    private static final com.geckolib.animation.RawAnimation IDLE_WEAPON = com.geckolib.animation.RawAnimation.begin().thenLoop("misc.idle.weapon");
    private static final com.geckolib.animation.RawAnimation IDLE_PLAIN = com.geckolib.animation.RawAnimation.begin().thenLoop("misc.idle");
    private static final java.util.Map<KamenRide, com.geckolib.animation.RawAnimation> IDLES = new java.util.EnumMap<>(KamenRide.class);

    private com.geckolib.animation.RawAnimation idleAnim() {
        if (isCivilian()) return IDLE_PLAIN;
        return IDLES.computeIfAbsent(rider, k -> com.geckolib.animation.RawAnimation.begin().thenLoop("misc.idle." + k.id()));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new com.geckolib.animation.AnimationController<>("main", 4, test -> {
            if (this.entityData.get(DATA_KICK)) return test.setAndContinue(KICK);
            if (this.hurtTime > 0) return test.setAndContinue(HIT);
            if (test.isMoving()) return test.setAndContinue(WALK);
            if (hasWeaponOut()) return test.setAndContinue(IDLE_WEAPON);
            return test.setAndContinue(idleAnim());
        }), DefaultAnimations.genericAttackAnimation(DefaultAnimations.ATTACK_PUNCH));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
