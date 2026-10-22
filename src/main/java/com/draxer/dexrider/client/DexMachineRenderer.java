package com.draxer.dexrider.client;

import com.draxer.dexrider.DexRider;
import com.draxer.dexrider.vehicle.BikeKind;
import com.draxer.dexrider.vehicle.DexMachineEntity;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * Renderer de las motos.
 *   geckolib/models/entity/<moto>.geo.json · textures/entity/<moto>.png (+ _glowmask.png: faros y luces)
 *   animaciones compartidas: geckolib/animations/entity/rider_bike.animation.json (ruedas)
 */
public class DexMachineRenderer<R extends LivingEntityRenderState & GeoRenderState> extends GeoEntityRenderer<DexMachineEntity, R> {
    public DexMachineRenderer(EntityRendererProvider.Context context, BikeKind kind) {
        super(context, new BikeModel(kind));
        withRenderLayer(AutoGlowingGeoLayer::new);
    }

    private static final com.geckolib.constant.dataticket.DataTicket<Boolean> SCARAB =
            com.geckolib.constant.dataticket.DataTicket.create("dexrider_bike_scarab", Boolean.class);

    @Override
    public void addRenderData(DexMachineEntity animatable, @org.jetbrains.annotations.Nullable Void relatedObject, R renderState, float partialTick) {
        renderState.addGeckolibData(SCARAB, animatable.scarab());
    }

    /** El caparazon del escarabajo (hueso "scarab") solo se ve con la fusion activa. */
    @Override
    public void adjustModelBonesForRender(com.geckolib.renderer.base.RenderPassInfo<R> renderPassInfo, com.geckolib.renderer.base.BoneSnapshots snapshots) {
        super.adjustModelBonesForRender(renderPassInfo, snapshots);
        boolean on = Boolean.TRUE.equals(renderPassInfo.renderState().getGeckolibData(SCARAB));
        snapshots.get("scarab").ifPresent(b -> b.skipRender(!on).skipChildrenRender(!on));
    }

    static final class BikeModel extends GeoModel<DexMachineEntity> {
        private static final Identifier ANIMATIONS = DexRider.id("entity/rider_bike");
        private final Identifier model, texture;

        BikeModel(BikeKind kind) {
            this.model = DexRider.id("entity/" + kind.id());
            this.texture = DexRider.id("textures/entity/" + kind.id() + ".png");
        }

        @Override public Identifier getModelResource(GeoRenderState renderState) { return model; }
        @Override public Identifier getTextureResource(GeoRenderState renderState) { return texture; }
        @Override public Identifier getAnimationResource(DexMachineEntity animatable) { return ANIMATIONS; }
    }
}
