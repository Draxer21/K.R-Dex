package com.draxer.dexrider.test;

import com.draxer.dexrider.DexRider;
import com.draxer.dexrider.entity.KaijinEntity;
import com.draxer.dexrider.item.DexCard;
import com.draxer.dexrider.item.DexDriverItem;
import com.draxer.dexrider.item.DriverDeck;
import com.draxer.dexrider.item.RiderCardItem;
import com.draxer.dexrider.registry.DexComponents;
import com.draxer.dexrider.registry.DexEntities;
import com.draxer.dexrider.registry.DexItems;
import com.draxer.dexrider.rider.DexEmotions;
import com.draxer.dexrider.rider.FormAbilities;
import com.draxer.dexrider.rider.FormRide;
import com.draxer.dexrider.rider.KTouch;
import com.draxer.dexrider.rider.KamenRide;
import com.draxer.dexrider.rider.RiderPowers;
import com.draxer.dexrider.rider.Transformation;
import com.draxer.dexrider.rider.UpgradeForm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Pruebas dentro de un servidor de Minecraft real (./gradlew runGameTestServer, tambien en GitHub Actions).
 * Cada prueba usa un jugador simulado y escribe "DEXTEST" en el registro con lo que comprobo.
 * Las pruebas estan en data/dexrider/test_instance (estructura dexrider:test_platform).
 */
public final class DexGameTests {
    private DexGameTests() {}

    public static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION, DexRider.MODID);

    static {
        FUNCTIONS.register("transform_cycle", () -> DexGameTests::transformCycle);
        FUNCTIONS.register("all_cards", () -> DexGameTests::allCards);
        FUNCTIONS.register("kamen_rides", () -> DexGameTests::kamenRides);
        FUNCTIONS.register("form_rides", () -> DexGameTests::formRides);
        FUNCTIONS.register("npc_riders", () -> DexGameTests::npcRiders);
        FUNCTIONS.register("daishocker", () -> DexGameTests::daiShocker);
        FUNCTIONS.register("emotions_upgrade", () -> DexGameTests::emotionsUpgrade);
        FUNCTIONS.register("complete_form", () -> DexGameTests::completeForm);
        FUNCTIONS.register("structures", () -> DexGameTests::structures);
        FUNCTIONS.register("tones", () -> DexGameTests::tones);
        FUNCTIONS.register("correa", () -> DexGameTests::correa);
        FUNCTIONS.register("lent_driver", () -> DexGameTests::lentDriver);
        FUNCTIONS.register("guard", () -> DexGameTests::guard);
        FUNCTIONS.register("combos", () -> DexGameTests::combos);
        FUNCTIONS.register("architect", () -> DexGameTests::architect);
        FUNCTIONS.register("combat_read", () -> DexGameTests::combatRead);
        FUNCTIONS.register("starter", () -> DexGameTests::starter);
        FUNCTIONS.register("booker", () -> DexGameTests::booker);
        FUNCTIONS.register("suit_editor", () -> DexGameTests::suitEditor);
        FUNCTIONS.register("archive_hints", () -> DexGameTests::archiveHints);
        FUNCTIONS.register("voices", () -> DexGameTests::voices);
        FUNCTIONS.register("hydro", () -> DexGameTests::hydro);
        FUNCTIONS.register("missions", () -> DexGameTests::missions);
        FUNCTIONS.register("quick_picks", () -> DexGameTests::quickPicks);
        FUNCTIONS.register("booker_morph", () -> DexGameTests::bookerMorph);
        FUNCTIONS.register("ally_learning", () -> DexGameTests::allyLearning);
        FUNCTIONS.register("ally_mining", () -> DexGameTests::allyMining);
        FUNCTIONS.register("ally_zone", () -> DexGameTests::allyZone);
        FUNCTIONS.register("ally_flatten", () -> DexGameTests::allyFlatten);
        FUNCTIONS.register("ally_trust", () -> DexGameTests::allyTrust);
        FUNCTIONS.register("ally_routine", () -> DexGameTests::allyRoutine);
    }

    // ------------------------------------------------------------------ utilidades

    private static void log(String test, String msg) {
        DexRider.LOGGER.info("DEXTEST [{}] {}", test, msg);
    }

    private static void fail(GameTestHelper h, String test, String msg) {
        DexRider.LOGGER.error("DEXTEST [{}] FALLO: {}", test, msg);
        h.fail(Component.literal(test + ": " + msg));
    }

    /** Jugador simulado con el Dex Driver y las cartas pedidas en el mazo. */
    private static ServerPlayer player(GameTestHelper h, DexCard... cards) {
        ServerPlayer p = mockPlayer(h);
        BlockPos at = h.absolutePos(new BlockPos(8, 1, 8));
        p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        ItemStack driver = new ItemStack(DexItems.DEX_DRIVER.get());
        DriverDeck deck = DexDriverItem.getDeck(driver);
        for (DexCard c : cards) deck = deck.withCard(c);
        DexDriverItem.setDeck(driver, deck);
        p.getInventory().add(driver);
        return p;
    }

    /**
     * Jugador simulado armado a mano (como GameTestHelper.makeMockServerPlayerInLevel, pero sin pasar por la lista de
     * jugadores: asi Curios no intenta sincronizar datos con un cliente que no existe).
     */
    private static ServerPlayer mockPlayer(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var server = level.getServer();
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "dex-test"), false);
        ServerPlayer p = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        // los paquetes de mods (Curios, el HUD...) no tienen cliente que los reciba: se descartan
        p.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(server, connection, p, cookie) {
            @Override
            public void send(net.minecraft.network.protocol.Packet<?> packet) {
                if (packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket) return;
                super.send(packet);
            }
        };
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        level.addNewPlayer(p);
        return p;
    }

    private static ItemStack driver(ServerPlayer p) {
        return DexDriverItem.findDriver(p);
    }

    private static void transform(GameTestHelper h, String test, ServerPlayer p) {
        driver(p).set(DexComponents.LOADED_CARD.get(), "kamen_ride_dex");
        Transformation.toggle(p);
        if (!Transformation.isTransformed(p)) fail(h, test, "no se transformo");
    }

    /** Hace avanzar al jugador simulado (los jugadores simulados no los mueve la red). */
    private static void tickPlayer(GameTestHelper h, ServerPlayer p) {
        h.onEachTick(() -> {
            if (!p.isAlive()) return;
            try { p.doTick(); }
            catch (UnsupportedOperationException e) { /* paquetes de otros mods a un cliente que no existe */ }
        });
    }

    // ------------------------------------------------------------------ pruebas

    static void transformCycle(GameTestHelper h) {
        String t = "transform_cycle";
        ServerPlayer p = player(h, DexCard.KAMEN_RIDE_DEX);
        ItemStack helmet = new ItemStack(net.minecraft.world.item.Items.IRON_HELMET);
        p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, helmet);
        transform(h, t, p);
        float maxHp = p.getMaxHealth();
        Transformation.applyFormEffects(p);
        if (p.getMaxHealth() < 29) fail(h, t, "el traje no da +5 corazones (vida maxima " + p.getMaxHealth() + ")");
        Transformation.toggle(p);
        if (Transformation.isTransformed(p)) fail(h, t, "no se destransformo");
        if (!p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(net.minecraft.world.item.Items.IRON_HELMET))
            fail(h, t, "no devolvio el casco original");
        log(t, "ok (vida transformado " + maxHp + ")");
        h.succeed();
    }

    static void allCards(GameTestHelper h) {
        String t = "all_cards";
        ServerPlayer p = player(h, DexCard.values());
        transform(h, t, p);
        tickPlayer(h, p);
        ServerLevel level = h.getLevel();
        List<String> errors = new ArrayList<>();
        int used = 0;
        for (DexCard c : DexCard.values()) {
            if (c.family() == DexCard.Family.KAMEN_RIDE) continue;   // las identidades van en otra prueba
            try {
                ItemStack stack = new ItemStack(DexItems.card(c).get());
                p.setItemInHand(InteractionHand.MAIN_HAND, stack);
                ((RiderCardItem) stack.getItem()).activate(level, p, stack, InteractionHand.MAIN_HAND);
                p.getCooldowns().removeCooldown(p.getCooldowns().getCooldownGroup(stack));
                if (p.getVehicle() != null) p.stopRiding();
                used++;
            } catch (Throwable e) {
                errors.add(c.id() + ": " + e);
                DexRider.LOGGER.error("DEXTEST [{}] {}", t, c.id(), e);
            }
        }
        final int n = used;
        h.runAfterDelay(80, () -> {
            if (!errors.isEmpty()) fail(h, t, errors.size() + " cartas fallaron: " + errors);
            if (!Transformation.isTransformed(p)) fail(h, t, "se perdio la transformacion");
            log(t, "ok, " + n + " cartas activadas sin errores");
            h.succeed();
        });
    }

    static void kamenRides(GameTestHelper h) {
        String t = "kamen_rides";
        ServerPlayer p = player(h, DexCard.values());
        transform(h, t, p);
        tickPlayer(h, p);
        ServerLevel level = h.getLevel();
        List<String> errors = new ArrayList<>();
        KamenRide[] riders = java.util.Arrays.stream(KamenRide.values()).filter(k -> k.card != null && k != KamenRide.DEX).toArray(KamenRide[]::new);
        for (int i = 0; i < riders.length; i++) {
            KamenRide k = riders[i];
            h.runAfterDelay(1 + i * 30L, () -> {
                try {
                    KamenRide.use(level, p, k);
                    if (KamenRide.current(p) != k) errors.add(k.id() + ": no cambio de identidad");
                    RiderPowers.applyEffects(p, k);
                    RiderPowers.ability(level, p, k, false);
                    RiderPowers.ability(level, p, k, true);
                    if (RiderPowers.hasFinisher(k)) RiderPowers.finalAttack(level, p, k);
                    log(t, k.id() + " ok");
                } catch (Throwable e) {
                    errors.add(k.id() + ": " + e);
                    DexRider.LOGGER.error("DEXTEST [{}] {}", t, k.id(), e);
                }
            });
        }
        h.runAfterDelay(riders.length * 30L + 40, () -> {
            if (!errors.isEmpty()) fail(h, t, String.valueOf(errors));
            log(t, "ok, " + riders.length + " identidades");
            h.succeed();
        });
    }

    static void formRides(GameTestHelper h) {
        String t = "form_rides";
        ServerPlayer p = player(h, DexCard.values());
        transform(h, t, p);
        tickPlayer(h, p);
        ServerLevel level = h.getLevel();
        List<String> errors = new ArrayList<>();
        FormRide[] forms = FormRide.values();
        for (int i = 0; i < forms.length; i++) {
            FormRide f = forms[i];
            h.runAfterDelay(1 + i * 25L, () -> {
                try {
                    FormRide.use(level, p, f);
                    if (FormRide.current(p) != f) errors.add(f.id() + ": no se activo");
                    FormAbilities.use(p);
                    log(t, f.id() + " ok");
                } catch (Throwable e) {
                    errors.add(f.id() + ": " + e);
                    DexRider.LOGGER.error("DEXTEST [{}] {}", t, f.id(), e);
                }
            });
        }
        h.runAfterDelay(forms.length * 25L + 30, () -> {
            if (!errors.isEmpty()) fail(h, t, String.valueOf(errors));
            h.succeed();
        });
    }

    static void npcRiders(GameTestHelper h) {
        String t = "npc_riders";
        List<Entity> spawned = new ArrayList<>();
        int i = 0;
        for (KamenRide k : DexEntities.NPC_RIDERS) {
            BlockPos pos = new BlockPos(2 + (i % 5) * 3, 1, 2 + (i / 5) * 6);
            spawned.add(h.spawn(DexEntities.ECHOES.get(k).get(), pos));
            i++;
        }
        h.runAfterDelay(100, () -> {
            for (Entity e : spawned) if (!e.isAlive()) fail(h, t, e.getType() + " desaparecio");
            log(t, "ok, " + spawned.size() + " Riders perdidos vivos tras 100 ticks");
            h.succeed();
        });
    }

    static void daiShocker(GameTestHelper h) {
        String t = "daishocker";
        ServerPlayer p = player(h, DexCard.KAMEN_RIDE_DEX);
        transform(h, t, p);
        List<KaijinEntity> mobs = new ArrayList<>();
        int i = 0;
        for (KaijinEntity.Kind k : KaijinEntity.Kind.values()) {
            if (k == KaijinEntity.Kind.ARCHITECT) continue;   // tiene su propia prueba
            KaijinEntity e = h.spawn(DexEntities.KAIJIN.get(k).get(), new BlockPos(2 + i * 4, 1, 2));
            e.setTarget(p);
            mobs.add(e);
            i++;
        }
        h.runAfterDelay(160, () -> {
            for (KaijinEntity e : mobs) {
                if (!e.isAlive()) fail(h, t, e.kind() + " murio solo");
            }
            log(t, "ok, Kaijin atacando 160 ticks sin errores; vida del jugador " + p.getHealth());
            h.succeed();
        });
    }

    static void emotionsUpgrade(GameTestHelper h) {
        String t = "emotions_upgrade";
        ServerPlayer p = player(h, DexCard.KAMEN_RIDE_DEX);
        transform(h, t, p);
        ServerLevel level = h.getLevel();
        // Violent: un golpe que deja al jugador bajo el 30 %
        p.setHealth(p.getMaxHealth() * 0.35F);
        DexEmotions.onDexHurt(level, p, p.getMaxHealth() * 0.1F);
        if (!DexEmotions.VIOLENT.equals(DexEmotions.current(p))) fail(h, t, "no desperto la Violent Emotion");
        DexEmotions.clear(p);
        // Heroic: un aldeano golpeado por un monstruo cerca
        var villager = h.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(10, 1, 8));
        var zombie = h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, new BlockPos(12, 1, 8));
        DexEmotions.onProtectedHurt(level, villager, level.damageSources().mobAttack(zombie));
        if (!DexEmotions.HEROIC.equals(DexEmotions.current(p))) fail(h, t, "no desperto la Heroic Emotion");
        DexEmotions.clear(p);
        // con las dos, nace la Upgrade Form
        if (!DexDriverItem.getDeck(driver(p)).has(DexCard.UPGRADE_FORM)) fail(h, t, "no se forjo la carta Upgrade Form");
        UpgradeForm.toggle(level, p);
        if (!UpgradeForm.isActive(p)) fail(h, t, "no se activo la Upgrade Form");
        log(t, "ok: Violent, Heroic y Upgrade Form");
        h.succeed();
    }

    static void completeForm(GameTestHelper h) {
        String t = "complete_form";
        ServerPlayer p = player(h, DexCard.values());
        transform(h, t, p);
        tickPlayer(h, p);
        ServerLevel level = h.getLevel();
        p.getInventory().add(new ItemStack(DexItems.K_TOUCH.get()));
        p.getInventory().add(new ItemStack(DexItems.COMPLETE_FORM_CARD.get()));
        UpgradeForm.toggle(level, p);   // entrar desde la Upgrade sube la carta a Nv.2
        KTouch.enter(p);
        if (!KTouch.isLevel2(p)) fail(h, t, "entrando desde la Upgrade no subio a la Complete Form Nv.2");
        if (KamenRide.current(p) != KamenRide.COMPLETE) { fail(h, t, "no entro en la Complete Form"); return; }
        KTouch.toggleCrest(p, KamenRide.NINE);
        KTouch.toggleCrest(p, KamenRide.CIPHER);
        h.runAfterDelay(40, () -> {
            long projections = level.getEntitiesOfClass(com.draxer.dexrider.entity.RiderAllyEntity.class, p.getBoundingBox().inflate(16),
                    a -> a.mode() == com.draxer.dexrider.entity.RiderAllyEntity.Mode.PROJECTION).size();
            if (projections < 2) fail(h, t, "salieron " + projections + " proyecciones (esperaba 2)");
            KTouch.exit(level, p, false);
            log(t, "ok: Complete Form con " + projections + " proyecciones");
        });
        h.runAfterDelay(60, () -> {
            long left = level.getEntitiesOfClass(com.draxer.dexrider.entity.RiderAllyEntity.class, p.getBoundingBox().inflate(16),
                    a -> a.mode() == com.draxer.dexrider.entity.RiderAllyEntity.Mode.PROJECTION).size();
            if (left > 0) fail(h, t, "las proyecciones no se fueron al salir");
            h.succeed();
        });
    }

    static void structures(GameTestHelper h) {
        String t = "structures";
        var manager = h.getLevel().getStructureManager();
        for (String name : new String[]{"mountain_house", "commission_depot", "gunma_lab", "signal_base"}) {
            var tpl = manager.get(DexRider.id(name));
            if (tpl.isEmpty()) { fail(h, t, name + " no carga"); return; }
            log(t, name + " ok, tamano " + tpl.get().getSize());
        }
        h.succeed();
    }

    static void tones(GameTestHelper h) {
        String t = "tones";
        ServerPlayer p = player(h, DexCard.values());
        transform(h, t, p);
        ServerLevel level = h.getLevel();
        var orga = h.spawn(DexEntities.ECHOES.get(KamenRide.ORGA).get(), new BlockPos(11, 1, 8));
        com.draxer.dexrider.rider.Tones.onCard(level, p, DexCard.ATTACK_RIDE_SLASH);
        com.draxer.dexrider.rider.Tones.onCard(level, p, DexCard.ATTACK_RIDE_SLASH);   // la misma carta: Orga la lee
        if (!com.draxer.dexrider.rider.Tones.dodges(orga, p.damageSources().playerAttack(p))) fail(h, t, "Orga no leyo el tono");
        com.draxer.dexrider.rider.Tones.decoy(level, p, DexCard.ATTACK_RIDE_GALE);
        if (com.draxer.dexrider.rider.Tones.exposed(orga) < 1.4F) fail(h, t, "el senuelo no dejo expuesto a Orga");
        log(t, "ok: leer, esquivar y senuelo");
        h.succeed();
    }

    static void correa(GameTestHelper h) {
        String t = "correa";
        ServerPlayer p = player(h, DexCard.values());
        transform(h, t, p);
        ServerLevel level = h.getLevel();
        p.getInventory().add(new ItemStack(DexItems.K_TOUCH.get()));
        p.getInventory().add(new ItemStack(DexItems.COMPLETE_FORM_CARD.get()));
        com.draxer.dexrider.rider.AllyManager.befriend(level, p, KamenRide.CIPHER, h.absolutePos(new BlockPos(10, 1, 8)));
        KTouch.enter(p);
        KTouch.toggleCrest(p, KamenRide.CIPHER);   // Takumi esta aqui de verdad
        KTouch.onHurt(level, p, 1.0F);
        if (KTouch.correa(p) < 0) { fail(h, t, "la correa no desperto con un familiar presente"); return; }
        KTouch.toggleCrest(p, KamenRide.CIPHER);   // "no" a tiempo: soltar su blason
        if (KTouch.correa(p) >= 0) fail(h, t, "soltar el blason no corto la correa");
        log(t, "ok: la correa viaja por la sangre y se corta diciendo no a tiempo");
        h.succeed();
    }

    static void lentDriver(GameTestHelper h) {
        String t = "lent_driver";
        ServerPlayer owner = player(h, DexCard.KAMEN_RIDE_DEX);
        ServerPlayer bearer = mockPlayer(h);
        ServerLevel level = h.getLevel();
        if (!com.draxer.dexrider.rider.LentDriver.lend(level, owner, bearer, driver(owner))) { fail(h, t, "no se pudo prestar"); return; }
        if (!driver(owner).isEmpty() || driver(bearer).isEmpty()) fail(h, t, "el Driver no cambio de manos");
        Transformation.toggle(bearer);
        if (!Transformation.isTransformed(bearer)) fail(h, t, "el portador no se transformo");
        if (!DexDriverItem.getDeck(driver(bearer)).has(DexCard.KAMEN_RIDE_BEARER)) fail(h, t, "no nacio la Kamen Ride del portador");
        com.draxer.dexrider.rider.LentDriver.giveBack(bearer, owner, true);
        if (Transformation.isTransformed(bearer) || driver(owner).isEmpty()) fail(h, t, "revocar no devolvio el Driver");
        log(t, "ok: prestar, transformarse, nacer la carta y revocar");
        h.succeed();
    }

    private static float life(ServerPlayer p) { return p.getHealth() + p.getAbsorptionAmount(); }

    /** Mira hacia +X (donde se ponen los enemigos de las pruebas). */
    private static void faceEast(ServerPlayer p) {
        p.setYRot(-90.0F); p.setYHeadRot(-90.0F); p.setXRot(0.0F);
    }

    static void guard(GameTestHelper h) {
        String t = "guard";
        ServerPlayer p = player(h, DexCard.KAMEN_RIDE_DEX);
        transform(h, t, p);
        faceEast(p);
        tickPlayer(h, p);   // el jugador simulado tiene que "terminar de cargar" para poder recibir golpes
        try { net.minecraft.world.entity.player.Player.class.getMethod("setClientLoaded", boolean.class).invoke(p, true); }
        catch (ReflectiveOperationException ignored) { /* en otras versiones se carga solo al pasar el tiempo */ }
        ServerLevel level = h.getLevel();
        var zombie = h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, new BlockPos(10, 1, 8));
        zombie.setNoAi(true);
        h.runAfterDelay(70, () -> {
            faceEast(p);
            var src = level.damageSources().mobAttack(zombie);
            // golpe sin guardia
            p.setShiftKeyDown(false);
            p.setHealth(p.getMaxHealth()); p.setAbsorptionAmount(0);
            float hp = life(p);
            p.invulnerableTime = 0;
            p.hurtServer(level, src, 6.0F);
            float plain = hp - life(p);
            if (plain <= 0) { fail(h, t, "el golpe sin guardia no hizo dano (jugador invulnerable?)"); return; }
            // parada: Shift pulsado justo ahora
            p.setHealth(p.getMaxHealth()); p.setAbsorptionAmount(0);
            p.setShiftKeyDown(true);
            com.draxer.dexrider.rider.Guard.press(p, level.getGameTime());
            hp = life(p);
            p.invulnerableTime = 0;
            p.hurtServer(level, src, 6.0F);
            if (life(p) < hp) { fail(h, t, "la parada no anulo el golpe"); return; }
            if (!com.draxer.dexrider.rider.Guard.counterReady(p)) { fail(h, t, "no quedo listo el contraataque"); return; }
            if (!zombie.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS)) { fail(h, t, "el atacante no quedo aturdido"); return; }
            // bloqueo: Shift mantenido (pulsado hace 20 ticks)
            h.runAfterDelay(12, () -> {
                faceEast(p);
                com.draxer.dexrider.rider.Guard.press(p, level.getGameTime() - 20);
                p.setHealth(p.getMaxHealth()); p.setAbsorptionAmount(0);
                float before = life(p);
                p.invulnerableTime = 0;
                p.hurtServer(level, src, 6.0F);
                float blocked = before - life(p);
                if (blocked > plain * 0.6F || blocked <= 0) { fail(h, t, "bloqueo raro: sin guardia " + plain + ", con bloqueo " + blocked); return; }
                log(t, "ok: parada anula, contraataque listo, bloqueo " + plain + " -> " + blocked);
                h.succeed();
            });
        });
    }

    static void combos(GameTestHelper h) {
        String t = "combos";
        ServerPlayer p = player(h, DexCard.values());
        transform(h, t, p);
        faceEast(p);
        tickPlayer(h, p);
        ServerLevel level = h.getLevel();
        com.draxer.dexrider.rider.AllyManager.befriend(level, p, KamenRide.KAIXA, h.absolutePos(new BlockPos(7, 1, 10)));
        for (var a : level.getEntitiesOfClass(com.draxer.dexrider.entity.RiderAllyEntity.class, p.getBoundingBox().inflate(16))) a.setCivilian(false);
        var golem = h.spawn(net.minecraft.world.entity.EntityType.IRON_GOLEM, new BlockPos(12, 1, 8));
        golem.setNoAi(true);
        golem.setLastHurtByMob(p);
        float hp0 = golem.getHealth();
        int joined = com.draxer.dexrider.rider.Combos.onFinisher(level, p);
        if (joined < 1) { fail(h, t, "Kaixa no se unio al combo"); return; }
        // dos jugadores
        ServerPlayer p2 = player(h, DexCard.KAMEN_RIDE_DEX);
        transform(h, t, p2);
        faceEast(p2);
        tickPlayer(h, p2);
        h.runAfterDelay(30, () -> {
            if (golem.isAlive() && golem.getHealth() >= hp0) fail(h, t, "el golpe combinado no hizo dano");
            var golem2 = h.spawn(net.minecraft.world.entity.EntityType.IRON_GOLEM, new BlockPos(12, 1, 8));
            golem2.setNoAi(true);
            golem2.setLastHurtByMob(p);
            faceEast(p); faceEast(p2);
            com.draxer.dexrider.rider.Combos.forget(p.getUUID());
            com.draxer.dexrider.rider.Combos.onFinisher(level, p2);
            int pair = com.draxer.dexrider.rider.Combos.onFinisher(level, p);
            if (pair != 1) fail(h, t, "dos jugadores rematando juntos no hicieron combo");
            log(t, "ok: combo con Kaixa (golem " + hp0 + " -> " + (golem.isAlive() ? golem.getHealth() : 0) + ") y combo entre jugadores");
            h.succeed();
        });
    }

    static void architect(GameTestHelper h) {
        String t = "architect";
        ServerPlayer p = player(h, DexCard.values());
        transform(h, t, p);
        faceEast(p);
        tickPlayer(h, p);
        ServerLevel level = h.getLevel();
        BlockPos corePos = h.absolutePos(new BlockPos(14, 1, 8));
        level.setBlockAndUpdate(corePos, com.draxer.dexrider.registry.DexBlocks.SIGNAL_CORE.get().defaultBlockState());
        var boss = com.draxer.dexrider.block.SignalCoreBlock.awaken(level, corePos, p);
        if (boss == null) { fail(h, t, "el Nucleo no desperto al Arquitecto"); return; }
        boss.setNoAi(true);
        // fase 2: se adapta
        boss.setHealth(boss.getMaxHealth() * 0.6F);
        h.runAfterDelay(2, () -> {
            if (boss.phase() < 2) { fail(h, t, "no paso a la fase 2"); return; }
            float a = boss.adaptScale(p), b = boss.adaptScale(p), c = boss.adaptScale(p);
            if (!(a == 1.0F && b < a && c < b)) fail(h, t, "no se adapta: " + a + " " + b + " " + c);
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
            float s = boss.adaptScale(p);
            if (s <= 1.0F) fail(h, t, "cambiar de arma no lo sorprendio: " + s);
            // fase 3 y la Senal
            boss.setHealth(boss.getMaxHealth() * 0.3F);
        });
        h.runAfterDelay(5, () -> {
            if (boss.phase() < 3) { fail(h, t, "no paso a la fase 3"); return; }
            boss.chargeSignal(level);
            if (!boss.isSignalCharging()) fail(h, t, "la Senal no empezo a cargar");
        });
        h.runAfterDelay(52, () -> com.draxer.dexrider.rider.Guard.press(p, level.getGameTime()));   // "no"
        h.runAfterDelay(66, () -> {
            if (!boss.isStunned()) { fail(h, t, "decir que no no corto la Senal"); return; }
            boss.hurtServer(level, p.damageSources().playerAttack(p), 100000.0F);
        });
        h.runAfterDelay(80, () -> {
            if (boss.isAlive()) { fail(h, t, "el Arquitecto no cayo"); return; }
            boolean card = !level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, boss.getBoundingBox().inflate(6),
                    e -> e.getItem().is(DexItems.card(DexCard.ATTACK_RIDE_SEVERANCE).get())).isEmpty();
            if (!card) fail(h, t, "no solto la carta Severance");
            if (!level.getBlockState(corePos).getValue(com.draxer.dexrider.block.SignalCoreBlock.SPENT)) fail(h, t, "el Nucleo no se apago");
            log(t, "ok: Nucleo, fases, adaptacion, la Senal cortada con un no, carta y Nucleo apagado");
            h.succeed();
        });
    }

    static void combatRead(GameTestHelper h) {
        String t = "combat_read";
        ServerPlayer p = player(h, DexCard.KAMEN_RIDE_DEX);
        transform(h, t, p);
        faceEast(p);
        tickPlayer(h, p);
        try { net.minecraft.world.entity.player.Player.class.getMethod("setClientLoaded", boolean.class).invoke(p, true); }
        catch (ReflectiveOperationException ignored) { }
        ServerLevel level = h.getLevel();
        var zombie = h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, new BlockPos(10, 1, 8));
        zombie.setNoAi(true);
        h.runAfterDelay(70, () -> {
            zombie.invulnerableTime = 0;
            zombie.hurtServer(level, p.damageSources().playerAttack(p), 4.0F);
            zombie.invulnerableTime = 0;
            zombie.hurtServer(level, p.damageSources().playerAttack(p), 4.0F);
            var tr = com.draxer.dexrider.rider.CombatRead.track(p);
            if (tr == null || !zombie.getUUID().equals(tr.target)) { fail(h, t, "el Driver no leyo al objetivo"); return; }
            if (tr.lastDealt <= 0 || tr.avgDealt() <= 0) { fail(h, t, "no midio tu golpe"); return; }
            int hits = com.draxer.dexrider.rider.CombatRead.hitsLeft(zombie.getHealth(), tr.avgDealt());
            if (hits <= 0) { fail(h, t, "no estimo los golpes que faltan"); return; }
            p.invulnerableTime = 0;
            p.hurtServer(level, level.damageSources().mobAttack(zombie), 6.0F);
            if (tr.lastTaken() <= 0) { fail(h, t, "no midio el golpe del enemigo"); return; }
            log(t, "ok: tu golpe " + tr.lastDealt + ", faltan " + hits + ", su golpe " + tr.lastTaken());
            h.succeed();
        });
    }

    static void starter(GameTestHelper h) {
        String t = "starter";
        ServerPlayer p = mockPlayer(h);
        if (!com.draxer.dexrider.rider.Starter.onLogin(p)) { fail(h, t, "la primera vez no entrego nada"); return; }
        if (!p.getInventory().contains(new ItemStack(DexItems.KAGAMI_COMPASS.get()))) { fail(h, t, "no recibio la brujula de los Kagami"); return; }
        if (com.draxer.dexrider.rider.Starter.onLogin(p)) { fail(h, t, "la segunda vez volvio a entregar"); return; }
        log(t, "ok: carta de Genzo y brujula la primera vez, nada la segunda");
        h.succeed();
    }

    static void booker(GameTestHelper h) {
        String t = "booker";
        ServerPlayer p = player(h);
        ItemStack driver = DexDriverItem.findDriver(p);
        DexCard[] all = DexCard.values();
        ItemStack a = new ItemStack(DexItems.card(all[0]).get()), b = new ItemStack(DexItems.card(all[1]).get()), c = new ItemStack(DexItems.card(all[2]).get());
        // formato viejo (18 por mazo): ranura 19 = mazo 1, carta 2 -> ahora 27 + 1
        driver.set(com.draxer.dexrider.registry.DexComponents.BOOKER.get(),
                new com.draxer.dexrider.item.BookerContents(java.util.List.of(new com.draxer.dexrider.item.BookerContents.Entry(19, a.copy()))));
        var items = com.draxer.dexrider.menu.Booker.load(driver);
        if (!items.get(28).is(a.getItem())) { fail(h, t, "no remapeo el Booker viejo a 27 por mazo"); return; }
        if (!com.draxer.dexrider.menu.Booker.store(p, b)) { fail(h, t, "no guardo una carta nueva"); return; }
        if (!com.draxer.dexrider.menu.Booker.load(driver).get(0).is(b.getItem())) { fail(h, t, "la carta nueva no fue al primer hueco del mazo"); return; }
        if (com.draxer.dexrider.menu.Booker.store(p, a.copy())) { fail(h, t, "guardo una carta repetida"); return; }
        var cItem = c.getItem();
        com.draxer.dexrider.menu.Booker.give(p, c);
        if (p.getInventory().contains(new ItemStack(cItem))) { fail(h, t, "la carta entregada fue al inventario y no al Booker"); return; }
        if (!com.draxer.dexrider.menu.Booker.load(driver).get(1).is(cItem)) { fail(h, t, "la carta entregada no esta en el Booker"); return; }
        var recipe = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, DexRider.id("card_armed_ride_ride_booker"));
        if (h.getLevel().getServer().getRecipeManager().byKey(recipe).isEmpty()) { fail(h, t, "falta la receta de la carta Armed Ride: Ride Booker"); return; }
        log(t, "ok: Booker viejo remapeado, guardado automatico sin repetidas, receta del arma");
        h.succeed();
    }

    static void suitEditor(GameTestHelper h) {
        String t = "suit_editor";
        ServerPlayer p = player(h);
        ItemStack driver = DexDriverItem.findDriver(p);
        // 1) el traje de antes de 0.33 pasa a la primera ranura
        driver.set(com.draxer.dexrider.registry.DexComponents.SUIT.get(), com.draxer.dexrider.item.SuitConfig.DEFAULT.withMain(0x1E69EB));
        var slots = com.draxer.dexrider.item.SuitSlots.of(driver);
        var base = com.draxer.dexrider.item.SuitLooks.resolve(slots.current(), "base");
        if (base.color("chest") != 0x1E69EB) { fail(h, t, "no migro el traje antiguo"); return; }
        // 2) herencia por estado
        var look = com.draxer.dexrider.item.SuitLook.EMPTY.withColor("accent", 0x00FF00).withPart("scarf", "long").withPart("bogus", "x");
        var prof = com.draxer.dexrider.item.SuitProfile.DEFAULT.withLook("base", look)
                .withLook("violent", com.draxer.dexrider.item.SuitLook.EMPTY.withPart("spikes_head", "off"));
        var violent = com.draxer.dexrider.item.SuitLooks.resolve(prof, "violent");
        if (!violent.on("spikes_body") || violent.on("spikes_head") || !violent.on("crystal")) { fail(h, t, "la Violent no combino sus piezas con las tuyas"); return; }
        if (!"long".equals(violent.part("scarf"))) { fail(h, t, "la Violent no heredo la bufanda de la base"); return; }
        var sky = com.draxer.dexrider.item.SuitLooks.resolve(prof, "form_sky");
        if (sky.color("helmet") != com.draxer.dexrider.rider.FormRide.SKY.suitColor() || sky.color("accent") != 0x00FF00) { fail(h, t, "la Form Ride no tomo su color y tus acentos"); return; }
        var upg = com.draxer.dexrider.item.SuitLooks.resolve(prof, "upgrade");
        if (upg.color("accent") == 0x00FF00) { fail(h, t, "la Upgrade hereda colores que no debe"); return; }
        // 3) codigo para compartir (ida y vuelta; lo invalido se descarta)
        var named = prof.withName("Prueba");
        var back = com.draxer.dexrider.item.SuitProfile.fromCode(named.toCode());
        if (back == null || !back.equals(named.sanitized())) { fail(h, t, "el codigo no devolvio el mismo diseno"); return; }
        if (back.look("base").parts().containsKey("bogus")) { fail(h, t, "el codigo dejo pasar una pieza invalida"); return; }
        if (com.draxer.dexrider.item.SuitProfile.fromCode("DEX1-basura!!") != null) { fail(h, t, "acepto un codigo roto"); return; }
        // 4) guardar y ponerselo
        driver.set(com.draxer.dexrider.registry.DexComponents.SUIT_SLOTS.get(), slots.with(2, named).withActive(2));
        ItemStack chest = new ItemStack(DexItems.DEX_CHESTPLATE.get());
        com.draxer.dexrider.rider.Suits.dressOwn(chest, driver);
        if (!named.sanitized().equals(com.draxer.dexrider.rider.Suits.worn(chest))) { fail(h, t, "el peto no lleva el diseno activo"); return; }
        log(t, "ok: migracion, herencia por estado, codigo " + named.toCode().length() + " letras, diseno puesto");
        h.succeed();
    }

    /** El Archivo del Driver: cada carta tiene nombre, pista y textura en todos los idiomas. */
    static void archiveHints(GameTestHelper h) {
        String t = "archive_hints";
        for (String lang : new String[]{"en_us", "es_es", "es_mx", "es_ar", "es_cl"}) {
            com.google.gson.JsonObject json;
            try (var in = DexRider.class.getResourceAsStream("/assets/dexrider/lang/" + lang + ".json")) {
                if (in == null) { fail(h, t, "falta el idioma " + lang); return; }
                json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (java.io.IOException e) { fail(h, t, "no pude leer " + lang); return; }
            for (DexCard c : DexCard.values()) {
                if (!json.has("hint.dexrider." + c.id())) { fail(h, t, lang + ": falta la pista de " + c.id()); return; }
                if (!json.has(c.translationKey())) { fail(h, t, lang + ": falta el nombre de " + c.id()); return; }
            }
        }
        for (DexCard c : DexCard.values()) {
            if (DexRider.class.getResource("/assets/dexrider/textures/item/" + c.itemName() + ".png") == null) {
                fail(h, t, "falta la textura de " + c.id()); return;
            }
        }
        log(t, "ok: " + DexCard.values().length + " cartas con nombre, pista y textura");
        h.succeed();
    }

    /** Cada sonido registrado tiene su .ogg, y las voces no son el silencio de antes. */
    static void voices(GameTestHelper h) {
        String t = "voices";
        int n = 0;
        for (var holder : com.draxer.dexrider.registry.DexSounds.SOUNDS.getEntries()) {
            String path = holder.getId().getPath();
            try (var in = DexRider.class.getResourceAsStream("/assets/dexrider/sounds/" + path + ".ogg")) {
                if (in == null) { fail(h, t, "falta " + path + ".ogg"); return; }
                byte[] b = in.readAllBytes();
                if (path.startsWith("voice_") && b.length < 6000) { fail(h, t, path + " parece vacio (" + b.length + " bytes)"); return; }
            } catch (java.io.IOException e) { fail(h, t, "no pude leer " + path); return; }
            n++;
        }
        log(t, "ok: " + n + " sonidos, voces grabadas");
        h.succeed();
    }

    /** Form Ride Hydro: en el agua respira, nada como delfin y pega mas fuerte; el remolino arrastra. */
    static void hydro(GameTestHelper h) {
        String t = "hydro";
        ServerPlayer p = player(h, DexCard.KAMEN_RIDE_DEX, DexCard.FORM_RIDE_HYDRO);
        transform(h, t, p);
        ServerLevel level = h.getLevel();
        BlockPos at = p.blockPosition();
        for (BlockPos b : BlockPos.betweenClosed(at.offset(-2, 0, -2), at.offset(2, 1, 2)))
            level.setBlockAndUpdate(b, net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
        FormRide.use(level, p, FormRide.HYDRO);
        if (FormRide.current(p) != FormRide.HYDRO) { fail(h, t, "no se activo Hydro"); return; }
        tickPlayer(h, p);
        h.runAfterDelay(30, () -> {
            com.draxer.dexrider.rider.Transformation.applyFormEffects(p);
            if (!p.hasEffect(net.minecraft.world.effect.MobEffects.WATER_BREATHING)) { fail(h, t, "no respira bajo el agua"); return; }
            if (!p.hasEffect(net.minecraft.world.effect.MobEffects.DOLPHINS_GRACE)) { fail(h, t, "no nada como delfin en el agua"); return; }
            for (int tier = 0; tier < 2; tier++) {
                try { com.draxer.dexrider.rider.FormAbilities.use(p); }
                catch (Throwable e) { fail(h, t, "la habilidad fallo: " + e); return; }
            }
            log(t, "ok: respira, nada rapido y sus habilidades funcionan");
            h.succeed();
        });
    }

    /** Misiones de los Kagami: en orden, una por vez, con recompensa. */
    static void missions(GameTestHelper h) {
        String t = "missions";
        ServerPlayer p = player(h);
        if (com.draxer.dexrider.rider.Missions.doneCount(p) != 0) { fail(h, t, "empezo con misiones cumplidas"); return; }
        com.draxer.dexrider.rider.Missions.tick(p);
        if (com.draxer.dexrider.rider.Missions.doneCount(p) != 0) { fail(h, t, "cumplio una mision sin hacer nada"); return; }
        com.draxer.dexrider.rider.DexAdvancements.award(p, "mountain_house");
        com.draxer.dexrider.rider.DexAdvancements.award(p, "henshin");
        com.draxer.dexrider.rider.Missions.tick(p);
        if (com.draxer.dexrider.rider.Missions.doneCount(p) != 1) { fail(h, t, "no cumplio la primera (llegar a la casa)"); return; }
        if (!p.getInventory().contains(new ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE))) { fail(h, t, "no dio la recompensa"); return; }
        com.draxer.dexrider.rider.Missions.tick(p);
        if (com.draxer.dexrider.rider.Missions.doneCount(p) != 2) { fail(h, t, "no siguio con la segunda (Henshin)"); return; }
        com.draxer.dexrider.rider.Missions.tick(p);
        if (com.draxer.dexrider.rider.Missions.doneCount(p) != 2) { fail(h, t, "se salto una mision"); return; }
        int keys = 0;
        for (var m : com.draxer.dexrider.rider.Missions.ALL)
            if (h.getLevel().getServer().getAdvancements().get(DexRider.id("mission/" + m.id())) == null) { fail(h, t, "falta el logro de " + m.id()); return; }
            else keys++;
        log(t, "ok: " + keys + " misiones, en orden y con recompensa");
        h.succeed();
    }

    /** Selector rapido: marcar y desmarcar cartas del Booker, con tope de 8. */
    static void quickPicks(GameTestHelper h) {
        String t = "quick_picks";
        ServerPlayer p = player(h);
        ItemStack driver = DexDriverItem.findDriver(p);
        net.minecraft.core.NonNullList<ItemStack> book = net.minecraft.core.NonNullList.withSize(com.draxer.dexrider.menu.RideBookerMenu.SIZE, ItemStack.EMPTY);
        DexCard[] all = DexCard.values();
        for (int i = 0; i < 10; i++) book.set(i, new ItemStack(DexItems.card(all[i]).get()));
        com.draxer.dexrider.menu.Booker.save(driver, book);
        int fav = com.draxer.dexrider.menu.RideBookerMenu.QUICK_FAV;
        com.draxer.dexrider.menu.RideBookerMenu.quick(p, fav);
        var picks = driver.getOrDefault(com.draxer.dexrider.registry.DexComponents.QUICK_PICKS.get(), java.util.List.<String>of());
        if (picks.size() != 1) { fail(h, t, "no marco la carta"); return; }
        com.draxer.dexrider.menu.RideBookerMenu.quick(p, fav);
        picks = driver.getOrDefault(com.draxer.dexrider.registry.DexComponents.QUICK_PICKS.get(), java.util.List.<String>of());
        if (!picks.isEmpty()) { fail(h, t, "no desmarco la carta"); return; }
        for (int i = 0; i < 10; i++) com.draxer.dexrider.menu.RideBookerMenu.quick(p, fav + i);
        picks = driver.getOrDefault(com.draxer.dexrider.registry.DexComponents.QUICK_PICKS.get(), java.util.List.<String>of());
        if (picks.size() != com.draxer.dexrider.menu.RideBookerMenu.MAX_PICKS) { fail(h, t, "el tope no es " + com.draxer.dexrider.menu.RideBookerMenu.MAX_PICKS + ": " + picks.size()); return; }
        log(t, "ok: marcar, desmarcar y tope de " + picks.size());
        h.succeed();
    }

    /** Ride Booker: Shift + clic derecho pasa a pistola con la transformacion animada (componente + avance 0..1). */
    static void bookerMorph(GameTestHelper h) {
        String t = "booker_morph";
        ServerPlayer p = player(h);
        transform(h, t, p);
        ItemStack booker = new ItemStack(DexItems.RIDE_BOOKER.get());
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, booker);
        if (com.draxer.dexrider.item.DexWeaponItem.morph(booker, 0) != 0.0F) { fail(h, t, "la espada no empieza en 0"); return; }
        p.setShiftKeyDown(true);
        booker.getItem().use(h.getLevel(), p, net.minecraft.world.InteractionHand.MAIN_HAND);
        p.setShiftKeyDown(false);
        Long start = booker.get(DexComponents.BOOKER_SHIFT.get());
        if (!com.draxer.dexrider.item.DexWeaponItem.isGunMode(booker) || start == null) { fail(h, t, "no paso a pistola"); return; }
        float a = com.draxer.dexrider.item.DexWeaponItem.morph(booker, start);
        float mid = com.draxer.dexrider.item.DexWeaponItem.morph(booker, start + com.draxer.dexrider.item.DexWeaponItem.MORPH_TICKS / 2.0);
        float b = com.draxer.dexrider.item.DexWeaponItem.morph(booker, start + com.draxer.dexrider.item.DexWeaponItem.MORPH_TICKS);
        if (a != 0.0F || Math.abs(mid - 0.5F) > 0.01F || b != 1.0F) { fail(h, t, "avance raro: " + a + " / " + mid + " / " + b); return; }
        if (!p.getCooldowns().isOnCooldown(booker)) { fail(h, t, "sin espera durante la transformacion"); return; }
        log(t, "ok: espada -> pistola en " + com.draxer.dexrider.item.DexWeaponItem.MORPH_TICKS + " ticks");
        h.succeed();
    }

    /** Amigos que aprenden: personalidad, bloques naturales, guardar en el Driver y fabricar desde un cofre. */
    static void allyLearning(GameTestHelper h) {
        String t = "ally_learning";
        com.draxer.dexrider.rider.AllyWork.Job MINE = com.draxer.dexrider.rider.AllyWork.Job.MINE;
        com.draxer.dexrider.rider.AllyWork.Job FARM = com.draxer.dexrider.rider.AllyWork.Job.FARM;
        com.draxer.dexrider.rider.AllyWork.Job CRAFT = com.draxer.dexrider.rider.AllyWork.Job.CRAFT;
        if (com.draxer.dexrider.rider.AllyWork.gain(KamenRide.PROTO, CRAFT) <= com.draxer.dexrider.rider.AllyWork.gain(KamenRide.PROTO, FARM)) { fail(h, t, "Mei no fabrica mejor de lo que cultiva"); return; }
        if (com.draxer.dexrider.rider.AllyWork.gain(KamenRide.SPORE, FARM) <= com.draxer.dexrider.rider.AllyWork.gain(KamenRide.SPORE, MINE)) { fail(h, t, "Kinoko no cultiva mejor de lo que pica"); return; }
        if (!com.draxer.dexrider.rider.AllyWork.isNaturalMineable(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState())
                || com.draxer.dexrider.rider.AllyWork.isNaturalMineable(net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState())) {
            fail(h, t, "clasifica mal la piedra natural / construida"); return;
        }
        ServerPlayer p = player(h);
        var e = com.draxer.dexrider.rider.AllyWork.entry(p, KamenRide.ORGA).withXp("mine", 120).withBlock("minecraft:stone");
        com.draxer.dexrider.rider.AllyWork.save(p, KamenRide.ORGA, e);
        var back = com.draxer.dexrider.rider.AllyWork.entry(p, KamenRide.ORGA);
        if (!back.knows("mine") || !back.blocks().contains("minecraft:stone")) { fail(h, t, "no se guardo en el Driver"); return; }
        // fabricar: 4 tablones en forma 2x2 = mesa de crafteo
        net.minecraft.world.SimpleContainer chest = new net.minecraft.world.SimpleContainer(27);
        chest.setItem(0, new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS, 4));
        var pattern = new com.draxer.dexrider.item.AllySkills.Pattern(2, 2, java.util.List.of(
                "minecraft:oak_planks", "minecraft:oak_planks", "minecraft:oak_planks", "minecraft:oak_planks"));
        if (!com.draxer.dexrider.rider.AllyWork.craftOnce(h.getLevel(), pattern, chest)) { fail(h, t, "no pudo fabricar"); return; }
        if (com.draxer.dexrider.rider.AllyWork.count(chest, net.minecraft.world.item.Items.CRAFTING_TABLE) != 1
                || com.draxer.dexrider.rider.AllyWork.count(chest, net.minecraft.world.item.Items.OAK_PLANKS) != 0) { fail(h, t, "el cofre quedo mal"); return; }
        if (com.draxer.dexrider.rider.AllyWork.craftOnce(h.getLevel(), pattern, chest)) { fail(h, t, "fabrico sin materiales"); return; }
        log(t, "ok: personalidad, clasificacion, Driver y mesa de crafteo desde el cofre");
        h.succeed();
    }

    /** Un amigo que ya sabe picar te ayuda: rompe el granito que le enseniaste y se queda con lo que suelta. */
    static void allyMining(GameTestHelper h) {
        String t = "ally_mining";
        ServerPlayer p = player(h);
        var e = com.draxer.dexrider.rider.AllyWork.entry(p, KamenRide.ORGA).withXp("mine", 300).withBlock("minecraft:granite");
        com.draxer.dexrider.rider.AllyWork.save(p, KamenRide.ORGA, e);
        BlockPos rock = h.absolutePos(new BlockPos(10, 1, 8));
        h.getLevel().setBlockAndUpdate(rock, net.minecraft.world.level.block.Blocks.GRANITE.defaultBlockState());
        var type = com.draxer.dexrider.registry.DexEntities.ally(KamenRide.ORGA);
        var ally = type.get().create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        if (ally == null) { fail(h, t, "no aparecio el amigo"); return; }
        BlockPos at = h.absolutePos(new BlockPos(8, 1, 8));
        ally.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        ally.setup(p.getUUID(), 0, com.draxer.dexrider.entity.RiderAllyEntity.Mode.WANDER, null);
        h.getLevel().addFreshEntity(ally);
        if (!ally.startHelping(com.draxer.dexrider.rider.AllyWork.Job.MINE, rock)) { fail(h, t, "no quiso ayudar"); return; }
        h.runAfterDelay(260, () -> {
            boolean broken = !h.getLevel().getBlockState(rock).is(net.minecraft.world.level.block.Blocks.GRANITE);
            boolean got = !ally.bagEmpty() || p.getInventory().countItem(net.minecraft.world.item.Items.GRANITE) > 0;
            ally.discard();
            if (!broken) { fail(h, t, "no pico el granito"); return; }
            if (!got) { fail(h, t, "pico pero no guardo lo que solto"); return; }
            log(t, "ok: pico el granito y guardo el bloque");
            h.succeed();
        });
    }

    /** Zona y filtro: dentro de la zona pica aunque no sea natural; fuera, nada aunque lo haya aprendido. */
    static void allyZone(GameTestHelper h) {
        String t = "ally_zone";
        ServerPlayer p = player(h);
        var lvl = h.getLevel();
        BlockPos a = h.absolutePos(new BlockPos(10, 1, 7)), b = h.absolutePos(new BlockPos(10, 1, 9)), out = h.absolutePos(new BlockPos(5, 1, 8));
        BlockPos keep = h.absolutePos(new BlockPos(10, 1, 8));
        lvl.setBlockAndUpdate(a, net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState());
        lvl.setBlockAndUpdate(b, net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState());
        lvl.setBlockAndUpdate(keep, net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState());   // fuera del filtro
        lvl.setBlockAndUpdate(out, net.minecraft.world.level.block.Blocks.GRANITE.defaultBlockState());
        if (!com.draxer.dexrider.rider.AllyWork.filterFor(new ItemStack(net.minecraft.world.item.Items.RAW_IRON)).contains("minecraft:deepslate_iron_ore")) {
            fail(h, t, "el hierro crudo no apunta a la mena"); return;
        }
        var e = com.draxer.dexrider.rider.AllyWork.entry(p, KamenRide.ORGA).withXp("mine", 300).withBlock("minecraft:granite").withJob("mine")
                .withFilter(java.util.List.of("minecraft:stone_bricks"))
                .withZone(java.util.List.of(a.asLong(), b.asLong()), lvl.dimension().identifier().toString());
        com.draxer.dexrider.rider.AllyWork.save(p, KamenRide.ORGA, e);
        var ally = com.draxer.dexrider.registry.DexEntities.ally(KamenRide.ORGA).get().create(lvl, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        if (ally == null) { fail(h, t, "no aparecio el amigo"); return; }
        BlockPos at = h.absolutePos(new BlockPos(8, 1, 8));
        ally.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        ally.setup(p.getUUID(), 0, com.draxer.dexrider.entity.RiderAllyEntity.Mode.WANDER, at);
        lvl.addFreshEntity(ally);
        h.runAfterDelay(300, () -> {
            boolean zoneDone = lvl.getBlockState(a).isAir() && lvl.getBlockState(b).isAir();
            boolean keptPlank = lvl.getBlockState(keep).is(net.minecraft.world.level.block.Blocks.OAK_PLANKS);
            boolean keptOut = lvl.getBlockState(out).is(net.minecraft.world.level.block.Blocks.GRANITE);
            ally.discard();
            if (!zoneDone) { fail(h, t, "no excavo la zona"); return; }
            if (!keptPlank) { fail(h, t, "pico algo fuera del filtro"); return; }
            if (!keptOut) { fail(h, t, "pico fuera de la zona"); return; }
            log(t, "ok: zona y filtro respetados");
            h.succeed();
        });
    }

    /** Aplanar: quita lo que sobresale del nivel del suelo y rellena los hoyos con lo del cofre. */
    static void allyFlatten(GameTestHelper h) {
        String t = "ally_flatten";
        ServerPlayer p = player(h);
        var lvl = h.getLevel();
        BlockPos min = h.absolutePos(new BlockPos(10, 1, 7)), max = h.absolutePos(new BlockPos(11, 1, 9));
        BlockPos pillarA = h.absolutePos(new BlockPos(11, 2, 8)), pillarB = h.absolutePos(new BlockPos(11, 3, 8));
        // suelo en y=1 menos un hoyo en (10,1,8); encima, un pilar que sobra
        for (BlockPos q : BlockPos.betweenClosed(min, max)) lvl.setBlockAndUpdate(q, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState());
        BlockPos hole = h.absolutePos(new BlockPos(10, 1, 8));
        lvl.setBlockAndUpdate(hole, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        lvl.setBlockAndUpdate(pillarA, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        lvl.setBlockAndUpdate(pillarB, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        BlockPos chestPos = h.absolutePos(new BlockPos(6, 1, 8));
        lvl.setBlockAndUpdate(chestPos, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        if (lvl.getBlockEntity(chestPos) instanceof net.minecraft.world.Container c) c.setItem(0, new ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 8));
        var e = com.draxer.dexrider.rider.AllyWork.entry(p, KamenRide.ORGA).withXp("mine", 300).withJob("flatten")
                .withZone(java.util.List.of(min.asLong(), max.asLong()), lvl.dimension().identifier().toString());
        com.draxer.dexrider.rider.AllyWork.save(p, KamenRide.ORGA, e);
        var ally = com.draxer.dexrider.registry.DexEntities.ally(KamenRide.ORGA).get().create(lvl, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        if (ally == null) { fail(h, t, "no aparecio el amigo"); return; }
        BlockPos at = h.absolutePos(new BlockPos(8, 1, 8));
        ally.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        ally.setup(p.getUUID(), 0, com.draxer.dexrider.entity.RiderAllyEntity.Mode.WANDER, at);
        lvl.addFreshEntity(ally);
        h.runAfterDelay(380, () -> {
            boolean cleared = lvl.getBlockState(pillarA).isAir() && lvl.getBlockState(pillarB).isAir();
            boolean filled = !lvl.getBlockState(hole).isAir();
            ally.discard();
            if (!cleared) { fail(h, t, "no quito el pilar"); return; }
            if (!filled) { fail(h, t, "no relleno el hoyo"); return; }
            log(t, "ok: quito el pilar y relleno el hoyo");
            h.succeed();
        });
    }

    /** Confianza: charlar cuenta una vez al dia, el regalo favorito vale mas y los temas se abren por nivel. */
    static void allyTrust(GameTestHelper h) {
        String t = "ally_trust";
        ServerPlayer p = player(h);
        var lvl = h.getLevel();
        var ally = com.draxer.dexrider.registry.DexEntities.ally(KamenRide.SPORE).get().create(lvl, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        if (ally == null) { fail(h, t, "no aparecio el amigo"); return; }
        BlockPos at = h.absolutePos(new BlockPos(8, 1, 8));
        ally.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        ally.setup(p.getUUID(), 0, com.draxer.dexrider.entity.RiderAllyEntity.Mode.WANDER, at);
        lvl.addFreshEntity(ally);
        com.draxer.dexrider.rider.AllyTrust.onTalk(lvl, p, ally);
        com.draxer.dexrider.rider.AllyTrust.onTalk(lvl, p, ally);
        int afterTalk = com.draxer.dexrider.rider.AllyWork.entry(p, KamenRide.SPORE).trust();
        // el Driver (donde se guarda la confianza) pasa al inventario para dejar la mano libre
        ItemStack drv = com.draxer.dexrider.item.DexDriverItem.findDriver(p).copy();
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.BROWN_MUSHROOM, 3));
        if (com.draxer.dexrider.item.DexDriverItem.findDriver(p).isEmpty()) p.getInventory().setItem(20, drv);
        String key = com.draxer.dexrider.rider.AllyTrust.onGift(lvl, p, ally);
        int afterGift = com.draxer.dexrider.rider.AllyWork.entry(p, KamenRide.SPORE).trust();
        boolean consumed = p.getMainHandItem().getCount() == 2 || p.isCreative();
        ally.discard();
        if (afterTalk != 2) { fail(h, t, "charlar dio " + afterTalk + " (esperaba 2, una vez al dia)"); return; }
        if (afterGift != 8 || key == null || !key.contains("gift_love")) { fail(h, t, "regalo favorito: " + afterGift + " " + key); return; }
        if (!consumed) { fail(h, t, "no gasto el regalo"); return; }
        int low = com.draxer.dexrider.rider.AllyTrust.topics(10), mid = com.draxer.dexrider.rider.AllyTrust.topics(55), top = com.draxer.dexrider.rider.AllyTrust.topics(90);
        if ((low & 8) != 0 || (mid & 8) == 0 || (mid & 16) != 0 || (top & 16) == 0) { fail(h, t, "temas por nivel mal"); return; }
        log(t, "ok: charla diaria, regalo favorito y temas por confianza");
        h.succeed();
    }

    /** Rutina: de noche el amigo en casa busca la cama libre y duerme; de dia se levanta. Y los momentos existen. */
    static void allyRoutine(GameTestHelper h) {
        String t = "ally_routine";
        ServerPlayer p = player(h);
        var lvl = h.getLevel();
        if (com.draxer.dexrider.entity.AllyRoutine.momentFor(KamenRide.ORGA, KamenRide.PSYGA) == null
                || com.draxer.dexrider.entity.AllyRoutine.momentFor(KamenRide.ORGA, KamenRide.NINE) != null) { fail(h, t, "momentos mal"); return; }
        var bed = net.minecraft.world.level.block.Blocks.RED_BED.defaultBlockState()
                .setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.NORTH);
        h.setBlock(new BlockPos(4, 1, 8), bed.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
        h.setBlock(new BlockPos(4, 1, 7), bed.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
        var ally = com.draxer.dexrider.registry.DexEntities.ally(KamenRide.PSYGA).get().create(lvl, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        if (ally == null) { fail(h, t, "no aparecio el amigo"); return; }
        BlockPos at = h.absolutePos(new BlockPos(8, 1, 8));
        ally.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        ally.setup(p.getUUID(), 0, com.draxer.dexrider.entity.RiderAllyEntity.Mode.WANDER, at);
        lvl.addFreshEntity(ally);
        com.draxer.dexrider.entity.AllyRoutine.testNight = true;
        final int[] phase = {0};
        final long[] t0 = {lvl.getGameTime()};
        h.onEachTick(() -> {
            if (phase[0] == 0 && ally.isSleeping()) {
                phase[0] = 1;
                log(t, "duerme tras " + (lvl.getGameTime() - t0[0]) + " ticks");
                com.draxer.dexrider.entity.AllyRoutine.testNight = false;
            } else if (phase[0] == 1 && !ally.isSleeping()) {
                phase[0] = 2;
                com.draxer.dexrider.entity.AllyRoutine.testNight = null;
                ally.discard();
                log(t, "ok: de noche busca la cama y duerme, de dia se levanta");
                h.succeed();
            } else if (lvl.getGameTime() - t0[0] > 380 && phase[0] < 2) {
                boolean slept = phase[0] == 1;
                phase[0] = 3;
                com.draxer.dexrider.entity.AllyRoutine.testNight = null;
                String where = ally.blockPosition().toShortString();
                ally.discard();
                fail(h, t, !slept ? "no se acosto (esta en " + where + ")" : "no desperto");
            }
        });
    }
}
