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

    // trabajo (ver AllyWork): encargo junto a su casa, ayuda a tu lado y lo que lleva encima
    @Nullable private com.draxer.dexrider.rider.AllyWork.Job job;
    @Nullable private com.draxer.dexrider.rider.AllyWork.Job helpJob;
    @Nullable private BlockPos helpPos;
    private String helpBlock = "";
    private long helpUntil;
    private com.draxer.dexrider.item.AllySkills.Entry skills = com.draxer.dexrider.item.AllySkills.Entry.EMPTY;
    private final java.util.List<net.minecraft.world.item.ItemStack> bag = new java.util.ArrayList<>();
    public static final int BAG_SLOTS = 9;
    // rutina en casa (AllyRoutine)
    @Nullable RiderAllyEntity momentPartner;
    boolean momentLeader, momentSpoke, saidGoodnight, routineBusy;
    int momentTicks;

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
        this.goalSelector.addGoal(3, new AllyWorkGoal(this));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7) {
            @Override public boolean canUse() { return mode == Mode.WANDER && workJob() == null && !routineBusy && !isSleeping() && super.canUse(); }
        });
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F) {
            @Override public boolean canUse() { return !isSleeping() && super.canUse(); }
        });
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this) {
            @Override public boolean canUse() { return !isSleeping() && super.canUse(); }
        });
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
            if (o != null && tickCount % 100 == 0) refreshSkills(o);
        }
        if (tickCount % 10 == 0) pickTarget(level);
        if (helpJob != null && level.getGameTime() > helpUntil) { helpJob = null; helpPos = null; }
        routineBusy = AllyRoutine.tick(this, level);
        if (routineBusy) return;                                 // durmiendo, yendo a la cama o en un momento
        if (workJob() != null && getTarget() == null) return;   // trabajando: AllyWorkGoal manda
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
            double r = job == com.draxer.dexrider.rider.AllyWork.Job.GUARD && workJob() == job ? 20 : mode == Mode.WANDER ? 12 : 16;
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
        net.minecraft.world.item.ItemStack held = sp.getItemInHand(hand);
        if (held.getItem() instanceof com.draxer.dexrider.item.DexDriverItem) return super.mobInteract(player, hand);
        com.draxer.dexrider.rider.AllyWork.Job tool = com.draxer.dexrider.rider.AllyWork.Job.forTool(held);
        if (tool == com.draxer.dexrider.rider.AllyWork.Job.MINE && sp.isShiftKeyDown() && level() instanceof ServerLevel) {
            // Shift + pico: borrar zona y filtro y marcar una zona nueva
            refreshSkills(sp);
            skills = skills.withZone(java.util.List.of(), "").withFilter(java.util.List.of());
            com.draxer.dexrider.rider.AllyWork.save(sp, rider, skills);
            com.draxer.dexrider.rider.AllyWork.pickForZone(sp, rider);
            sp.sendSystemMessage(Component.translatable("message.dexrider.ally_zone_select", rider.displayName()), false);
            return InteractionResult.SUCCESS;
        }
        if (tool != null && level() instanceof ServerLevel level) {
            assignJob(level, sp, tool);
            return InteractionResult.SUCCESS;
        }
        java.util.List<String> filter = com.draxer.dexrider.rider.AllyWork.filterFor(held);
        if (!filter.isEmpty() && !sp.isShiftKeyDown()) {
            refreshSkills(sp);
            boolean same = skills.filter().equals(filter);
            skills = skills.withFilter(same ? java.util.List.of() : filter);
            com.draxer.dexrider.rider.AllyWork.save(sp, rider, skills);
            sp.sendSystemMessage(same ? Component.translatable("message.dexrider.ally_filter_clear", rider.displayName())
                    : Component.translatable("message.dexrider.ally_filter_set", rider.displayName(), com.draxer.dexrider.rider.AllyWork.filterName(filter)), true);
            return InteractionResult.SUCCESS;
        }
        if (sp.isShiftKeyDown()) {
            AllyManager.setHome(sp, this, blockPosition());
        } else {
            openMenu(sp);
        }
        return InteractionResult.SUCCESS;
    }

    // ================================================================== menu (clic derecho con la mano vacia)

    public void openMenu(ServerPlayer sp) {
        refreshSkills(sp);
        BlockPos[] z = skills.hasZone() ? new BlockPos[]{com.draxer.dexrider.rider.AllyWork.zoneMin(skills), com.draxer.dexrider.rider.AllyWork.zoneMax(skills)} : null;
        int bagCount = 0;
        for (net.minecraft.world.item.ItemStack s : bag) bagCount += s.getCount();
        String filterName = skills.filter().isEmpty() ? "" : com.draxer.dexrider.rider.AllyWork.filterName(skills.filter()).getString();
        com.draxer.dexrider.network.AllyMenuPayload.send(sp, new com.draxer.dexrider.network.AllyMenuPayload(
                getId(), rider.id(), mode == Mode.SUMMONED, home != null, skills.job(),
                skills.xp("mine"), skills.xp("chop"), skills.xp("farm"), skills.xp("craft"), filterName,
                z == null ? 0 : z[1].getX() - z[0].getX() + 1, z == null ? 0 : z[1].getY() - z[0].getY() + 1, z == null ? 0 : z[1].getZ() - z[0].getZ() + 1,
                bagCount, skills.patterns().size(), skills.blocks().size(), skills.trust(),
                com.draxer.dexrider.rider.AllyBlueprint.blockCount(skills)));
    }

    /** Un boton del menu. Despues refresca el menu del jugador (salvo los que lo cierran). */
    public void menuAction(ServerPlayer sp, int action) {
        if (owner == null || !owner.equals(sp.getUUID()) || !(level() instanceof ServerLevel level)) return;
        refreshSkills(sp);
        com.draxer.dexrider.rider.AllyWork.Job J = null;
        switch (action) {
            case com.draxer.dexrider.network.AllyActionPayload.CHAT -> {
                // abre la conversacion: saluda y cuenta la charla del dia
                getLookControl().setLookAt(sp);
                com.draxer.dexrider.rider.AllyTrust.onTalk(level, sp, this);
                com.draxer.dexrider.rider.AllyTrust.sendTalk(sp, this, com.draxer.dexrider.rider.AllyTrust.answerKey(level, rider, -1));
                return;
            }
            case com.draxer.dexrider.network.AllyActionPayload.GIFT -> {
                getLookControl().setLookAt(sp);
                String k = com.draxer.dexrider.rider.AllyTrust.onGift(level, sp, this);
                if (k == null) {
                    sp.sendSystemMessage(Component.translatable("message.dexrider.gift_empty"), true);
                    return;
                }
                swing(InteractionHand.MAIN_HAND);
                com.draxer.dexrider.rider.FX.particle(level, net.minecraft.core.particles.ParticleTypes.HEART, getEyePosition(), 4, 0.3, 0.02);
                com.draxer.dexrider.rider.AllyTrust.sendTalk(sp, this, k);
                return;
            }
            case com.draxer.dexrider.network.AllyActionPayload.BACK -> { }
            case com.draxer.dexrider.network.AllyActionPayload.TOPIC_BASE,
                 com.draxer.dexrider.network.AllyActionPayload.TOPIC_BASE + 1,
                 com.draxer.dexrider.network.AllyActionPayload.TOPIC_BASE + 2,
                 com.draxer.dexrider.network.AllyActionPayload.TOPIC_BASE + 3,
                 com.draxer.dexrider.network.AllyActionPayload.TOPIC_BASE + 4 -> {
                int topic = action - com.draxer.dexrider.network.AllyActionPayload.TOPIC_BASE;
                getLookControl().setLookAt(sp);
                if ((com.draxer.dexrider.rider.AllyTrust.topics(skills.trust()) & (1 << topic)) != 0)
                    com.draxer.dexrider.rider.AllyTrust.sendTalk(sp, this, com.draxer.dexrider.rider.AllyTrust.answerKey(level, rider, topic));
                return;
            }
            case com.draxer.dexrider.network.AllyActionPayload.TOGGLE -> AllyManager.toggle(sp, rider);
            case com.draxer.dexrider.network.AllyActionPayload.LIVE_HERE -> AllyManager.setHome(sp, this, blockPosition());
            case com.draxer.dexrider.network.AllyActionPayload.JOB_MINE -> J = com.draxer.dexrider.rider.AllyWork.Job.MINE;
            case com.draxer.dexrider.network.AllyActionPayload.JOB_CHOP -> J = com.draxer.dexrider.rider.AllyWork.Job.CHOP;
            case com.draxer.dexrider.network.AllyActionPayload.JOB_FARM -> J = com.draxer.dexrider.rider.AllyWork.Job.FARM;
            case com.draxer.dexrider.network.AllyActionPayload.JOB_CRAFT -> J = com.draxer.dexrider.rider.AllyWork.Job.CRAFT;
            case com.draxer.dexrider.network.AllyActionPayload.JOB_FLATTEN -> J = com.draxer.dexrider.rider.AllyWork.Job.FLATTEN;
            case com.draxer.dexrider.network.AllyActionPayload.JOB_FISH -> J = com.draxer.dexrider.rider.AllyWork.Job.FISH;
            case com.draxer.dexrider.network.AllyActionPayload.JOB_BREED -> J = com.draxer.dexrider.rider.AllyWork.Job.BREED;
            case com.draxer.dexrider.network.AllyActionPayload.JOB_SMELT -> J = com.draxer.dexrider.rider.AllyWork.Job.SMELT;
            case com.draxer.dexrider.network.AllyActionPayload.JOB_GUARD -> J = com.draxer.dexrider.rider.AllyWork.Job.GUARD;
            case com.draxer.dexrider.network.AllyActionPayload.JOB_STOP -> {
                if (job != null) assignJob(level, sp, job);     // mismo trabajo = parar
            }
            case com.draxer.dexrider.network.AllyActionPayload.ZONE_MARK -> {
                skills = skills.withZone(java.util.List.of(), "");
                com.draxer.dexrider.rider.AllyWork.save(sp, rider, skills);
                com.draxer.dexrider.rider.AllyWork.pickForZone(sp, rider);
                sp.sendSystemMessage(Component.translatable("message.dexrider.ally_zone_select_menu", rider.displayName()), false);
                return;     // el cliente cierra el menu para marcar
            }
            case com.draxer.dexrider.network.AllyActionPayload.PLAN_COPY -> {
                com.draxer.dexrider.rider.AllyBlueprint.Capture c = com.draxer.dexrider.rider.AllyBlueprint.capture(level, skills);
                if (c.entry() != null) {
                    skills = c.entry();
                    com.draxer.dexrider.rider.AllyWork.save(sp, rider, skills);
                    com.draxer.dexrider.rider.RiderDialogue.force(level, this, rider, com.draxer.dexrider.rider.RiderDialogue.Line.LEARN);
                }
                sp.sendSystemMessage(Component.translatable(c.message(), rider.displayName(), c.count(),
                        com.draxer.dexrider.rider.AllyBlueprint.MAX_SIDE, com.draxer.dexrider.rider.AllyBlueprint.MAX_BLOCKS), false);
            }
            case com.draxer.dexrider.network.AllyActionPayload.BUILD_PICK -> {
                if (!skills.hasPlan()) sp.sendSystemMessage(Component.translatable("message.dexrider.ally_plan_none", rider.displayName()), false);
                else {
                    com.draxer.dexrider.rider.AllyBlueprint.pickBuildSpot(sp, rider);
                    sp.sendSystemMessage(Component.translatable("message.dexrider.ally_build_pick", rider.displayName()), false);
                }
                return;     // el cliente cierra el menu para elegir el sitio
            }
            case com.draxer.dexrider.network.AllyActionPayload.ZONE_CLEAR -> {
                skills = skills.withZone(java.util.List.of(), "");
                com.draxer.dexrider.rider.AllyWork.save(sp, rider, skills);
            }
            case com.draxer.dexrider.network.AllyActionPayload.FILTER_HAND -> {
                java.util.List<String> f = com.draxer.dexrider.rider.AllyWork.filterFor(sp.getMainHandItem());
                if (f.isEmpty()) sp.sendSystemMessage(Component.translatable("message.dexrider.ally_filter_none"), true);
                else {
                    skills = skills.withFilter(f);
                    com.draxer.dexrider.rider.AllyWork.save(sp, rider, skills);
                }
            }
            case com.draxer.dexrider.network.AllyActionPayload.FILTER_CLEAR -> {
                skills = skills.withFilter(java.util.List.of());
                com.draxer.dexrider.rider.AllyWork.save(sp, rider, skills);
            }
            case com.draxer.dexrider.network.AllyActionPayload.TAKE_BAG -> {
                for (net.minecraft.world.item.ItemStack s : bagTakeAll()) com.draxer.dexrider.rider.AllyWork.give(sp, s);
            }
            default -> { }
        }
        if (J != null && J != job) assignJob(level, sp, J);
        if (isAlive()) openMenu(sp);
    }

    // ================================================================== trabajo (AllyWork)

    /** Puede aprender mirandote (no los duplicados ni las proyecciones). */
    public boolean canLearn() {
        return mode == Mode.WANDER || mode == Mode.SUMMONED;
    }

    private void refreshSkills(ServerPlayer o) {
        skills = com.draxer.dexrider.rider.AllyWork.entry(o, rider);
        job = com.draxer.dexrider.rider.AllyWork.Job.byId(skills.job());
    }

    /** Se pone a ayudarte un rato con lo que estas haciendo. False si ya estaba en eso o no puede. */
    public boolean startHelping(com.draxer.dexrider.rider.AllyWork.Job j, BlockPos pos) {
        if (!canLearn() || j == com.draxer.dexrider.rider.AllyWork.Job.CRAFT || getTarget() != null || !(level() instanceof ServerLevel level)) return false;
        ServerPlayer o = ownerPlayer();
        if (o != null) refreshSkills(o);
        boolean already = helpJob == j;
        helpJob = j;
        helpPos = pos.immutable();
        helpBlock = com.draxer.dexrider.rider.AllyWork.blockId(level.getBlockState(pos));
        if (helpBlock.equals("minecraft:air") && j == com.draxer.dexrider.rider.AllyWork.Job.MINE) helpBlock = "";
        helpUntil = level.getGameTime() + 600;
        return !already;
    }

    public boolean isHelping() { return helpJob != null; }
    public String helpBlock() { return helpBlock; }

    /** Lo que esta haciendo ahora: ayudarte tiene prioridad sobre el encargo (que solo hace en casa). */
    @Nullable
    public com.draxer.dexrider.rider.AllyWork.Job workJob() {
        if (helpJob != null) return helpJob;
        if (job != null && mode == Mode.WANDER && home != null && (job.chore() || skills.knows(job.skill()))) {
            if (job == com.draxer.dexrider.rider.AllyWork.Job.FLATTEN && workZone() == null) return null;
            return job;
        }
        return null;
    }

    @Nullable
    public BlockPos workCenter() {
        if (helpJob != null) {
            ServerPlayer o = ownerPlayer();
            return o != null && o.level() == level() ? o.blockPosition() : helpPos;
        }
        BlockPos[] z = workZone();
        if (z != null && (job == com.draxer.dexrider.rider.AllyWork.Job.MINE || job == com.draxer.dexrider.rider.AllyWork.Job.FLATTEN)) {
            return new BlockPos((z[0].getX() + z[1].getX()) / 2, z[1].getY() + 1, (z[0].getZ() + z[1].getZ()) / 2);
        }
        return home;
    }

    /** Zona marcada para picar ([min, max]) si esta en esta dimension y no esta ayudandote. */
    @Nullable
    public BlockPos[] workZone() {
        if (helpJob != null || !skills.hasZone() || !(level() instanceof ServerLevel level)) return null;
        if (!level.dimension().identifier().toString().equals(skills.zoneDim())) return null;
        return new BlockPos[]{com.draxer.dexrider.rider.AllyWork.zoneMin(skills), com.draxer.dexrider.rider.AllyWork.zoneMax(skills)};
    }

    public java.util.List<String> workFilter() { return skills.filter(); }

    public int workXp(com.draxer.dexrider.rider.AllyWork.Job j) { return skills.xp(j.skill()); }
    public int trust() { return skills.trust(); }
    private int workActions;
    public java.util.List<String> workBlocks() { return skills.blocks(); }
    public java.util.List<com.draxer.dexrider.item.AllySkills.Pattern> workPatterns() { return skills.patterns(); }

    /** Practicar tambien ensenia (poco a poco hasta dominarlo). */
    public void gainWorkXp(com.draxer.dexrider.rider.AllyWork.Job j, int amount) {
        ServerPlayer o = ownerPlayer();
        if (o == null || tickCount % 4 != 0) return;     // un punto cada pocos bloques
        skills = com.draxer.dexrider.rider.AllyWork.entry(o, rider);
        skills = skills.withXp(j.skill(), skills.xp(j.skill()) + amount);
        com.draxer.dexrider.rider.AllyWork.save(o, rider, skills);
        if (++workActions % 10 == 0) {     // cada tanto, trabajar para ti tambien une
            com.draxer.dexrider.rider.AllyTrust.onWork(o, this);
            skills = com.draxer.dexrider.rider.AllyWork.entry(o, rider);
        }
    }

    private void assignJob(ServerLevel level, ServerPlayer o, com.draxer.dexrider.rider.AllyWork.Job j) {
        refreshSkills(o);
        com.draxer.dexrider.rider.RiderDialogue.forget(this);
        if (!j.chore() && !skills.knows(j.skill())) {
            com.draxer.dexrider.rider.RiderDialogue.force(level, this, rider, com.draxer.dexrider.rider.RiderDialogue.Line.NOT_YET, j.displayName());
            int pct = Math.min(99, skills.xp(j.skill()) * 100 / com.draxer.dexrider.item.AllySkills.LEARNED);
            o.sendSystemMessage(Component.translatable("message.dexrider.ally_job_learning", rider.displayName(), j.displayName(), pct), true);
            return;
        }
        if (j == com.draxer.dexrider.rider.AllyWork.Job.FLATTEN && job != j && !skills.hasZone()) {
            o.sendSystemMessage(Component.translatable("message.dexrider.ally_flatten_nozone", rider.displayName()), false);
            return;
        }
        if (job == j) {
            skills = skills.withJob("");
            job = null;
            com.draxer.dexrider.rider.RiderDialogue.force(level, this, rider, com.draxer.dexrider.rider.RiderDialogue.Line.JOB_STOP);
            o.sendSystemMessage(Component.translatable("message.dexrider.ally_job_stop", rider.displayName()), true);
        } else {
            skills = skills.withJob(j.id());
            job = j;
            com.draxer.dexrider.rider.RiderDialogue.force(level, this, rider, com.draxer.dexrider.rider.RiderDialogue.Line.JOB, j.displayName());
            o.sendSystemMessage(Component.translatable(home != null && com.draxer.dexrider.rider.AllyWork.chestNear(level, home) == null && j != null
                    ? "message.dexrider.ally_job_nochest" : "message.dexrider.ally_job", rider.displayName(), j.displayName()), true);
        }
        com.draxer.dexrider.rider.AllyWork.save(o, rider, skills);
    }

    public com.draxer.dexrider.item.AllySkills.Entry skillsEntry() { return skills; }

    /** Termino (o ya no puede seguir) la copia: deja el encargo. */
    public void finishBuild(ServerLevel level, boolean done) {
        ServerPlayer o = ownerPlayer();
        if (o == null) return;
        refreshSkills(o);
        skills = skills.withJob("").withBuild(java.util.List.of(), "");
        job = null;
        com.draxer.dexrider.rider.AllyWork.save(o, rider, skills);
        if (done) {
            FX.particle(level, net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, getEyePosition(), 10, 0.5, 0.0);
            com.draxer.dexrider.rider.RiderDialogue.force(level, this, rider, com.draxer.dexrider.rider.RiderDialogue.Line.JOB_STOP);
            o.sendSystemMessage(Component.translatable("message.dexrider.ally_build_done", rider.displayName()), false);
        }
    }

    public boolean bagHas(net.minecraft.world.item.Item it) {
        for (net.minecraft.world.item.ItemStack s : bag) if (s.is(it)) return true;
        return false;
    }

    public boolean bagTake(net.minecraft.world.item.Item it) {
        for (int i = 0; i < bag.size(); i++) {
            net.minecraft.world.item.ItemStack s = bag.get(i);
            if (!s.is(it)) continue;
            s.shrink(1);
            if (s.isEmpty()) bag.remove(i);
            return true;
        }
        return false;
    }

    public boolean bagEmpty() { return bag.isEmpty(); }
    public boolean bagFull() { return bag.size() >= BAG_SLOTS; }

    public void bagAdd(net.minecraft.world.item.ItemStack s) {
        net.minecraft.world.item.ItemStack rest = s.copy();
        for (net.minecraft.world.item.ItemStack in : bag) {
            if (rest.isEmpty()) break;
            if (net.minecraft.world.item.ItemStack.isSameItemSameComponents(in, rest)) {
                int move = Math.min(rest.getCount(), in.getMaxStackSize() - in.getCount());
                in.grow(move);
                rest.shrink(move);
            }
        }
        if (rest.isEmpty()) return;
        if (bag.size() < BAG_SLOTS) bag.add(rest);
        else if (level() instanceof ServerLevel) spawnAtLocation((ServerLevel) level(), rest);
    }

    public java.util.List<net.minecraft.world.item.ItemStack> bagView() { return java.util.Collections.unmodifiableList(bag); }

    /** Saca un bloque de relleno de la bolsa (tierra, piedra...) o null. */
    @Nullable
    public net.minecraft.world.level.block.state.BlockState bagTakeFiller() {
        for (int i = 0; i < bag.size(); i++) {
            net.minecraft.world.item.ItemStack st = bag.get(i);
            if (com.draxer.dexrider.rider.AllyWork.isFiller(st)) {
                var s = ((net.minecraft.world.item.BlockItem) st.getItem()).getBlock().defaultBlockState();
                st.shrink(1);
                if (st.isEmpty()) bag.remove(i);
                return s;
            }
        }
        return null;
    }

    public java.util.List<net.minecraft.world.item.ItemStack> bagTakeAll() {
        java.util.List<net.minecraft.world.item.ItemStack> out = new java.util.ArrayList<>(bag);
        bag.clear();
        return out;
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
        if (!bag.isEmpty()) out.store("dex_bag", net.minecraft.world.item.ItemStack.CODEC.listOf(), java.util.List.copyOf(bag));
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
        bag.clear();
        in.read("dex_bag", net.minecraft.world.item.ItemStack.CODEC.listOf()).ifPresent(bag::addAll);
    }
}
