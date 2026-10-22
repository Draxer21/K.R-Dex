package com.draxer.dexrider.entity;

import com.draxer.dexrider.item.DexDriverItem;
import com.draxer.dexrider.item.DriverDeck;
import com.draxer.dexrider.registry.DexItems;
import com.draxer.dexrider.registry.DexSounds;
import com.draxer.dexrider.rider.AllyManager;
import com.draxer.dexrider.rider.FX;
import com.draxer.dexrider.rider.KamenRide;
import com.draxer.dexrider.rider.Transformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import com.draxer.dexrider.registry.DexEntities;
import com.draxer.dexrider.rider.RiderDialogue;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * Rider perdido: llego a este mundo por accidente y sigue la senal del Driver de Dex. Pelea como su original
 * (especial, arma y Rider Kick; ver RiderMob).
 *
 * Regla del fanfic: una Kamen Ride no se saca de una demostracion, hace falta un COMBATE REAL.
 * La carta solo la recibe un Dex transformado (con el Driver) que lo derrote, y entonces
 * el Rider se vuelve tu AMIGO: aparece en ese lugar, que pasa a ser su "casa" (ver RiderAllyEntity).
 */
public class RiderEchoEntity extends RiderMob implements Enemy {

    public RiderEchoEntity(EntityType<? extends PathfinderMob> type, Level level, KamenRide rider) {
        super(type, level, rider);
        this.xpReward = 40;
    }

    public static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder createAttributes() {
        return createRiderAttributes(90, 8);
    }

    /** Aparece como un monstruo: con poca luz y nunca en pacifico. */
    public static boolean checkSpawnRules(EntityType<? extends PathfinderMob> type, ServerLevelAccessor level,
                                          EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        Player near = level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 128, false);
        if (near instanceof ServerPlayer sp) {
            for (var e : DexEntities.ECHOES.entrySet()) {
                if (e.getValue().get() == type && AllyManager.isFriend(sp, e.getKey())) return false;
            }
        }
        if (reason == EntitySpawnReason.NATURAL && random.nextDouble() >= com.draxer.dexrider.config.DexConfig.echoSpawnChance()) return false;
        return level.getDifficulty() != Difficulty.PEACEFUL
                && (reason == EntitySpawnReason.SPAWNER || Monster.isDarkEnoughToSpawn(level, pos, random))
                && Mob.checkMobSpawnRules(type, level, reason, pos, random);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, false));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean isFriendlyTo(LivingEntity other) {
        return other instanceof RiderEchoEntity;
    }

    // ------------------------------------------------------------------ voz, pareja y apoyo

    @Nullable private UUID partner;
    private boolean pairChecked;
    /** Invocado a mano (/summon, huevo, dispensador...): llega solo, sin pareja. */
    private boolean soloSpawn;

    @Override
    @Nullable
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor level,
            net.minecraft.world.DifficultyInstance difficulty, EntitySpawnReason reason,
            @Nullable net.minecraft.world.entity.SpawnGroupData data) {
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) soloSpawn = true;
        return super.finalizeSpawn(level, difficulty, reason, data);
    }
    private int lostTalk = 200;

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (!pairChecked) {
            pairChecked = true;
            if (canDetransform(rider)) setCivilian(true);
            if (!soloSpawn) maybeSpawnPartner(level);
        }
        if (getTarget() == null) {
            // perdido: habla solo de vez en cuando si alguien anda cerca
            if (--lostTalk <= 0) {
                lostTalk = 600 + getRandom().nextInt(900);
                if (level.getNearestPlayer(this, 24) != null) RiderDialogue.say(level, this, rider, RiderDialogue.Line.LOST, 200);
            }
            // sigue a su companero
            if (partner != null && tickCount % 20 == 0 && level.getEntity(partner) instanceof RiderEchoEntity p && p.isAlive()
                    && distanceTo(p) > 7) {
                getNavigation().moveTo(p, 1.0);
            }
        }
    }

    /** A veces dos Riders perdidos ya se encontraron entre ellos y viajan juntos. */
    private void maybeSpawnPartner(ServerLevel level) {
        if (partner != null || getRandom().nextFloat() > 0.3F) return;
        java.util.List<KamenRide> options = new java.util.ArrayList<>();
        for (KamenRide k : DexEntities.NPC_RIDERS) if (k != rider) options.add(k);
        KamenRide other = options.get(getRandom().nextInt(options.size()));
        Player near = level.getNearestPlayer(this, 128);
        if (near instanceof ServerPlayer sp && AllyManager.isFriend(sp, other)) return;
        RiderEchoEntity mate = DexEntities.ECHOES.get(other).get().create(level, EntitySpawnReason.REINFORCEMENT);
        if (mate == null) return;
        mate.pairChecked = true;
        if (canDetransform(other)) mate.setCivilian(true);
        mate.setPos(getX() + getRandom().nextGaussian(), getY(), getZ() + getRandom().nextGaussian());
        mate.partner = getUUID();
        this.partner = mate.getUUID();
        level.addFreshEntity(mate);
    }

    @Override
    protected void onNewTarget(ServerLevel level, LivingEntity target) {
        if (target instanceof ServerPlayer sp && Transformation.isTransformed(sp)) {
            RiderDialogue.say(level, this, rider, RiderDialogue.Line.SPOT_DEX, 100);
        } else if (target instanceof Player) {
            RiderDialogue.say(level, this, rider, RiderDialogue.Line.SPOT_PLAYER, 100);
        }
    }

    /** Alguien ataco a este Rider: los perdidos cercanos acuden a ayudarlo (y entran en panico). */
    public void callForHelp(ServerLevel level, LivingEntity attacker) {
        if (isFriendlyTo(attacker)) return;
        if (getTarget() == null) setTarget(attacker);
        for (RiderEchoEntity other : level.getEntitiesOfClass(RiderEchoEntity.class, getBoundingBox().inflate(24),
                e -> e != this && e.isAlive())) {
            boolean wasCalm = other.getTarget() != attacker;
            other.setTarget(attacker);
            if (wasCalm) RiderDialogue.say(level, other, other.rider(), RiderDialogue.Line.PANIC, 120, RiderDialogue.address(other.rider(), rider));
        }
    }

    // ------------------------------------------------------------------ la derrota: amistad o retirada

    /**
     * Llamado desde DexEvents en lugar de morir (nadie muere: son personas).
     * Si lo vence Dex transformado, se convence de que es el de verdad: carta Kamen Ride + amigo.
     * Si no, se retira.
     */
    public void onDefeated(DamageSource source) {
        if (!(level() instanceof ServerLevel level)) return;
        setHealth(getMaxHealth());
        ServerPlayer player = source.getEntity() instanceof ServerPlayer sp ? sp : null;
        boolean byDex = player != null && Transformation.isTransformed(player) && !DexDriverItem.findDriver(player).isEmpty();
        if (!byDex || rider.card == null || AllyManager.isFriend(player, rider)) {
            RiderDialogue.force(level, this, rider, RiderDialogue.Line.RETREAT);
            if (player != null && !byDex) {
                player.sendSystemMessage(Component.translatable("message.dexrider.echo_need_dex", rider.displayName()), true);
            }
            FX.particle(level, ParticleTypes.END_ROD, FX.center(this), 30, 0.5, 0.1);
            discard();
            return;
        }
        RiderDialogue.force(level, this, rider, RiderDialogue.Line.CONVINCED);
        // Takumi vencido despues de usar el Accel: te presta el reloj
        if (rider == KamenRide.CIPHER && usedAccel) com.draxer.dexrider.rider.CipherGifts.giveAccel(level, this, player);
        Vec3 c = FX.center(this);
        FX.card(level, c, player.getEyePosition().subtract(c).normalize(), rider.color);
        FX.cardPath(level, c, player.getEyePosition(), 3, rider.color);
        DexSounds.play(level, player, DexSounds.SCAN_COMPLETE, 1.3F, 0.9F);
        ItemStack card = new ItemStack(DexItems.card(rider.card).get());
        if (!player.getInventory().add(card)) player.drop(card, false);
        DexDriverItem.registerCard(player, rider.card);
        player.sendSystemMessage(Component.translatable("message.dexrider.echo_card", rider.displayName())
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        // Se queda como amigo, con su casa aqui (su companero, si lo tiene, deja de pelear)
        BlockPos home = blockPosition();
        if (partner != null && level.getEntity(partner) instanceof RiderEchoEntity mate) mate.setTarget(null);
        discard();
        AllyManager.befriend(level, player, rider, home);
    }

    @Override
    public void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putBoolean("dex_pair_checked", pairChecked);
        if (partner != null) out.putString("dex_partner", partner.toString());
    }

    @Override
    public void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        pairChecked = in.getBooleanOr("dex_pair_checked", false);
        String p = in.getStringOr("dex_partner", "");
        partner = p.isEmpty() ? null : UUID.fromString(p);
    }
}
