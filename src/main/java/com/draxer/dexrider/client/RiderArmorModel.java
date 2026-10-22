package com.draxer.dexrider.client;

import com.draxer.dexrider.DexRider;
import com.draxer.dexrider.item.DexArmorItem;
import com.draxer.dexrider.rider.KamenRide;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.resources.Identifier;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Modelo de la armadura que cambia segun la identidad de Rider activa (Dex, Nine, Cipher, Complete Form...).
 * La identidad llega en el estado de render (DexArmorRenderer.RIDER), que se rellena antes de dibujar.
 *   geckolib/models/item/armor/<modelo>.geo.json  +  textures/item/armor/<modelo>.png
 */
public class RiderArmorModel extends GeoModel<DexArmorItem> {

    private static final Map<KamenRide, Identifier> MODELS = new EnumMap<>(KamenRide.class);
    private static final Map<KamenRide, Identifier> TEXTURES = new EnumMap<>(KamenRide.class);
    private static final Identifier ANIMATIONS = DexRider.id("item/armor/dex_armor");

    static {
        for (KamenRide k : KamenRide.values()) {
            MODELS.put(k, DexRider.id("item/armor/" + k.model));
            TEXTURES.put(k, DexRider.id("textures/item/armor/" + k.model + ".png"));
        }
    }

    public static KamenRide rider(GeoRenderState state) {
        KamenRide k = KamenRide.byId(state.getGeckolibData(DexArmorRenderer.RIDER));
        return k.implemented ? k : KamenRide.DEX;
    }

    /** Nombre del modelo a dibujar (las formas de Forge y de Nine son solo de Momo e Izuku: el jugador usa la base;
     *  Cipher si cambia con el reloj Accel o el arma Blaster). */
    public static String modelName(GeoRenderState state) {
        KamenRide k = rider(state);
        if (k == KamenRide.DEX && Boolean.TRUE.equals(state.getGeckolibData(DexArmorRenderer.UPGRADE))) return "dex_upgrade";
        if (k == KamenRide.CIPHER) {
            String mode = state.getGeckolibData(DexArmorRenderer.CIPHER_MODE);
            if (mode != null && !mode.isEmpty()) return k.model + "_" + mode;
        }
        return k.model;
    }

    private static final Map<String, Identifier[]> BY_NAME = new HashMap<>();

    /** {modelo, textura, brillo} para un nombre de modelo. */
    public static Identifier[] resources(String name) {
        return BY_NAME.computeIfAbsent(name, n -> new Identifier[]{
                DexRider.id("item/armor/" + n),
                DexRider.id("textures/item/armor/" + n + ".png"),
                DexRider.id("textures/item/armor/" + n + "_glowmask.png")});
    }

    public static Identifier glowTexture(GeoRenderState state) {
        return resources(modelName(state))[2];
    }

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return resources(modelName(renderState))[0];
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return resources(modelName(renderState))[1];
    }

    @Override
    public Identifier getAnimationResource(DexArmorItem animatable) {
        return ANIMATIONS;
    }
}
