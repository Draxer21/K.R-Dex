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
            @Override public boolean canUse() { return mode == Mode.WANDER && workJob() == null && super.canUse(); }
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
            if (o != null && tickCount % 100 == 0) refreshSkills(o);
        }
        if (tickCount % 10 == 0) pickTarget(level);
        if (helpJob != null && level.getGameTime() > helpUntil) { helpJob = null; helpPos = null; }
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
        net.minecraft.world.item.ItemStack held = sp.getItemInHand(hand);
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
            AllyManager.toggle(sp, rider);
        }
        return InteractionResult.SUCCESS;
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
        if (job != null && mode == Mode.WANDER && home != null && skills.knows(job.skill())) {
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
    public java.util.List<String> workBlocks() { return skills.blocks(); }
    public java.util.List<com.draxer.dexrider.item.AllySkills.Pattern> workPatterns() { return skills.patterns(); }

    /** Practicar tambien ensenia (poco a poco hasta dominarlo). */
    public void gainWorkXp(com.draxer.dexrider.rider.AllyWork.Job j, int amount) {
        ServerPlayer o = ownerPlayer();
        if (o == null || tickCount % 4 != 0) return;     // un punto cada pocos bloques
        skills = com.draxer.dexrider.rider.AllyWork.entry(o, rider);
        skills = skills.withXp(j.skill(), skills.xp(j.skill()) + amount);
        com.draxer.dexrider.rider.AllyWork.save(o, rider, skills);
    }

    private void assignJob(ServerLevel level, ServerPlayer o, com.draxer.dexrider.rider.AllyWork.Job j) {
        refreshSkills(o);
        com.draxer.dexrider.rider.RiderDialogue.forget(this);
        if (!skills.knows(j.skill())) {
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
