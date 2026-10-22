package com.draxer.dexrider;

import com.draxer.dexrider.network.CompleteFormPayload;
import com.draxer.dexrider.network.FormAbilityPayload;
import com.draxer.dexrider.network.SuitPayload;
import com.draxer.dexrider.network.TransformPayload;
import com.draxer.dexrider.registry.DexComponents;
import com.draxer.dexrider.registry.DexEntities;
import com.draxer.dexrider.registry.DexItems;
import com.draxer.dexrider.registry.DexSounds;
import com.draxer.dexrider.rider.DexEvents;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

/**
 * Kamen Rider Dex — clase principal del mod.
 *
 * Flujo de juego:
 *  1. Llevas el Dex Driver en el inventario.
 *  2. Clic derecho con la carta "Kamen Ride: Dex" -> la carta queda cargada en el Driver.
 *  3. Pulsas V -> transformacion (la armadura de Dex sustituye a la tuya y la tuya queda guardada).
 *  4. Transformado: "Attack Ride: Slash" potencia tus golpes; "Final Attack Ride: Dex" lanza la Rider Kick.
 *  5. Pulsas V otra vez -> destransformacion y recuperas tu armadura.
 */
@Mod(DexRider.MODID)
public class DexRider {
    public static final String MODID = "dexrider";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DexRider(IEventBus modEventBus, ModContainer modContainer) {
        DexComponents.COMPONENTS.register(modEventBus);
        DexSounds.SOUNDS.register(modEventBus);
        DexItems.ITEMS.register(modEventBus);
        DexItems.TABS.register(modEventBus);
        DexEntities.ENTITIES.register(modEventBus);
        com.draxer.dexrider.registry.DexMenus.MENUS.register(modEventBus);
        com.draxer.dexrider.test.DexGameTests.FUNCTIONS.register(modEventBus);
        modEventBus.addListener(DexEntities::registerAttributes);
        modEventBus.addListener(DexEntities::registerSpawnPlacements);

        modEventBus.addListener(DexRider::registerPayloads);
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, com.draxer.dexrider.config.DexConfig.SPEC);

        NeoForge.EVENT_BUS.register(DexEvents.class);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(
                TransformPayload.TYPE,
                TransformPayload.STREAM_CODEC,
                TransformPayload::handleOnServer);
        event.registrar("1").playToServer(
                SuitPayload.TYPE,
                SuitPayload.STREAM_CODEC,
                SuitPayload::handleOnServer);
        event.registrar("1").playToServer(
                FormAbilityPayload.TYPE,
                FormAbilityPayload.STREAM_CODEC,
                FormAbilityPayload::handleOnServer);
        event.registrar("1").playToServer(
                CompleteFormPayload.TYPE,
                CompleteFormPayload.STREAM_CODEC,
                CompleteFormPayload::handleOnServer);
        event.registrar("1").playToServer(
                com.draxer.dexrider.network.SummonAllyPayload.TYPE,
                com.draxer.dexrider.network.SummonAllyPayload.STREAM_CODEC,
                com.draxer.dexrider.network.SummonAllyPayload::handleOnServer);
        event.registrar("1").playToServer(
                com.draxer.dexrider.network.QuickBookerPayload.TYPE,
                com.draxer.dexrider.network.QuickBookerPayload.STREAM_CODEC,
                com.draxer.dexrider.network.QuickBookerPayload::handleOnServer);
        event.registrar("1").playToServer(
                com.draxer.dexrider.network.OpenBookerPayload.TYPE,
                com.draxer.dexrider.network.OpenBookerPayload.STREAM_CODEC,
                com.draxer.dexrider.network.OpenBookerPayload::handleOnServer);
        event.registrar("1").playToClient(
                com.draxer.dexrider.network.HudSyncPayload.TYPE,
                com.draxer.dexrider.network.HudSyncPayload.STREAM_CODEC,
                com.draxer.dexrider.network.HudSyncPayload::handleOnClient);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
