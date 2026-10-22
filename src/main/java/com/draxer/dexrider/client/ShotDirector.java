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
            for (KaijinEntity.Kind k : KaijinEntity.Kind.values()) spawnStill(lvl, DexEntities.KAIJIN.get(k).get(), (i++ - 1.5) * 2.2, 7);
        }, 0, 2.0, 0.0, 0);
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
        // 6: motos (la Dex Machine con el escarabajo fusionado)
        t = scene(s, t + 2, "motos", lvl -> {
            int i = 0;
            BikeKind[] kinds = BikeKind.values();
            for (BikeKind k : kinds) {
                DexMachineEntity m = (DexMachineEntity) spawnStill(lvl, DexEntities.bike(k).get(), (i % 5 - 2) * 2.6, 7 + (i / 5) * 4);
                if (m != null) m.setYRot(90);
                i++;
            }
        }, 0, 3.5, 0.6, 0);
        t = scene(s, t, "scarab", lvl -> {
            DexMachineEntity m = (DexMachineEntity) spawnStill(lvl, DexEntities.bike(BikeKind.DEX_MACHINE).get(), 0, 4.5);
            if (m != null) { m.setYRot(60); m.yBodyRot = 60; m.yHeadRot = 60; m.showScarab(); }
        }, -1.5, 2.2, 1.2, 0);
        // 7: el HUD y la pantalla del K-Touch no se fotografian aqui (necesitan jugador transformado)
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

    private static void stand(ServerLevel level, double dx, String emotion, String rider, boolean upgrade) {
        ArmorStand a = (ArmorStand) spawnStill(level, EntityType.ARMOR_STAND, dx, 4.5);
        if (a == null) return;
        ItemStack chest = new ItemStack(DexItems.DEX_CHESTPLATE.get());
        if (emotion != null) chest.set(DexComponents.EMOTION.get(), emotion);
        if (rider != null) chest.set(DexComponents.RIDER.get(), rider);
        if (upgrade) chest.set(DexComponents.UPGRADE.get(), true);
        a.setItemSlot(EquipmentSlot.HEAD, new ItemStack(DexItems.DEX_HELMET.get()));
        a.setItemSlot(EquipmentSlot.CHEST, chest);
        a.setItemSlot(EquipmentSlot.LEGS, new ItemStack(DexItems.DEX_LEGGINGS.get()));
        a.setItemSlot(EquipmentSlot.FEET, new ItemStack(DexItems.DEX_BOOTS.get()));
    }
}
