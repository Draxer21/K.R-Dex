package com.draxer.dexrider.entity;

import com.draxer.dexrider.item.DexCard;
import com.draxer.dexrider.registry.DexEntities;
import com.draxer.dexrider.registry.DexItems;
import com.draxer.dexrider.rider.FX;
import com.draxer.dexrider.rider.TempBlocks;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Jefes de Dai-Shocker con tres fases (0.52). Cambian de fase al bajar del 66 % y del 33 % de vida: rugen, quedan
 * invulnerables un momento y empujan a quien este cerca. Al caer dejan su carta rara.
 *
 *  Mantis Reina: saltos y barridos  ->  llama a sus mantis y salta mas  ->  parpadea detras de ti y corta.
 *  Rinoceronte Titan: embestidas  ->  pisoton con onda que se expande (saltala)  ->  tres embestidas seguidas y rocas.
 *  Comandante: llama combatientes  ->  escudo mientras le quede tropa cerca  ->  furia: corre, salta y debilita.
 */
public class KaijinBossEntity extends KaijinEntity {
    private int phase = 1;
    private int invuln;
    private int quakeTicks = -1;
    private Vec3 quakeAt = Vec3.ZERO;
    private int chainCharges;
    private final List<Vec3> rocks = new ArrayList<>();
    private int rockTicks;
    /** Bloques del muro de la arena (para quitarlos al terminar). */
    public final List<BlockPos> arena = new ArrayList<>();

    public KaijinBossEntity(EntityType<? extends Monster> type, Level level, Kind kind) {
        super(type, level, kind);
    }

    public int phase() { return phase; }

    public static DexCard rareCard(Kind kind) {
        return switch (kind) {
            case MANTIS_QUEEN -> DexCard.ATTACK_RIDE_QUEEN_BLINK;
            case RHINO_TITAN -> DexCard.ATTACK_RIDE_TITAN_QUAKE;
            default -> DexCard.ATTACK_RIDE_COMMAND;
        };
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || !isAlive()) return;
        if (invuln > 0) {
            invuln--;
            if (invuln % 4 == 0) FX.particle(level, ParticleTypes.ENCHANT, FX.center(this), 10, 1.0, 0.3);
        }
        float frac = getHealth() / getMaxHealth();
        int want = frac > 0.66F ? 1 : frac > 0.33F ? 2 : 3;
        if (want > phase) enterPhase(level, want);
        if (quakeTicks >= 0) quakeTick(level);
        if (rockTicks > 0 && --rockTicks == 0) dropRocks(level);
        if (kind() == Kind.COMMANDER && phase == 2 && shielded(level) && tickCount % 6 == 0)
            FX.particle(level, ParticleTypes.END_ROD, FX.center(this), 4, 0.9, 0.02);
        if (kind() == Kind.RHINO_TITAN && chainCharges > 0 && !charging() && getTarget() != null && tickCount % 10 == 0) {
            chainCharges--;
            startCharge(level, getTarget());
        }
    }

    private boolean charging() { return chargeTicks() > 0; }

    private void enterPhase(ServerLevel level, int to) {
        phase = to;
        invuln = 40;
        setBossName(Component.translatable("entity.dexrider." + kind().id).append(" — ")
                .append(Component.translatable("boss.dexrider.phase", to)));
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0F, 0.6F);
        FX.particle(level, ParticleTypes.EXPLOSION, FX.center(this), 4, 1.2, 0);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(5), e -> e != this && !(e instanceof KaijinEntity)))
            FX.push(e, position(), 1.4, 0.5);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(40)))
            p.sendSystemMessage(Component.translatable("boss.dexrider." + kind().id + ".phase" + to).withStyle(ChatFormatting.RED), false);
        if (kind() == Kind.COMMANDER && to == 3) {
            addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 600, 1));
            addEffect(new MobEffectInstance(MobEffects.STRENGTH, 20 * 600, 0));
        }
        special = 30;
    }

    // ------------------------------------------------------------------ ataques

    @Override
    protected void bossSpecial(ServerLevel level, LivingEntity target, double d) {
        switch (kind()) {
            case MANTIS_QUEEN -> queen(level, target, d);
            case RHINO_TITAN -> titan(level, target, d);
            case COMMANDER -> commander(level, target, d);
            default -> special = 40;
        }
    }

    private void queen(ServerLevel level, LivingEntity target, double d) {
        if (phase >= 2 && random.nextInt(3) == 0 && count(level, Kind.MANTIS) < 3) {
            call(level, target, Kind.MANTIS, 2);
            special = 60;
            return;
        }
        if (phase == 3 && random.nextBoolean()) {
            // parpadeo: aparece detras del objetivo y corta
            Vec3 back = target.position().subtract(target.getLookAngle().multiply(1, 0, 1).normalize().scale(1.8));
            FX.particle(level, ParticleTypes.PORTAL, FX.center(this), 30, 0.6, 0.4);
            teleportTo(back.x, target.getY(), back.z);
            FX.particle(level, ParticleTypes.PORTAL, FX.center(this), 30, 0.6, 0.4);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 1.4F);
            sweep(level, 3.0F, 1.2F);
            special = 45;
            return;
        }
        if (d > 3.5 && d < 16) { leap(target, phase >= 2 ? 1.1 : 0.9); special = phase >= 2 ? 45 : 70; }
        else if (d <= 3.5) { sweep(level, 3.5F, 1.0F); special = 50; }
        else special = 20;
    }

    private void titan(ServerLevel level, LivingEntity target, double d) {
        if (phase == 3 && random.nextInt(3) == 0) {
            chainCharges = 2;
            startCharge(level, target);
            rockFall(level, target);
            special = 150;
            return;
        }
        if (phase >= 2 && (d < 7 || random.nextBoolean())) {
            // pisoton: salta en el sitio y al caer sale una onda que crece (hay que saltarla)
            quakeAt = position();
            quakeTicks = 0;
            setDeltaMovement(0, 0.6, 0);
            hurtMarked = true;
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.6F, 0.6F);
            special = 110;
            return;
        }
        if (d > 5 && d < 20) { startCharge(level, target); special = 100; }
        else if (d <= 5) { sweep(level, 3.0F, 1.1F); special = 40; }
        else special = 20;
    }

    private void commander(ServerLevel level, LivingEntity target, double d) {
        int troops = count(level, Kind.SOLDIER);
        if (troops < (phase == 1 ? 3 : 5) && (phase < 3 || random.nextInt(3) == 0)) {
            call(level, target, Kind.SOLDIER, phase == 1 ? 3 : 2);
            special = phase == 1 ? 160 : 120;
            return;
        }
        if (phase == 3) {
            // grito de guerra: debilita y ralentiza a los que estan cerca, y salta encima del objetivo
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(8), e -> e != this && !(e instanceof KaijinEntity))) {
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0));
                e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0));
            }
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 1.6F, 0.6F);
            if (d > 3) leap(target, 1.0);
            special = 70;
            return;
        }
        if (d <= 3.5) { sweep(level, 3.0F, 1.0F); special = 40; }
        else special = 30;
    }

    private void sweep(ServerLevel level, float r, float mult) {
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * mult;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r, 1, r), e -> e != this && e.isAlive() && !(e instanceof KaijinEntity))) {
            e.hurtServer(level, damageSources().mobAttack(this), dmg);
            if (kind() == Kind.MANTIS_QUEEN) e.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, phase >= 3 ? 1 : 0));
            FX.push(e, position(), 0.8, 0.3);
        }
        FX.particle(level, ParticleTypes.SWEEP_ATTACK, FX.center(this), 6, r * 0.5, 0);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.4F, 0.7F);
    }

    private int count(ServerLevel level, Kind k) {
        return level.getEntitiesOfClass(KaijinEntity.class, getBoundingBox().inflate(20), e -> e.isAlive() && e.kind() == k).size();
    }

    private void call(ServerLevel level, LivingEntity target, Kind k, int n) {
        for (int i = 0; i < n; i++) {
            KaijinEntity s = DexEntities.KAIJIN.get(k).get().create(level, EntitySpawnReason.REINFORCEMENT);
            if (s == null) continue;
            double a = random.nextDouble() * Math.PI * 2;
            s.setPos(getX() + Math.cos(a) * 3, getY(), getZ() + Math.sin(a) * 3);
            s.setTarget(target);
            level.addFreshEntity(s);
            FX.particle(level, ParticleTypes.LARGE_SMOKE, s.position().add(0, 1, 0), 15, 0.4, 0.02);
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.5F, 0.7F);
    }

    private boolean shielded(ServerLevel level) {
        return count(level, Kind.SOLDIER) > 0;
    }

    private void quakeTick(ServerLevel level) {
        if (quakeTicks == 0 && !onGround()) return;      // espera a caer
        double r = 1.5 + quakeTicks * 0.8;
        for (int a = 0; a < 360; a += 12) {
            double rad = Math.toRadians(a);
            Vec3 p = quakeAt.add(Math.cos(rad) * r, 0.2, Math.sin(rad) * r);
            FX.dust(level, 0xE0402C, 1.6F, p, 1, 0.05);
        }
        if (quakeTicks % 2 == 0) level.playSound(null, quakeAt.x, quakeAt.y, quakeAt.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.5F, 0.5F);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(quakeAt, quakeAt).inflate(r + 0.6, 1.2, r + 0.6),
                e -> e != this && e.isAlive() && !(e instanceof KaijinEntity) && e.onGround())) {
            double dist = e.position().subtract(quakeAt).horizontalDistance();
            if (Math.abs(dist - r) > 0.9) continue;
            e.hurtServer(level, damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.9F);
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
            FX.push(e, quakeAt, 1.0, 0.5);
        }
        if (++quakeTicks > 12) quakeTicks = -1;
    }

    private void rockFall(ServerLevel level, LivingEntity target) {
        rocks.clear();
        for (int i = 0; i < 5; i++)
            rocks.add(target.position().add((random.nextDouble() - 0.5) * 8, 0, (random.nextDouble() - 0.5) * 8));
        rocks.set(0, target.position());
        rockTicks = 30;
        for (Vec3 r : rocks) FX.dust(level, 0x8C6E50, 1.5F, r.add(0, 0.1, 0), 12, 0.6);   // marca donde caera la roca
    }

    private void dropRocks(ServerLevel level) {
        for (Vec3 r : rocks) {
            FX.particle(level, ParticleTypes.EXPLOSION, r.add(0, 0.5, 0), 1, 0.2, 0);
            level.playSound(null, r.x, r.y, r.z, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 1.5F, 0.6F);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(r, r).inflate(1.6, 2, 1.6), e -> e != this && !(e instanceof KaijinEntity)))
                e.hurtServer(level, damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.8F);
        }
        rocks.clear();
    }

    // ------------------------------------------------------------------ dano, muerte y arena

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (invuln > 0) return false;
        if (kind() == Kind.COMMANDER && phase == 2 && shielded(level)) amount *= 0.2F;   // la tropa lo cubre
        return super.hurtServer(level, source, amount);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!(level() instanceof ServerLevel level)) return;
        level.addFreshEntity(new ItemEntity(level, getX(), getY() + 0.5, getZ(), new ItemStack(DexItems.card(rareCard(kind())).get())));
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(48))) {
            p.sendSystemMessage(Component.translatable("boss.dexrider.down", Component.translatable("entity.dexrider." + kind().id)).withStyle(ChatFormatting.GOLD), false);
            com.draxer.dexrider.rider.DexAdvancements.award(p, "daishocker_boss");
        }
        clearArena(level);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel level) clearArena(level);
        super.remove(reason);
    }

    private void clearArena(ServerLevel level) {
        for (BlockPos p : arena) TempBlocks.breakTemp(level, p);
        arena.clear();
    }
}
