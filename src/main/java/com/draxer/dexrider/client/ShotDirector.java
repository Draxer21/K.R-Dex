package com.draxer.dexrider.client;

import com.draxer.dexrider.DexRider;
import com.draxer.dexrider.entity.KaijinEntity;
import com.draxer.dexrider.registry.DexComponents;
import com.draxer.dexrider.registry.DexEntities;
import com.draxer.dexrider.registry.DexItems;
import com.draxer.dexrider.rider.KamenRide;
import com.draxer.dexrider.vehicle.BikeKind;
import com.draxer.dexrider.vehicle.DexMachineEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * "Director de fotos" para probar el mod con graficos sin nadie delante (GitHub Actions + pantalla virtual).
 * Solo se activa con -Ddexrider.shots=true (tarea runShots). Entra al mundo, monta escenas con los modelos del mod,
 * saca capturas en run/screenshots y cierra el juego.
 */
@EventBusSubscriber(modid = DexRider.MODID, value = Dist.CLIENT)
public final class ShotDirector {
    private ShotDirector() {}

    private static final boolean ENABLED = Boolean.getBoolean("dexrider.shots");
    private static int tick = -1;
    private static final List<Entity> SCENE = new ArrayList<>();
    private static double bx, by, bz;

    private record Step(int at, Runnable action) {}
    private static List<Step> steps;
    private static int next;

    private static int boot;
    private static boolean triedOpen;

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        if (!ENABLED) return;
        Minecraft mc = Minecraft.getInstance();
        boot++;
        if (boot % 100 == 0 && tick < 0) {
            DexRider.LOGGER.info("SHOTS: tick {} pantalla={} jugador={}", boot,
                    mc.screen == null ? "ninguna" : mc.screen.getClass().getName(), mc.player != null);
            if (boot == 300 || boot == 900) shoot("debug_" + boot);
        }
        // si el juego se quedo en el menu principal, abrimos el mundo a mano
        if (!triedOpen && boot > 400 && mc.player == null && mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
            triedOpen = true;
            DexRider.LOGGER.info("SHOTS: abro el mundo dextest desde el menu");
            mc.createWorldOpenFlows().openWorld("dextest", () -> DexRider.LOGGER.error("SHOTS: no se pudo abrir el mundo"));
        }
        if (boot > 6000) { DexRider.LOGGER.error("SHOTS: me rindo"); mc.stop(); return; }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
        if (tick < 0) {
            tick = 0;
            steps = plan();
            next = 0;
            DexRider.LOGGER.info("SHOTS: en el mundo, empiezo");
        }
        tick++;
        while (next < steps.size() && steps.get(next).at() <= tick) {
            try { steps.get(next).action().run(); }
            catch (Throwable e) { DexRider.LOGGER.error("SHOTS: fallo en el paso {}", next, e); }
            next++;
        }
    }

    // ------------------------------------------------------------------ escenas

    private static List<Step> plan() {
        List<Step> s = new ArrayList<>();
        int t = 20;
        s.add(new Step(t, () -> {
            Minecraft.getInstance().options.hideGui = true;
            server(srv -> {
                cmd(srv, "time set noon"); cmd(srv, "weather clear");
                cmd(srv, "gamemode spectator @a");
                ServerPlayer p = srv.getPlayerList().getPlayers().get(0);
                bx = Math.floor(p.getX()) + 0.5; by = p.getY(); bz = Math.floor(p.getZ()) + 0.5;
            });
        }));
        t += 20;
        // 1-2: los Riders del juego (cinco por foto)
        KamenRide[] riders = DexEntities.NPC_RIDERS;
        for (int part = 0; part < 2; part++) {
            final int from = part * 5;
            final String name = "riders_" + (part + 1);
            t = scene(s, t, name, lvl -> {
                for (int i = from; i < Math.min(from + 5, riders.length); i++) {
                    spawnStill(lvl, DexEntities.ECHOES.get(riders[i]).get(), (i - from - 2) * 1.6, 6);
                }
            }, 0, 1.6, 0.4, 0);
        }
        // 2b: Nagisa de perfil (civil a la izquierda, Psyga a la derecha): para revisar el busto
        s.add(new Step(t, () -> server(srv -> {
            for (Entity e : SCENE) e.discard();
            SCENE.clear();
            cmd(srv, "kill @e[type=!minecraft:player]");
            ServerLevel lvl = srv.overworld();
            for (int i = 0; i < 2; i++) {
                Entity e = spawnStill(lvl, DexEntities.ECHOES.get(KamenRide.PSYGA).get(), i == 0 ? -0.9 : 0.9, 3.4);
                if (e instanceof Mob m) { m.setYRot(90); m.yBodyRot = 90; m.yHeadRot = 90; }
            }
            cmd(srv, String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f 0 8", bx, by + 0.3, bz));
        })));
        s.add(new Step(t + 26, () -> server(srv -> {
            int i = 0;
            for (Entity e : SCENE) if (e instanceof com.draxer.dexrider.entity.RiderMob rm) rm.setCivilian(i++ == 0);
        })));
        s.add(new Step(t + 32, () -> shoot("nagisa_perfil")));
        t += 38;
        // 3: Dai-Shocker
        t = scene(s, t, "daishocker", lvl -> {
            int i = 0;
            for (KaijinEntity.Kind k : KaijinEntity.Kind.values()) spawnStill(lvl, DexEntities.KAIJIN.get(k).get(), (i++ - 2.0) * 2.4, 7.5);
        }, 0, 2.0, 0.0, 0);
        // 3b: el Arquitecto de cerca, con el Nucleo de la Senal encendido y otro apagado
        t = scene(s, t, "arquitecto", lvl -> {
            spawnStill(lvl, DexEntities.KAIJIN.get(KaijinEntity.Kind.ARCHITECT).get(), -0.8, 4.2);
            net.minecraft.core.BlockPos c = net.minecraft.core.BlockPos.containing(bx + 1.6, by, bz + 3.6);
            lvl.setBlockAndUpdate(c, com.draxer.dexrider.registry.DexBlocks.SIGNAL_CORE.get().defaultBlockState());
            lvl.setBlockAndUpdate(c.east(), com.draxer.dexrider.registry.DexBlocks.SIGNAL_CORE.get().defaultBlockState()
                    .setValue(com.draxer.dexrider.block.SignalCoreBlock.SPENT, true));
        }, 0, 1.7, 0.15, 0);
        // 4: Dex y sus formas en soportes de armadura (normal, Violent, Heroic, Upgrade, Complete)
        t = scene(s, t, "dex_formas", lvl -> {
            stand(lvl, -3.2, null, null, false);
            stand(lvl, -1.6, "violent", null, false);
            stand(lvl, 0.0, "heroic", null, false);
            stand(lvl, 1.6, null, null, true);
            stand(lvl, 3.2, null, "complete", false);
        }, 0, 1.4, 0.2, 0);
        // 5: lo mismo de noche (brillos)
        s.add(new Step(t, () -> server(srv -> cmd(srv, "time set midnight"))));
        t = scene(s, t + 2, "dex_formas_noche", lvl -> {
            stand(lvl, -3.2, null, null, false);
            stand(lvl, -1.6, "violent", null, false);
            stand(lvl, 0.0, "heroic", null, false);
            stand(lvl, 1.6, null, null, true);
            stand(lvl, 3.2, null, "complete", false);
        }, 0, 1.4, 0.2, 0);
        s.add(new Step(t, () -> server(srv -> cmd(srv, "time set noon"))));
        // 5b: el editor del traje (0.33): disenos de ejemplo en cada estado
        t = scene(s, t + 2, "trajes_editor", lvl -> {
            for (int i = 0; i < 5; i++) {
                ArmorStand a = stand(lvl, -3.2 + i * 1.6, i == 1 ? "violent" : i == 2 ? "heroic" : null, null, i == 3);
                if (a == null) continue;
                ItemStack chest = a.getItemBySlot(EquipmentSlot.CHEST);
                chest.set(DexComponents.SUIT_PROFILE.get(), sampleDesign());
                if (i == 4) chest.set(DexComponents.FORM.get(), "half");
            }
        }, 0, 1.4, 0.2, 0);
        // 5c: el editor abierto
        s.add(new Step(t, () -> {
            Minecraft mc = Minecraft.getInstance();
            mc.options.hideGui = false;
            com.draxer.dexrider.item.SuitSlots slots = com.draxer.dexrider.item.SuitSlots.DEFAULT.with(0, sampleDesign());
            mc.setScreen(new SuitScreen(slots));
        }));
        s.add(new Step(t + 20, () -> shoot("editor_traje")));
        s.add(new Step(t + 31, () -> shoot("editor_traje_b")));     // medio segundo despues: el maniqui tiene que haber girado
        s.add(new Step(t + 34, () -> { Minecraft.getInstance().setScreen(null); Minecraft.getInstance().options.hideGui = true; }));
        t += 40;
        // 5d: el Archivo del Driver (con un mazo a medias) y el aviso de escaneo
        s.add(new Step(t, () -> server(srv -> {
            ServerPlayer p = srv.getPlayerList().getPlayers().get(0);
            ItemStack driver = new ItemStack(DexItems.DEX_DRIVER.get());
            com.draxer.dexrider.item.DriverDeck deck = com.draxer.dexrider.item.DexDriverItem.getDeck(driver);
            for (com.draxer.dexrider.item.DexCard c : new com.draxer.dexrider.item.DexCard[]{
                    com.draxer.dexrider.item.DexCard.KAMEN_RIDE_DEX, com.draxer.dexrider.item.DexCard.ATTACK_RIDE_SLASH,
                    com.draxer.dexrider.item.DexCard.FINAL_ATTACK_RIDE_DEX, com.draxer.dexrider.item.DexCard.FORM_RIDE_SPEED,
                    com.draxer.dexrider.item.DexCard.FORM_RIDE_FROST}) deck = deck.withCard(c);
            deck = deck.levelUp(com.draxer.dexrider.item.DexCard.FORM_RIDE_SPEED)
                    .withSamples(com.draxer.dexrider.item.DexCard.FORM_RIDE_SPEED, 3)
                    .withSamples(com.draxer.dexrider.item.DexCard.FORM_RIDE_SKY, 1);
            com.draxer.dexrider.item.DexDriverItem.setDeck(driver, deck);
            p.getInventory().add(driver);
        })));
        s.add(new Step(t + 10, () -> {
            Minecraft.getInstance().options.hideGui = false;
            Minecraft.getInstance().setScreen(new DriverArchiveScreen(com.draxer.dexrider.item.DexCard.FORM_RIDE_SKY));
        }));
        s.add(new Step(t + 30, () -> shoot("archivo")));
        s.add(new Step(t + 34, () -> {
            Minecraft.getInstance().setScreen(null);
            server(srv -> {
                ServerPlayer p = srv.getPlayerList().getPlayers().get(0);
                ItemStack driver = com.draxer.dexrider.item.DexDriverItem.findDriver(p);
                com.draxer.dexrider.item.DexDriverItem.setDeck(driver, com.draxer.dexrider.item.DexDriverItem.getDeck(driver)
                        .withSamples(com.draxer.dexrider.item.DexCard.FORM_RIDE_TERRA, 1));
            });
        }));
        s.add(new Step(t + 48, () -> shoot("aviso_escaneo")));
        s.add(new Step(t + 52, () -> {
            Minecraft.getInstance().options.hideGui = true;
            server(srv -> com.draxer.dexrider.item.DexDriverItem.findDriver(srv.getPlayerList().getPlayers().get(0)).setCount(0));
        }));
        t += 58;
        // 5e: el registro de misiones de los Kagami (con cinco cumplidas)
        s.add(new Step(t, () -> {
            com.draxer.dexrider.network.MissionSyncPayload.clientDone = 5;
            Minecraft.getInstance().options.hideGui = false;
            Minecraft.getInstance().setScreen(new MissionsScreen());
        }));
        s.add(new Step(t + 20, () -> shoot("misiones")));
        s.add(new Step(t + 24, () -> { Minecraft.getInstance().setScreen(null); Minecraft.getInstance().options.hideGui = true; }));
        t += 28;
        // 5e2: el Ride Booker abierto como libro, con cartas en los bolsillos
        s.add(new Step(t, () -> server(srv -> {
            ServerPlayer p = srv.getPlayerList().getPlayers().get(0);
            ItemStack driver = new ItemStack(DexItems.DEX_DRIVER.get());
            net.minecraft.core.NonNullList<ItemStack> book = net.minecraft.core.NonNullList.withSize(com.draxer.dexrider.menu.RideBookerMenu.SIZE, ItemStack.EMPTY);
            com.draxer.dexrider.item.DexCard[] all = com.draxer.dexrider.item.DexCard.values();
            for (int i = 0; i < 15; i++) book.set(i < 9 ? i : i + 0, new ItemStack(DexItems.card(all[i * 3 % all.length]).get()));
            com.draxer.dexrider.menu.Booker.save(driver, book);
            driver.set(DexComponents.BOOKER_SEL.get(), 4);
            java.util.List<String> picks = new java.util.ArrayList<>();
            for (int i = 0; i < 6; i++) picks.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(book.get(i).getItem()).toString());
            driver.set(DexComponents.QUICK_PICKS.get(), picks);
            p.getInventory().add(driver);
        })));
        s.add(new Step(t + 10, () -> {
            Minecraft.getInstance().options.hideGui = false;
            Minecraft.getInstance().setScreen(new BookerBookScreen());
        }));
        s.add(new Step(t + 30, () -> shoot("booker_libro")));
        s.add(new Step(t + 34, () -> Minecraft.getInstance().setScreen(new QuickSelectScreen(DexRiderClient.QUICK_SELECT_KEY.get(), true))));
        s.add(new Step(t + 50, () -> shoot("selector_rapido")));
        s.add(new Step(t + 54, () -> {
            Minecraft.getInstance().setScreen(null);
            Minecraft.getInstance().options.hideGui = true;
            server(srv -> com.draxer.dexrider.item.DexDriverItem.findDriver(srv.getPlayerList().getPlayers().get(0)).setCount(0));
        }));
        t += 60;
        // 5g: la interfaz del visor (transformado, con poca vida y escaneando)
        s.add(new Step(t, () -> server(srv -> {
            ServerPlayer p = srv.getPlayerList().getPlayers().get(0);
            ItemStack driver = new ItemStack(DexItems.DEX_DRIVER.get());
            p.getInventory().add(driver);
            ItemStack chest = new ItemStack(DexItems.DEX_CHESTPLATE.get());
            chest.set(DexComponents.FORM.get(), "hydro");
            chest.set(DexComponents.FORM_LEVEL.get(), 2);
            p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(DexItems.DEX_HELMET.get()));
            p.setItemSlot(EquipmentSlot.CHEST, chest);
            p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(DexItems.DEX_LEGGINGS.get()));
            p.setItemSlot(EquipmentSlot.FEET, new ItemStack(DexItems.DEX_BOOTS.get()));
            p.setHealth(4.0F);
        })));
        s.add(new Step(t + 4, () -> Minecraft.getInstance().options.hideGui = false));
        s.add(new Step(t + 22, () -> shoot("visor_arranque")));
        s.add(new Step(t + 66, () -> server(srv -> {
            ServerPlayer p = srv.getPlayerList().getPlayers().get(0);
            ItemStack driver = com.draxer.dexrider.item.DexDriverItem.findDriver(p);
            com.draxer.dexrider.item.DexDriverItem.setDeck(driver, com.draxer.dexrider.item.DexDriverItem.getDeck(driver)
                    .withSamples(com.draxer.dexrider.item.DexCard.FORM_RIDE_SKY, 1));
        })));
        s.add(new Step(t + 80, () -> shoot("visor")));
        s.add(new Step(t + 82, () -> Minecraft.getInstance().setScreen(new VisorLayoutScreen(null))));
        s.add(new Step(t + 96, () -> shoot("visor_posiciones")));
        s.add(new Step(t + 99, () -> {
            Minecraft.getInstance().setScreen(null);
            Minecraft.getInstance().options.hideGui = true;
            server(srv -> {
                ServerPlayer p = srv.getPlayerList().getPlayers().get(0);
                for (EquipmentSlot sl : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
                    p.setItemSlot(sl, ItemStack.EMPTY);
                com.draxer.dexrider.item.DexDriverItem.findDriver(p).setCount(0);
                p.setHealth(p.getMaxHealth());
            });
        }));
        t += 105;
        // 5f: Form Ride Hydro junto a Frost y Speed
        t = scene(s, t, "hydro", lvl -> {
            String[] forms = {"speed", "hydro", "frost"};
            for (int i = 0; i < 3; i++) {
                ArmorStand a = stand(lvl, -1.6 + i * 1.6, null, null, false);
                if (a != null) a.getItemBySlot(EquipmentSlot.CHEST).set(DexComponents.FORM.get(), forms[i]);
            }
        }, 0, 1.4, 0.2, 0);
        // Complete Form normal y Nv.2 (de frente y de espaldas para ver la capa)
        t = scene(s, t + 2, "complete_nv2", lvl -> {
            stand(lvl, -1.2, null, "complete", false);
            ArmorStand a = stand(lvl, 1.2, null, "complete", false);
            if (a != null) a.getItemBySlot(EquipmentSlot.CHEST).set(DexComponents.COMPLETE_LV2.get(), true);
            ArmorStand b = stand(lvl, 3.4, null, "complete", false);
            if (b != null) { b.getItemBySlot(EquipmentSlot.CHEST).set(DexComponents.COMPLETE_LV2.get(), true); b.setYRot(0); b.yBodyRot = 0; }
        }, 1.0, 1.4, 0.2, 0);
        // 5b: alas de Sky (plegadas en el suelo; abiertas en el aire, de espaldas y de lado)
        t = scene(s, t + 2, "sky_alas", lvl -> {
            float[][] cfg = {{-3.6F, 180F, 0F}, {-1.2F, 0F, 0F}, {1.2F, 0F, 1.2F}, {3.6F, 90F, 1.2F}};
            for (float[] c : cfg) {
                ArmorStand a = stand(lvl, c[0], null, null, false);
                if (a == null) continue;
                a.getItemBySlot(EquipmentSlot.CHEST).set(DexComponents.FORM.get(), "sky");
                a.getItemBySlot(EquipmentSlot.CHEST).set(DexComponents.FORM_LEVEL.get(), 3);
                a.setYRot(c[1]); a.yBodyRot = c[1]; a.yHeadRot = c[1];
                if (c[2] > 0) { a.setNoGravity(true); a.setPos(a.getX(), a.getY() + c[2], a.getZ()); }
            }
        }, 0, 1.8, 0.3, 0);
        // 6: motos (la Dex Machine con el escarabajo fusionado)
        t = scene(s, t + 2, "motos", lvl -> {
            int i = 0;
            BikeKind[] kinds = BikeKind.values();
            for (BikeKind k : kinds) {
                DexMachineEntity m = (DexMachineEntity) spawnStill(lvl, DexEntities.bike(k).get(), (i % 6 - 2.5) * 2.6, 7 + (i / 6) * 4);
                if (m != null) m.setYRot(90);
                i++;
            }
        }, 0, 3.5, 0.6, 0);
        t = scene(s, t, "scarab", lvl -> {
            DexMachineEntity m = (DexMachineEntity) spawnStill(lvl, DexEntities.bike(BikeKind.DEX_MACHINE).get(), 0, 4.5);
            if (m != null) { m.setYRot(60); m.yBodyRot = 60; m.yHeadRot = 60; m.showScarab(); }
        }, -1.5, 2.2, 1.2, 0);
        // 7: el propio jugador transformado, en tercera persona de frente, con las poses (si hay Player Animation Library)
        s.add(new Step(t, () -> server(srv -> {
            for (Entity e : SCENE) e.discard();
            SCENE.clear();
            cmd(srv, "kill @e[type=!minecraft:player]");
            cmd(srv, "gamemode creative @a");
            cmd(srv, String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f 180 0", bx, by, bz + 3));
            ServerPlayer p = srv.getPlayerList().getPlayers().get(0);
            ItemStack driver = new ItemStack(DexItems.DEX_DRIVER.get());
            driver.set(DexComponents.LOADED_CARD.get(), "kamen_ride_dex");
            p.getInventory().add(driver);
            com.draxer.dexrider.rider.Transformation.toggle(p);
        })));
        s.add(new Step(t + 4, () -> Minecraft.getInstance().options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT)));
        s.add(new Step(t + 30, () -> server(srv -> com.draxer.dexrider.network.PosePayload.send(srv.getPlayerList().getPlayers().get(0), "henshin"))));
        s.add(new Step(t + 36, () -> shoot("pose_henshin_a")));
        s.add(new Step(t + 50, () -> shoot("pose_henshin_b")));
        s.add(new Step(t + 70, () -> server(srv -> com.draxer.dexrider.network.PosePayload.send(srv.getPlayerList().getPlayers().get(0), "kick"))));
        s.add(new Step(t + 78, () -> shoot("pose_kick")));
        s.add(new Step(t + 95, () -> server(srv -> com.draxer.dexrider.network.PosePayload.send(srv.getPlayerList().getPlayers().get(0), "finisher"))));
        s.add(new Step(t + 108, () -> shoot("pose_finisher")));
        // 7b: el Ride Booker 3D en la mano: espada, transformacion y pistola (tercera persona de frente y primera persona)
        s.add(new Step(t + 112, () -> server(srv -> {
            ServerPlayer p = srv.getPlayerList().getPlayers().get(0);
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(DexItems.RIDE_BOOKER.get()));
        })));
        s.add(new Step(t + 130, () -> shoot("booker_espada_3p")));
        s.add(new Step(t + 132, () -> server(srv -> bookerMode(srv, true))));
        s.add(new Step(t + 136, () -> shoot("booker_transicion_3p")));
        s.add(new Step(t + 150, () -> shoot("booker_pistola_3p")));
        s.add(new Step(t + 152, () -> {
            Minecraft.getInstance().options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            Minecraft.getInstance().options.hideGui = false;   // con la interfaz oculta tampoco se ve la mano
        }));
        s.add(new Step(t + 162, () -> shoot("booker_pistola_1p")));
        s.add(new Step(t + 164, () -> server(srv -> bookerMode(srv, false))));
        s.add(new Step(t + 168, () -> shoot("booker_transicion_1p")));
        s.add(new Step(t + 182, () -> shoot("booker_espada_1p")));
        s.add(new Step(t + 184, () -> server(srv -> srv.getPlayerList().getPlayers().get(0)
                .setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY))));
        t += 76;
        // 8: la Lectura del Driver (primera persona, frente a un golem que ya intercambio golpes contigo)
        s.add(new Step(t + 112, () -> {
            Minecraft.getInstance().options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            Minecraft.getInstance().options.hideGui = false;   // esta foto es del HUD
        }));
        s.add(new Step(t + 114, () -> server(srv -> {
            ServerPlayer p = srv.getPlayerList().getPlayers().get(0);
            ServerLevel lvl = srv.overworld();
            Entity golem = spawnStill(lvl, EntityType.IRON_GOLEM, 0, 6.5);
            cmd(srv, String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f 0 10", bx, by, bz + 2.5));
            cmd(srv, String.format(java.util.Locale.ROOT, "fill %d %d %d %d %d %d air", (int) Math.floor(bx) - 4, (int) by, (int) Math.floor(bz), (int) Math.floor(bx) + 4, (int) by + 1, (int) Math.floor(bz) + 8));
            if (golem instanceof net.minecraft.world.entity.LivingEntity le) {
                le.setInvulnerable(false);
                for (int i = 0; i < 3; i++) { le.invulnerableTime = 0; le.hurtServer(lvl, p.damageSources().playerAttack(p), 9.0F); }
                com.draxer.dexrider.rider.CombatRead.recordTaken(p, le, 7.0F, lvl.getGameTime());
            }
        })));
        s.add(new Step(t + 132, () -> shoot("hud_lectura")));
        t += 140;
        s.add(new Step(t + 10, () -> {
            DexRider.LOGGER.info("SHOTS: terminado");
            Minecraft.getInstance().stop();
        }));
        return s;
    }

    /** Cambia el Ride Booker de la mano a pistola o espada, como Shift + clic derecho (con la animacion). */
    private static void bookerMode(MinecraftServer srv, boolean gun) {
        ItemStack stack = srv.getPlayerList().getPlayers().get(0).getMainHandItem();
        if (gun) stack.set(DexComponents.GUN_MODE.get(), true);
        else stack.remove(DexComponents.GUN_MODE.get());
        stack.set(DexComponents.BOOKER_SHIFT.get(), srv.overworld().getGameTime());
    }

    /** Monta una escena, espera a que cargue, coloca la camara y saca la foto. Devuelve el tick siguiente. */
    private static int scene(List<Step> s, int t, String name, Consumer<ServerLevel> build, double camX, double camY, double pitchDown, double unused) {
        s.add(new Step(t, () -> server(srv -> {
            for (Entity e : SCENE) e.discard();
            SCENE.clear();
            cmd(srv, "kill @e[type=!minecraft:player]");   // companeros que traen los Riders perdidos, etc.
            build.accept(srv.overworld());
            cmd(srv, String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f 0 %.1f", bx + camX, by + camY, bz, pitchDown * 20));
        })));
        // los Kagami se quitan el traje cuando no pelean: para la foto, con traje
        s.add(new Step(t + 26, () -> server(srv -> {
            for (Entity e : SCENE) if (e instanceof com.draxer.dexrider.entity.RiderMob rm) rm.setCivilian(false);
        })));
        s.add(new Step(t + 30, () -> shoot(name)));
        return t + 36;
    }

    private static void shoot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "dex_" + name + ".png", mc.getMainRenderTarget(), 1,
                c -> DexRider.LOGGER.info("SHOTS: {} -> {}", name, c.getString()));
    }

    private static void server(Consumer<MinecraftServer> action) {
        MinecraftServer srv = Minecraft.getInstance().getSingleplayerServer();
        if (srv != null) srv.execute(() -> action.accept(srv));
    }

    private static void cmd(MinecraftServer srv, String command) {
        srv.getCommands().performPrefixedCommand(srv.createCommandSourceStack().withSuppressedOutput(), command);
    }

    private static Entity spawnStill(ServerLevel level, EntityType<?> type, double dx, double dz) {
        Entity e = type.create(level, EntitySpawnReason.COMMAND);
        if (e == null) return null;
        e.setPos(bx + dx, by, bz + dz);
        e.setYRot(180);
        if (e instanceof Mob m) { m.setNoAi(true); m.yBodyRot = 180; m.yHeadRot = 180; m.setPersistenceRequired(); }
        e.setInvulnerable(true);
        level.addFreshEntity(e);
        SCENE.add(e);
        return e;
    }

    /** Diseno de ejemplo: azul metalico con bufanda larga y cresta, y cambios en cada estado. */
    private static com.draxer.dexrider.item.SuitProfile sampleDesign() {
        com.draxer.dexrider.item.SuitLook base = com.draxer.dexrider.item.SuitLook.EMPTY;
        for (String z : com.draxer.dexrider.item.SuitLooks.MAIN_ZONES) base = base.withColor(z, 0x1E69EB);
        base = base.withColor("accent", 0xB8BCC8).withColor("eyes", 0xF5E642).withColor("scarf", 0xCD1C28)
                .withPart("scarf", "long").withPart("crest", "on").withPart("pauldrons", "flat").withPart("finish", "metal");
        return com.draxer.dexrider.item.SuitProfile.DEFAULT.withName("Azul")
                .withLook("base", base)
                .withLook("violent", com.draxer.dexrider.item.SuitLook.EMPTY.withColor("chest", 0x1C1C24).withColor("eyes", 0xFF3020).withPart("spikes_body", "off"))
                .withLook("heroic", com.draxer.dexrider.item.SuitLook.EMPTY.withColor("crystal", 0x1EA0A0).withPart("scarf", "short"))
                .withLook("upgrade", com.draxer.dexrider.item.SuitLook.EMPTY.withColor("helmet", 0x28D250).withColor("arms", 0x28D250)
                        .withColor("legs", 0x28D250).withColor("boots", 0x28D250).withPart("finish", "matte"));
    }

    private static ArmorStand stand(ServerLevel level, double dx, String emotion, String rider, boolean upgrade) {
        ArmorStand a = (ArmorStand) spawnStill(level, EntityType.ARMOR_STAND, dx, 4.5);
        if (a == null) return null;
        ItemStack chest = new ItemStack(DexItems.DEX_CHESTPLATE.get());
        if (emotion != null) chest.set(DexComponents.EMOTION.get(), emotion);
        if (rider != null) chest.set(DexComponents.RIDER.get(), rider);
        if (upgrade) chest.set(DexComponents.UPGRADE.get(), true);
        a.setItemSlot(EquipmentSlot.HEAD, new ItemStack(DexItems.DEX_HELMET.get()));
        a.setItemSlot(EquipmentSlot.CHEST, chest);
        a.setItemSlot(EquipmentSlot.LEGS, new ItemStack(DexItems.DEX_LEGGINGS.get()));
        a.setItemSlot(EquipmentSlot.FEET, new ItemStack(DexItems.DEX_BOOTS.get()));
        return a;
    }
}
