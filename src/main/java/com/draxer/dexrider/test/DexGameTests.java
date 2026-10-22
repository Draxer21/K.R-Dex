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
}
