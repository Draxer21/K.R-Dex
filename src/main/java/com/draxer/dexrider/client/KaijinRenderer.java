package com.draxer.dexrider.client;

import com.draxer.dexrider.DexRider;
import com.draxer.dexrider.entity.KaijinEntity;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/** Renderer de Dai-Shocker: modelos propios (item/armor/kaijin_*), animaciones de los Riders del juego. */
public class KaijinRenderer<R extends LivingEntityRenderState & GeoRenderState> extends GeoEntityRenderer<KaijinEntity, R> {

    public KaijinRenderer(EntityRendererProvider.Context context, KaijinEntity.Kind kind) {
        super(context, new Model(kind));
        withRenderLayer(AutoGlowingGeoLayer::new);
        withScale(kind.scale);
        this.shadowRadius = 0.5F * kind.scale;
    }

    static final class Model extends GeoModel<KaijinEntity> {
        private static final Identifier ANIMATIONS = DexRider.id("entity/rider_echo");
        private final Identifier model, texture;

        Model(KaijinEntity.Kind kind) {
            this.model = DexRider.id("item/armor/" + kind.id);
            this.texture = DexRider.id("textures/item/armor/" + kind.id + ".png");
        }

        @Override public Identifier getModelResource(GeoRenderState renderState) { return model; }
        @Override public Identifier getTextureResource(GeoRenderState renderState) { return texture; }
        @Override public Identifier getAnimationResource(KaijinEntity animatable) { return ANIMATIONS; }
    }
}
