package com.draxer.dexrider.entity;

import com.draxer.dexrider.item.AllyBook;
import com.draxer.dexrider.rider.AllyManager;
import com.draxer.dexrider.rider.FX;
import com.draxer.dexrider.rider.KamenRide;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Un Rider AMIGO. Es una entidad aparte que hace su vida:
 *
 *  WANDER   : vive alrededor de su casa (donde lo venciste, o donde le digas), patrulla y la defiende de monstruos,
 *             se cura con el tiempo. Aparece cuando estas cerca de su casa.
 *  SUMMONED : invocado (pantalla de invocar o teclado numerico). Viene a tu lado y pelea contigo como un Rider de verdad
 *             (especial, arma y Rider Kick) contra lo que te ataca o lo que atacas. Pasado el tiempo vuelve a casa y descansa.
 *  DOUBLE   : duplicado solido de Mirage (dura poco, pega menos).
 *
 * Si cae en combate no muere: queda agotado, se retira y descansa un buen rato.
 * Clic derecho (su duenio): llamarlo / despedirlo. Shift + clic derecho: "tu casa ahora es aqui".
 */
public class RiderAllyEntity extends RiderMob {

    public enum Mode { WANDER, SUMMONED, DOUBLE, PROJECTION }

    @Nullable private UUID owner;
    private int gen;
    private Mode mode = Mode.WANDER;
    private int lifeTicks = -1;
    @Nullable private BlockPos home;
    private int suitLineIn = -1;
    private boolean visitor;      // ya volvio a su mundo: vino por un portal y se ira por otro

    public RiderAllyEntity(EntityType<? extends PathfinderMob> type, Level level, KamenRide rider) {
        super(type, level, rider);
        setPersistenceRequired();
    }

    public static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder createAttributes() {
        return createRiderAttributes(80, 7);
    }

    public void setup(UUID owner, int gen, Mode mode, @Nullable BlockPos home) {
        this.owner = owner;
        this.gen = gen;
        this.mode = mode;
        this.home = home;
        if (mode == Mode.DOUBLE) {
            this.lifeTicks = 200;
            this.specialCooldown = 9999;
        }
    }

    @Nullable public UUID owner() { return owner; }
    public int gen() { return gen; }
    public Mode mode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }
    @Nullable public BlockPos home() { return home; }
    public boolean isVisitor() { return visitor; }
    public void setVisitor(boolean v) { this.visitor = v; }

    /** Tras hacerse amigos, cuenta lo del traje (y por que no puede quitarselo, o si puede). */
    public void scheduleSuitLine() { this.suitLineIn = 70; }

    @Override
    protected void idleChat(ServerLevel level) {
        if (mode != Mode.DOUBLE && mode != Mode.PROJECTION) super.idleChat(level);
    }
    public void setHome(BlockPos home) { this.home = home; }

    @Nullable
    public ServerPlayer ownerPlayer() {
        if (owner == null || !(level() instanceof ServerLevel level)) return null;
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(owner);
        return p != null ? p : (level.getPlayerByUUID(owner) instanceof ServerPlayer sp ? sp : null);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25, true));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7) {
            @Override public boolean canUse() { return mode == Mode.WANDER && super.canUse(); }
        });
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean isFriendlyTo(LivingEntity other) {
        if (owner != null && owner.equals(other.getUUID())) return true;
        if (other instanceof RiderAllyEntity a && owner != null && owner.equals(a.owner)) return true;
        if (other instanceof Player p) {
            ServerPlayer o = ownerPlayer();
            return o == null || (o.getLastHurtByMob() != p && o.getLastHurtMob() != p);
        }
        return false;
    }

    @Override
    protected boolean blasterAllowed() {
        ServerPlayer o = ownerPlayer();
        return o != null && com.draxer.dexrider.rider.AllyManager.isFriend(o, KamenRide.ORGA);
    }

    /** Takumi te presta el reloj Accel despues de usarlo peleando a tu lado (si aun no lo tienes). */
    @Override
    protected void onAccelEnd(ServerLevel level) {
        ServerPlayer o = ownerPlayer();
        if (o != null && distanceTo(o) < 32) com.draxer.dexrider.rider.CipherGifts.giveAccel(level, this, o);
    }

    @Override
    protected float powerScale() {
        return mode == Mode.DOUBLE ? 0.4F : 1.0F;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    // ================================================================== vida propia

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (owner == null) { discard(); return; }
        if (suitLineIn > 0 && --suitLineIn == 0) {
            com.draxer.dexrider.rider.RiderDialogue.force(level, this, rider, com.draxer.dexrider.rider.RiderDialogue.Line.SUIT);
        }

        if (mode == Mode.PROJECTION) {
            ServerPlayer o = ownerPlayer();
            if (o == null || com.draxer.dexrider.rider.KamenRide.current(o) != com.draxer.dexrider.rider.KamenRide.COMPLETE
                    || !com.draxer.dexrider.rider.KTouch.isHeld(o, rider) || o.level() != level) {
                vanish(level);
                return;
            }
            if (!com.draxer.dexrider.rider.KTouch.isDragged(o)) followOwner(level);   // arrastre: ya no te siguen
            if (tickCount % 10 == 0) pickTarget(level);
            if (tickCount % 5 == 0) FX.dust(level, rider.color, 0.7F, position().add(0, 1.0, 0), 2, 0.4);
            return;
        }
        if (mode == Mode.DOUBLE) {
            if (--lifeTicks <= 0) vanish(level);
            followOwner(level);
            if (tickCount % 10 == 0) pickTarget(level);
            return;
        }

        if (tickCount % 20 == 0) {
            ServerPlayer o = ownerPlayer();
            if (o != null) {
                AllyBook book = AllyManager.book(o);
                if (book.gen(rider.id()) != gen) {      // existe una version mas nueva de este amigo
                    discard();
                    return;
                }
                if (mode == Mode.SUMMONED && (book.activeUntil(rider.id()) < level.getGameTime()
                        || !o.isAlive() || o.level() != level)) {
                    AllyManager.sendHome(level, this, true);
                    return;
                }
            } else if (mode == Mode.SUMMONED) {
                discard();
                return;
            }
            if (mode == Mode.WANDER && getHealth() < getMaxHealth() && tickCount % 40 == 0) heal(1.0F);
        }
        if (tickCount % 10 == 0) pickTarget(level);
        if (mode == Mode.SUMMONED) followOwner(level);
        else stayNearHome(level);
    }

    private void pickTarget(ServerLevel level) {
        LivingEntity current = getTarget();
        if (current != null && current.isAlive() && !isFriendlyTo(current) && distanceTo(current) < 24) return;
        LivingEntity next = null;
        ServerPlayer o = ownerPlayer();
        if (mode != Mode.WANDER && o != null) {
            LivingEntity a = o.getLastHurtByMob();
            LivingEntity b = o.getLastHurtMob();
            if (valid(a)) next = a;
            else if (valid(b) && o.tickCount - o.getLastHurtMobTimestamp() < 200) next = b;
        }
        if (next == null) {
            double r = mode == Mode.WANDER ? 12 : 16;
            double best = Double.MAX_VALUE;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r),
                    e -> e instanceof Enemy && e.isAlive() && !isFriendlyTo(e))) {
                double dd = distanceToSqr(e);
                if (dd < best && hasLineOfSight(e)) { best = dd; next = e; }
            }
        }
        setTarget(next);
    }

    private boolean valid(@Nullable LivingEntity e) {
        return e != null && e.isAlive() && e != this && !isFriendlyTo(e) && distanceTo(e) < 28;
    }

    private void followOwner(ServerLevel level) {
        ServerPlayer o = ownerPlayer();
        if (o == null || o.level() != level) return;
        double d = distanceTo(o);
        if (d > 20) {
            Vec3 p = o.position().add(o.getLookAngle().scale(-1.5));
            teleportTo(p.x, o.getY(), p.z);
            FX.particle(level, ParticleTypes.END_ROD, FX.center(this), 10, 0.4, 0.05);
        } else if (getTarget() == null && d > 5 && tickCount % 10 == 0) {
            getNavigation().moveTo(o, 1.2);
        }
    }

    private void stayNearHome(ServerLevel level) {
        if (home == null || tickCount % 40 != 0) return;
        double d = Math.sqrt(home.distToCenterSqr(position()));
        if (d > 64 && level.isLoaded(home)) {
            teleportTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
        } else if (d > 20 && getTarget() == null) {
            getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.9);
        }
    }

    /** Llamado desde DexEvents en lugar de morir. */
    public void knockOut() {
        setHealth(getMaxHealth());
        if (!(level() instanceof ServerLevel level)) return;
        if (mode == Mode.DOUBLE || mode == Mode.PROJECTION) {
            vanish(level);
            return;
        }
        ServerPlayer o = ownerPlayer();
        com.draxer.dexrider.rider.RiderDialogue.force(level, this, rider, com.draxer.dexrider.rider.RiderDialogue.Line.DOWN);
        if (o != null) {
            o.sendSystemMessage(Component.translatable("message.dexrider.ally_down", rider.displayName()), true);
            AllyManager.rest(o, rider, 6000);
        }
        vanish(level);
    }

    public void vanish(ServerLevel level) {
        FX.particle(level, ParticleTypes.END_ROD, FX.center(this), 25, 0.4, 0.08);
        FX.dust(level, rider.color, 1.2F, FX.center(this), 15, 0.4);
        discard();
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer sp) || owner == null || !owner.equals(player.getUUID()) || mode == Mode.DOUBLE || mode == Mode.PROJECTION) {
            return super.mobInteract(player, hand);
        }
        if (sp.isShiftKeyDown()) {
            AllyManager.setHome(sp, this, blockPosition());
        } else {
            AllyManager.toggle(sp, rider);
        }
        return InteractionResult.SUCCESS;
    }

    // ================================================================== guardado

    @Override
    public void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        if (owner != null) out.putString("dex_owner", owner.toString());
        out.putInt("dex_gen", gen);
        out.putBoolean("dex_visitor", visitor);
        out.putString("dex_mode", mode.name());
        if (home != null) out.putLong("dex_home", home.asLong());
    }

    @Override
    public void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        String o = in.getStringOr("dex_owner", "");
        owner = o.isEmpty() ? null : UUID.fromString(o);
        gen = in.getIntOr("dex_gen", 0);
        visitor = in.getBooleanOr("dex_visitor", false);
        try { mode = Mode.valueOf(in.getStringOr("dex_mode", "WANDER")); } catch (IllegalArgumentException e) { mode = Mode.WANDER; }
        long h = in.getLongOr("dex_home", Long.MIN_VALUE);
        home = h == Long.MIN_VALUE ? null : BlockPos.of(h);
    }
}
