package com.draxer.dexrider.rider;

import com.draxer.dexrider.item.DexCard;
import com.draxer.dexrider.item.DexDriverItem;
import com.draxer.dexrider.item.DriverDeck;
import com.draxer.dexrider.registry.DexItems;
import com.draxer.dexrider.registry.DexSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Escaneo del Driver, como en el fanfic:
 *  - Solo funciona transformado y con el Driver encima (inventario o ranura de cinturon).
 *  - Solo registra poderes y armas EN USO REAL.
 *  - Hay que verlo: a 20 bloques o menos, con linea de vision y mirando mas o menos hacia ahi.
 *  - Los poderes complejos necesitan varias muestras (DexCard.samplesToScan).
 *  - Las Form Ride que ya tienes suben de nivel al seguir escaneando (sustitucion por version mejorada).
 *
 * Dos vias:
 *  1. Golpes (onDamage): espadas, arcos, tridentes, rayos, guardianes, golems, ghasts, strays, blazes...
 *     Half-Cold Half-Hot: con Frost y Cinder en el mazo, ver fuego y hielo atacando con menos de 5 s de diferencia.
 *  2. Observacion continua (observe, cada 10 ticks): galopes, excavaciones, picadas de phantom, vehiculos.
 */
public final class Scanner {
    private Scanner() {}

    private static final double RANGE = 20.0;
    private static final long SAMPLE_COOLDOWN_TICKS = 40;   // una muestra cada 2 s por carta

    private static final Map<String, Long> LAST_SAMPLE = new HashMap<>();

    // ------------------------------------------------------------------ via 1: golpes

    /** Se llama desde LivingIncomingDamageEvent. */
    public static void onDamage(ServerLevel level, LivingEntity victim, DamageSource source) {
        Entity attacker = source.getEntity();
        DexCard card = classifyDamage(source, attacker);
        if (card == null) return;
        Entity seen = attacker != null ? attacker : victim;
        // Las armas de los Riders se pueden escanear aunque el golpe sea contra ti: es un combate real contra su duenio
        boolean riderWeapon = attacker instanceof com.draxer.dexrider.entity.RiderMob;
        for (ServerPlayer player : level.players()) {
            if (player == attacker || (player == victim && !riderWeapon)) continue;   // lo que te pasa a ti no se escanea
            if (!canSee(player, seen)) continue;
            noteElement(level, player, card);
            sample(level, player, card);
        }
    }

    // ------------------------------------------------------------------ Half-Cold Half-Hot

    private static final long HALF_WINDOW_TICKS = 100;   // fuego y hielo con menos de 5 s de diferencia
    private static final Map<String, Long> LAST_ELEMENT = new HashMap<>();

    /**
     * Para generar Half-Cold Half-Hot hay que tener ya Frost y Cinder y ver un ataque de fuego (blaze)
     * y uno de hielo (stray o golem de nieve) casi a la vez: el Driver "entiende" la combinacion.
     */
    private static void noteElement(ServerLevel level, ServerPlayer player, DexCard card) {
        if (card != DexCard.FORM_RIDE_FROST && card != DexCard.FORM_RIDE_CINDER) return;
        DriverDeck deck = DexDriverItem.getDeck(DexDriverItem.findDriver(player));
        if (!deck.has(DexCard.FORM_RIDE_FROST) || !deck.has(DexCard.FORM_RIDE_CINDER)) return;

        DexCard other = card == DexCard.FORM_RIDE_FROST ? DexCard.FORM_RIDE_CINDER : DexCard.FORM_RIDE_FROST;
        long now = level.getGameTime();
        LAST_ELEMENT.put(player.getUUID() + "|" + card.id(), now);
        Long seenOther = LAST_ELEMENT.get(player.getUUID() + "|" + other.id());
        if (seenOther != null && now - seenOther <= HALF_WINDOW_TICKS) {
            LAST_ELEMENT.remove(player.getUUID() + "|" + other.id());
            LAST_ELEMENT.remove(player.getUUID() + "|" + card.id());
            sample(level, player, DexCard.FORM_RIDE_HALF);
        }
    }

    private static DexCard classifyDamage(DamageSource source, Entity attacker) {
        if (source.is(DamageTypes.LIGHTNING_BOLT)) return DexCard.ARMED_RIDE_SHOCK_BATON;
        if (source.is(DamageTypes.SONIC_BOOM)) return DexCard.ATTACK_RIDE_GROUND_WAVE;
        if (source.is(DamageTypeTags.IS_EXPLOSION) && !(attacker instanceof net.minecraft.world.entity.player.Player)
                && (attacker == null || attacker.getType() == EntityType.CREEPER || attacker.getType() == EntityType.TNT
                    || source.getDirectEntity() != null && source.getDirectEntity().getType() == EntityType.TNT)) {
            return DexCard.ATTACK_RIDE_NITRO_BURST;
        }
        if (attacker == null) return null;

        // Un Rider peleando con su arma fuera
        if (attacker instanceof com.draxer.dexrider.entity.RiderMob rider && rider.hasWeaponOut() && !rider.isCivilian()) {
            return riderWeaponCard(rider.rider());
        }
        EntityType<?> type = attacker.getType();
        if (type == EntityType.GHAST) return DexCard.FORM_RIDE_GAS;
        if (type == EntityType.BREEZE) return DexCard.ATTACK_RIDE_GALE;
        if (attacker instanceof com.draxer.dexrider.entity.KaijinEntity k) {
            return switch (k.kind()) {
                case MANTIS -> DexCard.ATTACK_RIDE_MANTIS_REAP;
                case RHINO -> DexCard.ATTACK_RIDE_RHINO_CHARGE;
                case CHIMERA -> DexCard.ATTACK_RIDE_CHIMERA_DIVE;
                default -> null;
            };
        }
        if (type == EntityType.VEX) return DexCard.ATTACK_RIDE_RAZOR_FEATHER;
        if (type == EntityType.ENDERMAN) return DexCard.ATTACK_RIDE_OVERCLOCK;
        if (type == EntityType.BLAZE) return DexCard.FORM_RIDE_CINDER;
        if (type == EntityType.STRAY || type == EntityType.SNOW_GOLEM) return DexCard.FORM_RIDE_FROST;
        if (type == EntityType.GUARDIAN || type == EntityType.ELDER_GUARDIAN) return DexCard.ARMED_RIDE_VOLT_CHAIN;
        if (type == EntityType.IRON_GOLEM || type == EntityType.RAVAGER || type == EntityType.WARDEN) {
            return source.is(DamageTypeTags.IS_PROJECTILE) ? null : DexCard.ARMED_RIDE_POWER_KNUCKLE;
        }
        if (attacker instanceof LivingEntity living) {
            ItemStack held = living.getMainHandItem();
            if (source.is(DamageTypes.ARROW) && (held.is(Items.BOW) || held.is(Items.CROSSBOW))) {
                return DexCard.ARMED_RIDE_BLAST_SHOT;
            }
            if (held.is(Items.TRIDENT)) return DexCard.ARMED_RIDE_SHOCK_BATON;
            if (held.is(Items.MACE) && source.getDirectEntity() == attacker) return DexCard.ATTACK_RIDE_GROUND_WAVE;
            if (held.is(ItemTags.SWORDS) && source.getDirectEntity() == attacker) return DexCard.ARMED_RIDE_SWORD;
        }
        return null;
    }

    public static DexCard riderWeaponCard(KamenRide rider) {
        return switch (rider) {
            case CIPHER -> DexCard.ARMED_RIDE_CIPHER_EDGE;
            case MIRAGE -> DexCard.ARMED_RIDE_PRISM_EDGE;
            case PROTO -> DexCard.ARMED_RIDE_MODULE_DRIVER;
            case FORGE -> DexCard.ARMED_RIDE_FORGE_LANCE;
            case WARDEN -> DexCard.ARMED_RIDE_WARDEN_BATON;
            case ORGA -> DexCard.ARMED_RIDE_ORGA_HALBERD;
            case SPORE -> DexCard.ARMED_RIDE_SPORE_ROD;
            case KAIXA -> DexCard.ARMED_RIDE_KAIXA_GUNBLADE;
            default -> null;
        };
    }

    // ------------------------------------------------------------------ via 2: observacion

    /** Se llama cada 10 ticks para cada jugador transformado. */
    public static void observe(ServerLevel level, ServerPlayer player) {
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RANGE),
                e -> e != player && e.isAlive())) {
            DexCard card = classifyObservation(e);
            if (card != null && canSee(player, e)) sample(level, player, card);
        }
    }

    private static DexCard classifyObservation(LivingEntity e) {
        EntityType<?> type = e.getType();
        double speed = e.getDeltaMovement().horizontalDistance();

        // Speed: caballo, burro, mula o camello galopando con jinete
        if ((type == EntityType.HORSE || type == EntityType.DONKEY || type == EntityType.MULE
                || type == EntityType.CAMEL || type == EntityType.SKELETON_HORSE || type == EntityType.ZOMBIE_HORSE)
                && e.isVehicle() && speed > 0.25) {
            return DexCard.FORM_RIDE_SPEED;
        }
        // Terra: algo excavando o saliendo de la tierra (warden, sniffer)
        if (e.getPose() == Pose.DIGGING || e.getPose() == Pose.EMERGING) return DexCard.FORM_RIDE_TERRA;
        // Overclock: Cipher en Accel (lo que Yusuke leyo de su padre)
        if (e instanceof com.draxer.dexrider.entity.RiderMob rm && rm.rider() == KamenRide.CIPHER && rm.riderForm() == 1) {
            return DexCard.ATTACK_RIDE_OVERCLOCK;
        }
        // Sky: phantom en picada sobre un objetivo
        if (type == EntityType.PHANTOM && e instanceof Mob mob && mob.getTarget() != null) return DexCard.FORM_RIDE_SKY;
        // Vehicle: alguien montado en un vehiculo en marcha (bote, vagoneta, strider, caballo...)
        Entity vehicle = e.getVehicle();
        if (vehicle != null && vehicle.getDeltaMovement().horizontalDistance() > 0.2) return DexCard.VEHICLE_RIDE_DEX_MACHINE;
        return null;
    }

    // ------------------------------------------------------------------ comun

    private static boolean canSee(ServerPlayer player, Entity seen) {
        if (!Transformation.isTransformed(player)) return false;
        Vec3 origin = FX.center(seen);
        if (player.distanceToSqr(origin) > RANGE * RANGE) return false;
        if (!player.hasLineOfSight(seen)) return false;
        Vec3 to = origin.subtract(player.getEyePosition()).normalize();
        return to.dot(player.getLookAngle()) >= 0.3;
    }

    private static void sample(ServerLevel level, ServerPlayer player, DexCard card) {
        ItemStack driver = DexDriverItem.findDriver(player);
        if (driver.isEmpty()) return;
        DriverDeck deck = DexDriverItem.getDeck(driver);

        boolean canLevel = (card.family() == DexCard.Family.FORM_RIDE || card.family() == DexCard.Family.ATTACK_RIDE
                || card.family() == DexCard.Family.ARMED_RIDE || card.family() == DexCard.Family.VEHICLE_RIDE)
                && card.samplesToScan() > 0 && deck.level(card) < DriverDeck.MAX_LEVEL;
        if (deck.has(card) && !canLevel) return;

        String key = player.getUUID() + "|" + card.id();
        long now = level.getGameTime();
        Long last = LAST_SAMPLE.get(key);
        if (last != null && now - last < SAMPLE_COOLDOWN_TICKS) return;
        LAST_SAMPLE.put(key, now);

        int samples = deck.samples(card) + 1;
        int needed = deck.has(card) ? deck.samplesForNextLevel(card) : card.samplesToScan();

        if (samples < needed) {
            DexDriverItem.setDeck(driver, deck.withSamples(card, samples));
            DexSounds.play(level, player, DexSounds.SCAN_SAMPLE, 0.8F, 1.0F);
            player.sendSystemMessage(Component.translatable("message.dexrider.scan_vibrate", samples, needed), true);
            return;
        }

        Vec3 front = player.getEyePosition().add(player.getLookAngle().scale(1.2));
        FX.card(level, front, player.getLookAngle(), FX.MAGENTA);
        DexSounds.play(level, player, DexSounds.SCAN_COMPLETE, 1.2F, 1.0F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.4F);

        if (deck.has(card)) {
            // Subida de nivel por sustitucion
            DriverDeck upgraded = deck.levelUp(card);
            DexDriverItem.setDeck(driver, upgraded);
            player.sendSystemMessage(Component.translatable("message.dexrider.level_up",
                    Component.translatable(card.translationKey()), upgraded.level(card)), true);
            if (FormRide.current(player) == FormRide.byCard(card)) Transformation.applyFormEffects(player);
            if (upgraded.level(card) >= DriverDeck.MAX_LEVEL) DexAdvancements.award(player, "max_level");
            return;
        }

        // Carta nueva
        DexDriverItem.setDeck(driver, deck.withCard(card));
        ItemStack newCard = new ItemStack(DexItems.card(card).get());
        if (!player.getInventory().add(newCard)) player.drop(newCard, false);
        DexAdvancements.award(player, "first_scan");
        player.sendSystemMessage(Component.translatable("message.dexrider.scan_complete",
                Component.translatable(card.translationKey())), true);
    }
}
