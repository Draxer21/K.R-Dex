package com.draxer.dexrider.client;

import com.draxer.dexrider.DexRider;
import com.draxer.dexrider.network.FormAbilityPayload;
import com.draxer.dexrider.network.TransformPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import com.draxer.dexrider.registry.DexEntities;
import com.draxer.dexrider.rider.KamenRide;
import com.draxer.dexrider.registry.DexItems;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import top.theillusivec4.curios.api.client.ICurioRenderer;
import java.util.function.BiConsumer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.util.Lazy;
import org.lwjgl.glfw.GLFW;

/** Solo cliente: teclas (V transformacion, R habilidad de forma; se cambian en Controles) y renderers. */
@Mod(value = DexRider.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = DexRider.MODID, value = Dist.CLIENT)
public class DexRiderClient {

    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(DexRider.id("dexrider"));

    public static final Lazy<KeyMapping> TRANSFORM_KEY = Lazy.of(() -> new KeyMapping(
            "key.dexrider.transform",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY));

    /** Habilidad de la Form Ride activa (R por defecto). Shift + R = habilidad basica. */
    public static final Lazy<KeyMapping> ABILITY_KEY = Lazy.of(() -> new KeyMapping(
            "key.dexrider.form_ability",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            CATEGORY));

    /** Pantalla de invocar amigos Rider (B por defecto). */
    public static final Lazy<KeyMapping> SUMMON_KEY = Lazy.of(() -> new KeyMapping(
            "key.dexrider.summon_menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY));

    /** Teclado numerico 1-9: invocar directamente al amigo de ese pad. */
    public static final Lazy<KeyMapping> BOOKER_KEY = Lazy.of(() -> new KeyMapping(
            "key.dexrider.ride_booker", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY));

    // Uso rapido del Ride Booker en combate: Z / X carta anterior / siguiente (con Shift: mazo), Alt izquierdo inserta.
    public static final Lazy<KeyMapping> BOOKER_PREV_KEY = Lazy.of(() -> new KeyMapping(
            "key.dexrider.booker_prev", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY));
    public static final Lazy<KeyMapping> BOOKER_NEXT_KEY = Lazy.of(() -> new KeyMapping(
            "key.dexrider.booker_next", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY));
    public static final Lazy<KeyMapping> BOOKER_USE_KEY = Lazy.of(() -> new KeyMapping(
            "key.dexrider.booker_use", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY));

    public static final java.util.List<Lazy<KeyMapping>> SUMMON_SLOTS = java.util.stream.IntStream.range(0, 9)
            .mapToObj(i -> Lazy.of(() -> new KeyMapping("key.dexrider.summon_" + (i + 1),
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_KP_1 + i, CATEGORY)))
            .toList();

    @SubscribeEvent
    static void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(TRANSFORM_KEY.get());
        event.register(ABILITY_KEY.get());
        event.register(SUMMON_KEY.get());
        event.register(BOOKER_KEY.get());
        event.register(BOOKER_PREV_KEY.get());
        event.register(BOOKER_NEXT_KEY.get());
        event.register(BOOKER_USE_KEY.get());
        for (Lazy<KeyMapping> k : SUMMON_SLOTS) event.register(k.get());
    }

    /** Renderer de la Dex Machine (mismo patron que el ejemplo oficial de GeckoLib). */
    @SubscribeEvent
    @SuppressWarnings({"rawtypes", "unchecked"})
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        BiConsumer<EntityType<? extends Entity>, EntityRendererProvider> renderers = event::registerEntityRenderer;
        for (com.draxer.dexrider.vehicle.BikeKind kind : com.draxer.dexrider.vehicle.BikeKind.values()) {
            renderers.accept(DexEntities.bike(kind).get(), ctx -> new DexMachineRenderer(ctx, kind));
        }
        for (KamenRide rider : DexEntities.NPC_RIDERS) {
            renderers.accept(DexEntities.ECHOES.get(rider).get(), ctx -> new RiderEchoRenderer(ctx, rider));
            renderers.accept(DexEntities.ALLIES.get(rider).get(), ctx -> new RiderEchoRenderer(ctx, rider));
        }
        for (com.draxer.dexrider.entity.KaijinEntity.Kind kind : com.draxer.dexrider.entity.KaijinEntity.Kind.values()) {
            renderers.accept(DexEntities.KAIJIN.get(kind).get(), ctx -> new KaijinRenderer(ctx, kind));
        }
    }

    /** Pantalla del Ride Booker. */
    @SubscribeEvent
    static void registerScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(com.draxer.dexrider.registry.DexMenus.RIDE_BOOKER.get(), RideBookerScreen::new);
    }

    /** Modelo del Driver en el cinturon (Curios). */
    @SubscribeEvent
    static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(DexDriverBeltModel.LAYER, DexDriverBeltModel::createLayer);
    }

    /** Asocia el Dex Driver con su renderer de Curios (igual que el mod de prueba de Curios). */
    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        ICurioRenderer.register(DexItems.DEX_DRIVER.get(), DexDriverCurioRenderer::new);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        while (TRANSFORM_KEY.get().consumeClick()) {
            if (Minecraft.getInstance().player != null) {
                ClientPacketDistributor.sendToServer(TransformPayload.INSTANCE);
            }
        }
        while (BOOKER_KEY.get().consumeClick()) {
            if (Minecraft.getInstance().player != null && Minecraft.getInstance().screen == null) {
                ClientPacketDistributor.sendToServer(com.draxer.dexrider.network.OpenBookerPayload.INSTANCE);
            }
        }
        var mcp = Minecraft.getInstance().player;
        while (BOOKER_PREV_KEY.get().consumeClick()) {
            if (mcp != null) ClientPacketDistributor.sendToServer(new com.draxer.dexrider.network.QuickBookerPayload(
                    mcp.isShiftKeyDown() ? com.draxer.dexrider.menu.RideBookerMenu.QUICK_DECK_PREV : com.draxer.dexrider.menu.RideBookerMenu.QUICK_PREV));
        }
        while (BOOKER_NEXT_KEY.get().consumeClick()) {
            if (mcp != null) ClientPacketDistributor.sendToServer(new com.draxer.dexrider.network.QuickBookerPayload(
                    mcp.isShiftKeyDown() ? com.draxer.dexrider.menu.RideBookerMenu.QUICK_DECK_NEXT : com.draxer.dexrider.menu.RideBookerMenu.QUICK_NEXT));
        }
        while (BOOKER_USE_KEY.get().consumeClick()) {
            if (mcp != null) ClientPacketDistributor.sendToServer(new com.draxer.dexrider.network.QuickBookerPayload(
                    com.draxer.dexrider.menu.RideBookerMenu.QUICK_USE));
        }
        while (SUMMON_KEY.get().consumeClick()) {
            if (Minecraft.getInstance().player != null && Minecraft.getInstance().screen == null) {
                ClientHooks.openSummonScreen();
            }
        }
        for (int i = 0; i < SUMMON_SLOTS.size(); i++) {
            while (SUMMON_SLOTS.get(i).get().consumeClick()) {
                if (Minecraft.getInstance().player != null) {
                    ClientPacketDistributor.sendToServer(new com.draxer.dexrider.network.SummonAllyPayload(i));
                }
            }
        }
        while (ABILITY_KEY.get().consumeClick()) {
            if (Minecraft.getInstance().player != null) {
                ClientPacketDistributor.sendToServer(FormAbilityPayload.INSTANCE);
            }
        }
    }
}
