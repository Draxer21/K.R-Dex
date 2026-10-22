package com.draxer.dexrider.entity;

import com.draxer.dexrider.item.DexCard;
import com.draxer.dexrider.registry.DexEntities;
import com.draxer.dexrider.registry.DexItems;
import com.draxer.dexrider.rider.DexAdvancements;
import com.draxer.dexrider.rider.FX;
import com.draxer.dexrider.rider.FormRide;
import com.draxer.dexrider.rider.Guard;
import com.draxer.dexrider.rider.KTouch;
import com.draxer.dexrider.rider.KamenRide;
import com.draxer.dexrider.rider.Transformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * El Arquitecto (diseno propio). "No toda Dai-Shocker es la casa de la montania": la gente de fuera de la
 * familia que penso que harian con diez Riders que se encienden con un boton. Lo despierta el Nucleo de la
 * Senal en el fondo de su base.
 *
 *  Fase 1 (100-66 %)  llama refuerzos, dispara un rayo a distancia y empuja con un pulso si lo rodean.
 *  Fase 2 (66-33 %)   SE ADAPTA: cada golpe seguido con la misma identidad, forma y arma le hace un 15 % menos
 *                     (hasta -75 %). Cambiar de Kamen Ride, Form Ride o arma lo sorprende: +25 %.
 *  Fase 3 (<33 %)     LA SENAL: carga 2,5 s y suelta la correa sobre todos. Si dices que no (Shift a tiempo,
 *                     como una parada) la Senal se corta y queda aturdido 3 s (recibe +50 %). Si no, nauseas,
 *                     debilidad, tus Riders amigos pierden el control un rato y, en Complete Form, la correa
 *                     empieza a arrastrarte.
 *  Al caer deja la carta Attack Ride: Severance (cortar la correa) y apaga el Nucleo para siempre.
 */
public class ArchitectEntity extends KaijinEntity {
    private static final int SIGNAL_CHARGE = 50;

    private int phase = 1;
    private int callCooldown = 80, beamCooldown = 60, signalCooldown = 120;
    private int signalCharge = -1;
    private long signalRelease = -1;
    private int stunned;
    private boolean introduced;
    @Nullable private BlockPos core;
    private final Map<UUID, String> lastKey = new HashMap<>();
    private final Map<UUID, Integer> streak = new HashMap<>();

    public ArchitectEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, Kind.ARCHITECT);
    }

    public void setCore(BlockPos pos) { this.core = pos; }
    public int phase() { return phase; }
    public boolean isStunned() { return stunned > 0; }
    public boolean isSignalCharging() { return signalCharge >= 0; }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || !isAlive()) return;
        float hp = getHealth() / getMaxHealth();
        int want = hp <= 0.33F ? 3 : hp <= 0.66F ? 2 : 1;
        if (want > phase) {
            phase = want;
            say(level, "architect_phase" + phase, ChatFormatting.RED);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.2F, 1.4F);
            FX.ring(level, this, 0xD4202C, 2.0, 8);
            if (phase == 3) signalCooldown = 40;
        }
        if (!introduced && getTarget() instanceof Player) {
            introduced = true;
            say(level, "architect_intro", ChatFormatting.GRAY);
        }
        if (callCooldown > 0) callCooldown--;
        if (beamCooldown > 0) beamCooldown--;
        if (signalCooldown > 0) signalCooldown--;

        if (stunned > 0) {
            stunned--;
            getNavigation().stop();
            setDeltaMovement(0, getDeltaMovement().y, 0);
            if (tickCount % 4 == 0) FX.particle(level, ParticleTypes.CRIT, getEyePosition().add(0, 0.6, 0), 3, 0.4, 0.05);
        }
        if (signalCharge >= 0) signalTick(level);
        if (signalRelease >= 0 && level.getGameTime() >= signalRelease + 3) judgeSignal(level);
    }

    @Override
    protected void bossSpecial(ServerLevel level, LivingEntity target, double d) {
        if (stunned > 0 || signalCharge >= 0 || signalRelease >= 0) { special = 10; return; }
        int fast = phase >= 2 ? 15 : 0;
        if (phase >= 3 && signalCooldown <= 0) { chargeSignal(level); special = 70; return; }
        if (callCooldown <= 0) { callReinforcements(level, target); special = 40; return; }
        if (crowded(level) >= 2) { pulse(level); special = 50 - fast; return; }
        if (d > 6 && beamCooldown <= 0 && hasLineOfSight(target)) { beam(level, target); special = 45 - fast; return; }
        if (phase >= 2 && d > 5 && d < 16 && random.nextBoolean()) { leap(target, 1.0); special = 70 - fast; return; }
        if (d > 5 && d < 16) { startCharge(level, target); special = 90 - fast; return; }
        special = 20;
    }

    private int crowded(ServerLevel level) {
        return level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3.5), this::isFoe).size();
    }

    private boolean isFoe(LivingEntity e) {
        return e != this && e.isAlive() && !(e instanceof KaijinEntity) && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    private void callReinforcements(ServerLevel level, LivingEntity target) {
        callCooldown = phase >= 2 ? 420 : 520;
        Kind[] wave = phase == 1 ? new Kind[]{Kind.SOLDIER, Kind.SOLDIER, Kind.MANTIS}
                : new Kind[]{Kind.SOLDIER, Kind.MANTIS, Kind.RHINO};
        for (int i = 0; i < wave.length; i++) {
            KaijinEntity s = DexEntities.KAIJIN.get(wave[i]).get().create(level, EntitySpawnReason.REINFORCEMENT);
            if (s == null) continue;
            double a = i * Math.PI * 2 / wave.length + random.nextDouble();
            s.setPos(getX() + Math.cos(a) * 4, getY(), getZ() + Math.sin(a) * 4);
            s.setTarget(target);
            level.addFreshEntity(s);
            FX.particle(level, ParticleTypes.LARGE_SMOKE, s.position().add(0, 1, 0), 15, 0.4, 0.02);
        }
        say(level, "architect_call", ChatFormatting.GRAY);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.5F, 0.6F);
    }

    private void pulse(ServerLevel level) {
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(5, 1.5, 5), this::isFoe)) {
            e.hurtServer(level, damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.6F);
            FX.push(e, position(), 1.6, 0.5);
        }
        FX.ring(level, this, 0xD4202C, 4.5, 3);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.8F, 1.4F);
    }

    private void beam(ServerLevel level, LivingEntity target) {
        beamCooldown = phase >= 2 ? 50 : 80;
        Vec3 from = getEyePosition();
        Vec3 to = FX.center(target);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.2F, 0.5F);
        FX.line(level, from, to, 0xFF2030, 1.2F, 0.35);
        target.hurtServer(level, damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.7F);
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 1));
    }

    // ------------------------------------------------------------------ la Senal

    public void chargeSignal(ServerLevel level) {
        signalCharge = SIGNAL_CHARGE;
        signalCooldown = 260;
        getNavigation().stop();
        say(level, "signal_charge", ChatFormatting.DARK_RED);
    }

    private void signalTick(ServerLevel level) {
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        double r = 1.0 + (SIGNAL_CHARGE - signalCharge) * 0.18;
        if (tickCount % 2 == 0) {
            for (int a = 0; a < 360; a += 20) {
                double rad = Math.toRadians(a + tickCount * 6);
                FX.dust(level, 0x8A2BE2, 1.3F, position().add(Math.cos(rad) * r, 0.3 + (SIGNAL_CHARGE - signalCharge) * 0.04, Math.sin(rad) * r), 1, 0.02);
            }
        }
        if (signalCharge % 10 == 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2.0F, 0.6F + (SIGNAL_CHARGE - signalCharge) * 0.01F);
        if (--signalCharge < 0) {
            signalRelease = level.getGameTime();
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.5F, 0.7F);
            FX.ring(level, this, 0x8A2BE2, 6.0, 4);
        }
    }

    /** Tres ticks despues de soltarla: quien dijo que no a tiempo la corta; a los demas los alcanza. */
    private void judgeSignal(ServerLevel level) {
        long r = signalRelease;
        signalRelease = -1;
        boolean cut = false;
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(32), this::isFoe)) {
            if (Guard.saidNo(p, r - 12, r + 3)) {
                cut = true;
                p.sendSystemMessage(Component.translatable("message.dexrider.signal_no").withStyle(ChatFormatting.AQUA));
                DexAdvancements.award(p, "said_no");
                continue;
            }
            pull(level, p);
        }
        if (cut) stun(level, 60);
    }

    private void pull(ServerLevel level, ServerPlayer p) {
        p.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 120, 0));
        p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1));
        p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
        p.sendSystemMessage(Component.translatable("message.dexrider.signal_hit").withStyle(ChatFormatting.DARK_PURPLE));
        for (RiderAllyEntity a : level.getEntitiesOfClass(RiderAllyEntity.class, p.getBoundingBox().inflate(24),
                a -> p.getUUID().equals(a.owner()))) {
            a.setTarget(null);
            a.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 120, 4));
            a.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 4));
            FX.line(level, FX.center(this), FX.center(a), 0x8A2BE2, 0.8F, 0.8);
        }
        if (Transformation.isTransformed(p) && KamenRide.current(p) == KamenRide.COMPLETE && KTouch.correa(p) < 0) {
            KTouch.forceCorrea(p, KTouch.ECHO_END);
        }
    }

    public void stun(ServerLevel level, int ticks) {
        stunned = Math.max(stunned, ticks);
        signalCharge = -1;
        signalRelease = -1;
        special = ticks;
        addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 9, false, false));
        addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 9, false, false));
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.0F, 0.5F);
        FX.particle(level, ParticleTypes.CRIT, getEyePosition(), 20, 0.5, 0.2);
    }

    /** Una parada perfecta contra el: durante la carga de la Senal tambien es un "no". */
    public void parried() {
        if (!(level() instanceof ServerLevel level)) return;
        if (signalCharge >= 0) {
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(6)))
                p.sendSystemMessage(Component.translatable("message.dexrider.signal_no").withStyle(ChatFormatting.AQUA));
            stun(level, 60);
        } else {
            stunned = Math.max(stunned, 15);
        }
    }

    /** Lo que corta la carta Severance: si la Senal esta cargando, se corta. */
    public void sever(ServerLevel level) {
        if (signalCharge >= 0 || signalRelease >= 0) stun(level, 60);
        else stunned = Math.max(stunned, 30);
    }

    // ------------------------------------------------------------------ adaptacion

    /** Lo que el Arquitecto "lee" del que le pega: identidad, forma y arma. */
    public static String adaptKey(LivingEntity attacker) {
        if (attacker instanceof Player p && Transformation.isTransformed(p)) {
            FormRide f = FormRide.current(p);
            return KamenRide.current(p).id() + "/" + (f == null ? "-" : f.name()) + "/"
                    + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(p.getMainHandItem().getItem());
        }
        if (attacker instanceof RiderMob rm) return "rider/" + rm.rider().id();
        return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(attacker.getType()).toString();
    }

    /** Multiplicador de dano para este golpe (actualiza la racha). Publico para las pruebas. */
    public float adaptScale(LivingEntity attacker) {
        if (phase < 2) return 1.0F;
        UUID id = attacker.getUUID();
        String key = adaptKey(attacker);
        String last = lastKey.get(id);
        if (key.equals(last)) {
            int s = Math.min(streak.merge(id, 1, Integer::sum), 5);
            if (s == 3 && attacker instanceof ServerPlayer p)
                p.sendSystemMessage(Component.translatable("message.dexrider.architect_adapts"), true);
            return 1.0F - 0.15F * s;
        }
        int prev = streak.getOrDefault(id, 0);
        lastKey.put(id, key);
        streak.put(id, 0);
        if (last != null && prev >= 2) {
            if (level() instanceof ServerLevel level) FX.particle(level, ParticleTypes.ENCHANTED_HIT, FX.center(this), 12, 0.4, 0.2);
            if (attacker instanceof ServerPlayer p) p.sendSystemMessage(Component.translatable("message.dexrider.architect_surprised"), true);
            return 1.25F;
        }
        return 1.0F;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        float scale = stunned > 0 ? 1.5F : 1.0F;
        if (source.getEntity() instanceof LivingEntity attacker && attacker != this) scale *= adaptScale(attacker);
        return super.hurtServer(level, source, amount * scale);
    }

    // ------------------------------------------------------------------ muerte

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!(level() instanceof ServerLevel level)) return;
        say(level, "architect_down", ChatFormatting.GRAY);
        level.addFreshEntity(new ItemEntity(level, getX(), getY() + 0.5, getZ(),
                new ItemStack(DexItems.card(DexCard.ATTACK_RIDE_SEVERANCE).get())));
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(48)))
            DexAdvancements.award(p, "architect");
        if (core != null && level.getBlockState(core).getBlock() instanceof com.draxer.dexrider.block.SignalCoreBlock) {
            level.setBlockAndUpdate(core, level.getBlockState(core).setValue(com.draxer.dexrider.block.SignalCoreBlock.SPENT, true));
        }
    }

    private void say(ServerLevel level, String key, ChatFormatting color) {
        Component line = Component.translatable("message.dexrider." + key).withStyle(color);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(40))) p.sendSystemMessage(line);
    }

    // ------------------------------------------------------------------ guardado

    @Override
    public void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        if (core != null) out.putLong("dex_core", core.asLong());
        out.putBoolean("dex_intro", introduced);
    }

    @Override
    public void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        long c = in.getLongOr("dex_core", Long.MIN_VALUE);
        core = c == Long.MIN_VALUE ? null : BlockPos.of(c);
        introduced = in.getBooleanOr("dex_intro", false);
    }
}
