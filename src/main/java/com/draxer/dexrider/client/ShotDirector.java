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
        // Complete Form normal y Nv.2 (de frente y de espaldas para ver la capa)
        t = scene(s, t + 2, "complete_nv2", lvl -> {
            stand(lvl, -1.2, null, "complete", false);
            ArmorStand a = stand(lvl, 1.2, null, "complete", false);
            if (a != null) a.getItemBySlot(EquipmentSlot.CHEST).set(DexComponents.COMPLETE_LV2.get(), true);
            ArmorStand b = stand(lvl, 3.4, null, "complete", false);
            if (b != null) { b.getItemBySlot(EquipmentSlot.CHEST).set(DexComponents.COMPLETE_LV2.get(), true); b.setYRot(0); b.yBodyRot = 0; }
        }, 1.0, 1.4, 0.2, 0);
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
        // 8: la Lectura del Driver (primera persona, frente a un golem que ya intercambio golpes contigo)
        s.add(new Step(t + 112, () -> Minecraft.getInstance().options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON)));
        s.add(new Step(t + 114, () -> server(srv -> {
            ServerPlayer p = srv.getPlayerList().getPlayers().get(0);
            ServerLevel lvl = srv.overworld();
            Entity golem = spawnStill(lvl, EntityType.IRON_GOLEM, 0, 6.5);
            cmd(srv, String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f 0 10", bx, by, bz + 2.5));
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
