package com.draxer.dexrider.rider;

import com.draxer.dexrider.entity.RiderAllyEntity;
import com.draxer.dexrider.item.AllyBook;
import com.draxer.dexrider.item.DexDriverItem;
import com.draxer.dexrider.item.DriverDeck;
import com.draxer.dexrider.registry.DexComponents;
import com.draxer.dexrider.registry.DexEntities;
import com.draxer.dexrider.registry.DexSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Amigos Rider: los Riders que venciste en combate real se quedan como amigos.
 *
 *  - Viven en su casa (donde los venciste; Shift + clic derecho sobre ellos la cambia) y aparecen cuando estas cerca.
 *  - Se invocan con la pantalla de invocar (tecla B) o con el teclado numerico (1-9, en el orden del anillo del K-Touch).
 *  - Invocado: pelea a tu lado 90 s y luego vuelve a casa a descansar 2 min. Volver a llamarlo antes = despedirlo.
 *  - Maximo tres invocados a la vez. Si cae en combate, se retira y descansa 5 min.
 */
public final class AllyManager {
    private AllyManager() {}

    public static final int SUMMON_TICKS = 1800;   // 90 s
    public static final int REST_TICKS = 2400;     // 2 min
    public static final int MAX_ACTIVE = 3;

    // ------------------------------------------------------------------ libreta en el Driver

    public static AllyBook book(ServerPlayer player) {
        ItemStack driver = DexDriverItem.findDriver(player);
        AllyBook b = driver.isEmpty() ? null : driver.get(DexComponents.ALLIES.get());
        return b == null ? AllyBook.EMPTY : b;
    }

    private static void save(ServerPlayer player, AllyBook book) {
        ItemStack driver = DexDriverItem.findDriver(player);
        if (!driver.isEmpty()) driver.set(DexComponents.ALLIES.get(), book);
    }

    public static boolean isFriend(ServerPlayer player, KamenRide rider) {
        if (rider.card == null || rider == KamenRide.DEX || !rider.implemented) return false;
        DriverDeck deck = DexDriverItem.getDeck(DexDriverItem.findDriver(player));
        return deck.has(rider.card);
    }

    // ------------------------------------------------------------------ hacerse amigos

    public static void befriend(ServerLevel level, ServerPlayer player, KamenRide rider, BlockPos where) {
        if (!rider.implemented || DexEntities.ally(rider) == null) return;
        AllyBook book = book(player);
        DexAdvancements.award(player, "ally");
        if (rider == KamenRide.NINE) giveScarab(player);
        int gen = book.gen(rider.id()) + 1;
        save(player, book.withGen(rider.id(), gen).withHome(rider.id(), where.asLong(), level.dimension().identifier().toString()));
        RiderAllyEntity ally = spawn(level, player, rider, gen, RiderAllyEntity.Mode.WANDER, Vec3.atBottomCenterOf(where), where);
        if (ally != null) ally.scheduleSuitLine();
        // Con Takumi y Genzo como amigos, Genzo saca el equipo Blaster de la casa de la montania
        if ((rider == KamenRide.ORGA && isFriend(player, KamenRide.CIPHER)) || (rider == KamenRide.CIPHER && isFriend(player, KamenRide.ORGA))) {
            CipherGifts.giveBlaster(level, rider == KamenRide.ORGA && ally != null ? ally : player, player);
        }
        player.sendSystemMessage(Component.translatable("message.dexrider.ally_befriended", rider.displayName())
                .withStyle(ChatFormatting.AQUA));
    }

    @Nullable
    private static RiderAllyEntity spawn(ServerLevel level, ServerPlayer owner, KamenRide rider, int gen,
                                         RiderAllyEntity.Mode mode, Vec3 pos, @Nullable BlockPos home) {
        var type = DexEntities.ally(rider);
        if (type == null) return null;
        RiderAllyEntity ally = type.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (ally == null) return null;
        ally.setup(owner.getUUID(), gen, mode, home);
        ally.setPos(pos.x, pos.y, pos.z);
        ally.setYRot(owner.getYRot());
        level.addFreshEntity(ally);
        FX.dust(level, rider.color, 1.4F, pos.add(0, 1, 0), 30, 0.6);
        FX.card(level, pos.add(0, 1, 0), owner.getLookAngle(), rider.color);
        return ally;
    }

    @Nullable
    private static RiderAllyEntity findLoaded(ServerPlayer owner, KamenRide rider) {
        for (ServerLevel level : owner.level().getServer().getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                if (e instanceof RiderAllyEntity a && a.rider() == rider && owner.getUUID().equals(a.owner())
                        && a.mode() != RiderAllyEntity.Mode.DOUBLE && a.mode() != RiderAllyEntity.Mode.PROJECTION && a.isAlive()) return a;
            }
        }
        return null;
    }

    private static int activeCount(ServerPlayer owner, long now) {
        AllyBook b = book(owner);
        int n = 0;
        for (long until : b.active().values()) if (until > now) n++;
        return n;
    }

    // ------------------------------------------------------------------ invocar / despedir

    /** Indice del anillo del K-Touch (0-8). */
    public static void summonByIndex(ServerPlayer player, int index) {
        KamenRide[] ring = KamenRide.ring(player);
        if (index < 0 || index >= ring.length) return;
        // en Complete Form, los pads son los blasones: invocan a cada Rider (o a su proyeccion)
        if (KamenRide.current(player) == KamenRide.COMPLETE) {
            KTouch.toggleCrest(player, ring[index]);
            return;
        }
        toggle(player, ring[index]);
    }

    public static void toggle(ServerPlayer player, KamenRide rider) {
        if (!(player.level() instanceof ServerLevel level)) return;
        if (DexDriverItem.findDriver(player).isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.dexrider.need_driver"), true);
            return;
        }
        if (!isFriend(player, rider)) {
            player.sendSystemMessage(Component.translatable("message.dexrider.ally_not_friend", rider.displayName()), true);
            return;
        }
        long now = level.getGameTime();
        AllyBook book = book(player);
        String id = rider.id();

        // Ya esta peleando: despedirlo
        if (book.activeUntil(id) > now) {
            RiderAllyEntity a = findLoaded(player, rider);
            if (a != null) sendHome(level, a, true);
            else save(player, book.withActive(id, 0).withReady(id, now + REST_TICKS));
            return;
        }
        if (book.readyAt(id) > now) {
            player.sendSystemMessage(Component.translatable("message.dexrider.ally_resting", rider.displayName(),
                    (book.readyAt(id) - now) / 20), true);
            return;
        }
        if (activeCount(player, now) >= MAX_ACTIVE) {
            player.sendSystemMessage(Component.translatable("message.dexrider.ally_max", MAX_ACTIVE), true);
            return;
        }

        Vec3 at = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(-1.6))
                .add(player.getRandom().nextGaussian() * 0.6, 0, player.getRandom().nextGaussian() * 0.6);
        RiderAllyEntity ally = findLoaded(player, rider);
        int gen = book.gen(id);
        if (book.isReturned(id)) {
            // Ya volvio a su mundo: llega por un portal
            if (ally != null) ally.discard();
            gen++;
            book = book.withGen(id, gen);
            portalFx(level, new Vec3(at.x, player.getY(), at.z), rider.color);
            ally = spawn(level, player, rider, gen, RiderAllyEntity.Mode.SUMMONED, new Vec3(at.x, player.getY(), at.z), null);
            if (ally == null) return;
            ally.setVisitor(true);
            save(player, book.withActive(id, now + SUMMON_TICKS));
            RiderDialogue.force(level, ally, rider, RiderDialogue.Line.RETURN);
            return;
        }
        if (ally != null && ally.level() == level && ally.gen() == gen) {
            ally.teleportTo(at.x, player.getY(), at.z);
            FX.dust(level, rider.color, 1.4F, at.add(0, 1, 0), 30, 0.6);
        } else {
            if (ally != null) ally.discard();
            gen++;
            book = book.withGen(id, gen);
            ally = spawn(level, player, rider, gen, RiderAllyEntity.Mode.SUMMONED, new Vec3(at.x, player.getY(), at.z),
                    book.home().containsKey(id) ? BlockPos.of(book.home().get(id)) : null);
        }
        if (ally == null) return;
        ally.setMode(RiderAllyEntity.Mode.SUMMONED);
        save(player, book.withActive(id, now + SUMMON_TICKS));
        RiderDialogue.force(level, ally, rider, RiderDialogue.Line.SUMMONED);
        FX.cardPath(level, player.getEyePosition(), ally.position().add(0, 1, 0), 2, rider.color);
        DexSounds.play(level, player, DexSounds.CARD_INSERT, 1.0F, 1.2F);
        player.sendSystemMessage(Component.translatable("message.dexrider.ally_summoned", rider.displayName())
                .withStyle(ChatFormatting.AQUA), true);
    }

    /** Termina la invocacion: descanso y de vuelta a casa (si su casa esta cargada en este mundo, va alli). */
    public static void sendHome(ServerLevel level, RiderAllyEntity ally, boolean rest) {
        ServerPlayer o = ally.ownerPlayer();
        String id = ally.rider().id();
        if (o != null) {
            AllyBook b = book(o).withActive(id, 0);
            if (rest) b = b.withReady(id, level.getGameTime() + REST_TICKS);
            save(o, b);
            o.sendSystemMessage(Component.translatable("message.dexrider.ally_home", ally.rider().displayName()), true);
        }
        ally.setMode(RiderAllyEntity.Mode.WANDER);
        ally.setTarget(null);
        RiderDialogue.force(level, ally, ally.rider(), RiderDialogue.Line.HOME);
        if (ally.isVisitor()) {
            portalFx(level, ally.position(), ally.rider().color);
            ally.vanish(level);
            return;
        }
        BlockPos home = ally.home();
        if (home != null && level.isLoaded(home) && home.distToCenterSqr(ally.position()) < 256 * 256) {
            FX.dust(level, ally.rider().color, 1.2F, FX.center(ally), 15, 0.4);
            ally.teleportTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
        } else {
            ally.vanish(level);    // reaparece en su casa cuando pases por alli
        }
    }

    public static void rest(ServerPlayer owner, KamenRide rider, int ticks) {
        long now = owner.level().getGameTime();
        save(owner, book(owner).withActive(rider.id(), 0).withReady(rider.id(), now + ticks));
    }

    public static void setHome(ServerPlayer owner, RiderAllyEntity ally, BlockPos pos) {
        ally.setHome(pos);
        save(owner, book(owner).withHome(ally.rider().id(), pos.asLong(), ally.level().dimension().identifier().toString()));
        owner.sendSystemMessage(Component.translatable("message.dexrider.ally_new_home", ally.rider().displayName()), true);
    }

    /** Duplicados solidos de Mirage (no son amigos aparte: duran 10 s). */
    public static void mirageDoubles(ServerLevel level, ServerPlayer player, int count) {
        var type = DexEntities.ally(KamenRide.MIRAGE);
        if (type == null) return;
        for (int i = 0; i < count; i++) {
            Vec3 side = FX.yaw(player.getLookAngle().multiply(1, 0, 1), i == 0 ? 90 : -90).scale(1.5);
            Vec3 at = player.position().add(side);
            RiderAllyEntity d = type.get().create(level, EntitySpawnReason.MOB_SUMMONED);
            if (d == null) continue;
            d.setup(player.getUUID(), -1, RiderAllyEntity.Mode.DOUBLE, null);
            d.setPos(at.x, player.getY(), at.z);
            d.setHealth(10.0F);
            level.addFreshEntity(d);
            FX.dust(level, 0xDCE4F0, 1.2F, at.add(0, 1, 0), 20, 0.4);
        }
    }

    // ------------------------------------------------------------------ cada 5 s por jugador

    /** Si pasas cerca de la casa de un amigo y no esta ahi (ni invocado, ni descansando), aparece. */
    public static void tickPlayer(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        ItemStack driver = DexDriverItem.findDriver(player);
        if (driver.isEmpty()) return;
        AllyBook book = book(player);
        long now = level.getGameTime();
        String dim = level.dimension().identifier().toString();
        for (KamenRide rider : DexEntities.NPC_RIDERS) {
            String id = rider.id();
            if (!book.home().containsKey(id) || !dim.equals(book.homeDim().get(id))) continue;
            if (book.isReturned(id) || book.activeUntil(id) > now || book.readyAt(id) > now || !isFriend(player, rider)) continue;
            BlockPos home = BlockPos.of(book.home().get(id));
            if (home.distToCenterSqr(player.position()) > 48 * 48 || !level.isLoaded(home)) continue;
            if (findLoaded(player, rider) != null) continue;
            int gen = book.gen(id) + 1;
            book = book.withGen(id, gen);
            save(player, book);
            spawn(level, player, rider, gen, RiderAllyEntity.Mode.WANDER, Vec3.atBottomCenterOf(home), home);
        }
        checkReunion(level, player, book);
    }

    // ------------------------------------------------------------------ reunion y portal a casa

    /** Estado de una reunion en curso (por jugador). */
    private static final class Reunion {
        final java.util.List<RiderAllyEntity> riders;
        final Vec3 portal;
        int ticks;
        Reunion(java.util.List<RiderAllyEntity> riders, Vec3 portal) { this.riders = riders; this.portal = portal; }
    }

    private static final java.util.Map<java.util.UUID, Reunion> REUNIONS = new java.util.HashMap<>();

    /**
     * Todos buscan a Dex: cuando TODOS los Riders perdidos que existen ya son tus amigos y estan juntos a tu lado
     * (a 20 bloques o menos: invocados o viviendo cerca), su senal combinada con el Driver abre un portal a su mundo.
     */
    private static void checkReunion(ServerLevel level, ServerPlayer player, AllyBook book) {
        if (REUNIONS.containsKey(player.getUUID()) || !com.draxer.dexrider.rider.Transformation.isTransformed(player)) return;
        java.util.List<RiderAllyEntity> here = new java.util.ArrayList<>();
        for (KamenRide rider : DexEntities.NPC_RIDERS) {
            if (!isFriend(player, rider)) return;              // aun queda alguien perdido
            if (book.isReturned(rider.id())) continue;          // este ya se fue
            RiderAllyEntity a = findLoaded(player, rider);
            if (a == null || a.level() != level || a.distanceTo(player) > 20) return;
            here.add(a);
        }
        if (here.isEmpty()) return;
        Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
        REUNIONS.put(player.getUUID(), new Reunion(here, player.position().add(look.scale(5)).add(0, 1.2, 0)));
        for (RiderAllyEntity a : here) {
            a.setTarget(null);
            a.getNavigation().moveTo(player, 1.0);
        }
    }

    /** Cada tick del jugador: avanza la escena de la reunion. */
    public static void tickReunion(ServerPlayer player) {
        Reunion r = REUNIONS.get(player.getUUID());
        if (r == null || !(player.level() instanceof ServerLevel level)) return;
        int t = r.ticks++;
        int n = r.riders.size();
        int talkEnd = n * 50;
        int portalAt = talkEnd + 40;
        // 1. cada uno dice algo al verse todos juntos
        if (t < talkEnd && t % 50 == 0) {
            RiderAllyEntity a = r.riders.get(t / 50);
            if (a.isAlive()) RiderDialogue.force(level, a, a.rider(), RiderDialogue.Line.REUNION);
        }
        // 2. se abre el portal
        if (t == portalAt) {
            RiderAllyEntity first = r.riders.get(0);
            if (first.isAlive()) RiderDialogue.force(level, first, first.rider(), RiderDialogue.Line.PORTAL);
            level.playSound(null, r.portal.x, r.portal.y, r.portal.z, net.minecraft.sounds.SoundEvents.END_PORTAL_SPAWN,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        if (t >= portalAt) {
            int k = (t / 6) % n;
            portalRing(level, r.portal, r.riders.get(k).rider().color, t);
        }
        // 3. uno a uno se despiden, te dejan su moto y cruzan
        int step = t - portalAt - 60;
        if (step >= 0 && step % 70 == 0) {
            int i = step / 70;
            if (i < n) {
                RiderAllyEntity a = r.riders.get(i);
                KamenRide rider = a.rider();
                if (a.isAlive()) {
                    RiderDialogue.force(level, a, rider, RiderDialogue.Line.FAREWELL);
                    a.teleportTo(r.portal.x, r.portal.y - 1.2, r.portal.z);
                    FX.dust(level, rider.color, 1.6F, r.portal, 40, 0.8);
                    a.discard();
                }
                com.draxer.dexrider.vehicle.BikeKind bike = com.draxer.dexrider.vehicle.BikeKind.forRider(rider);
                if (bike != null) {
                    ItemStack card = new ItemStack(com.draxer.dexrider.registry.DexItems.card(bike.card).get());
                    if (!player.getInventory().add(card)) player.drop(card, false);
                    DexDriverItem.registerCard(player, bike.card);
                    player.sendSystemMessage(Component.translatable("message.dexrider.bike_gift", rider.displayName(), bike.displayName())
                            .withStyle(ChatFormatting.AQUA));
                }
                save(player, book(player).withReturned(rider.id(), level.getGameTime()).withActive(rider.id(), 0));
            } else {
                // 4. se cierra
                FX.particle(level, net.minecraft.core.particles.ParticleTypes.END_ROD, r.portal, 60, 1.0, 0.2);
                player.sendSystemMessage(Component.translatable("message.dexrider.portal_closed").withStyle(ChatFormatting.LIGHT_PURPLE));
                REUNIONS.remove(player.getUUID());
            }
        }
    }

    public static void cancelReunion(ServerPlayer player) {
        REUNIONS.remove(player.getUUID());
    }

    private static void portalRing(ServerLevel level, Vec3 c, int color, int t) {
        for (int a = 0; a < 360; a += 20) {
            double r = Math.toRadians(a + t * 4);
            Vec3 p = c.add(Math.cos(r) * 1.6, Math.sin(r) * 2.0, 0);
            FX.dust(level, color, 1.1F, p, 1, 0.02);
        }
        if (t % 3 == 0) FX.particle(level, net.minecraft.core.particles.ParticleTypes.PORTAL, c, 12, 0.6, 0.4);
    }

    /** Destello de portal (llegadas y salidas de los que ya volvieron a su mundo). */
    public static void portalFx(ServerLevel level, Vec3 at, int color) {
        for (int t = 0; t < 40; t += 8) portalRing(level, at.add(0, 1.2, 0), color, t);
        FX.particle(level, net.minecraft.core.particles.ParticleTypes.PORTAL, at.add(0, 1, 0), 40, 0.6, 0.5);
        level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 0.6F);
    }

    /** Izuku te deja su escarabajo (Vehicle Ride: Scarab) la primera vez que os haceis amigos. */
    private static void giveScarab(ServerPlayer player) {
        var driver = com.draxer.dexrider.item.DexDriverItem.findDriver(player);
        if (driver.isEmpty()) return;
        var deck = com.draxer.dexrider.item.DexDriverItem.getDeck(driver);
        if (deck.has(com.draxer.dexrider.item.DexCard.VEHICLE_RIDE_SCARAB)) return;
        com.draxer.dexrider.item.DexDriverItem.setDeck(driver, deck.withCard(com.draxer.dexrider.item.DexCard.VEHICLE_RIDE_SCARAB));
        var card = new net.minecraft.world.item.ItemStack(com.draxer.dexrider.registry.DexItems.card(com.draxer.dexrider.item.DexCard.VEHICLE_RIDE_SCARAB).get());
        if (!player.getInventory().add(card)) player.drop(card, false);
        player.sendSystemMessage(Component.translatable("message.dexrider.scarab_gift"));
    }

    /** Amigo de verdad (no proyeccion ni doble) de este jugador, cerca. */
    @Nullable
    public static RiderAllyEntity nearbyFriend(ServerPlayer owner, KamenRide rider, double range) {
        if (!(owner.level() instanceof ServerLevel level)) return null;
        for (RiderAllyEntity a : level.getEntitiesOfClass(RiderAllyEntity.class, owner.getBoundingBox().inflate(range))) {
            if (a.rider() == rider && owner.getUUID().equals(a.owner()) && a.isAlive()
                    && (a.mode() == RiderAllyEntity.Mode.WANDER || a.mode() == RiderAllyEntity.Mode.SUMMONED)) return a;
        }
        return null;
    }

    /** Proyeccion de un Rider (Complete Form): pelea como el original mientras sostengas su blason. */
    public static void spawnProjection(ServerLevel level, ServerPlayer owner, KamenRide rider) {
        Vec3 look = owner.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 pos = owner.position().add(look.scale(2)).add(FX.yaw(look, 90).scale((owner.getRandom().nextDouble() - 0.5) * 4));
        spawn(level, owner, rider, book(owner).gen(rider.id()), RiderAllyEntity.Mode.PROJECTION, pos, null);
    }
}
