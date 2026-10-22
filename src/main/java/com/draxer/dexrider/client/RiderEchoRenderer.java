package com.draxer.dexrider.client;

import com.draxer.dexrider.DexRider;
import com.draxer.dexrider.entity.RiderMob;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import org.jetbrains.annotations.Nullable;
import com.draxer.dexrider.rider.KamenRide;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * Renderer de los Riders del juego (ecos y amigos): usa el MISMO modelo y textura que la armadura del Rider
 * (geckolib/models/item/armor/rider_x.geo.json), que ademas trae el traje interior en los huesos bipedX.
 * Animaciones compartidas: geckolib/animations/entity/rider_echo.animation.json
 */
public class RiderEchoRenderer<R extends LivingEntityRenderState & GeoRenderState> extends GeoEntityRenderer<RiderMob, R> {

    public RiderEchoRenderer(EntityRendererProvider.Context context, KamenRide rider) {
        super(context, new EchoModel(rider));
        withRenderLayer(AutoGlowingGeoLayer::new);
    }

    private static final DataTicket<Boolean> WEAPON = DataTicket.create("dexrider_npc_weapon", Boolean.class);
    static final DataTicket<Boolean> CIVILIAN = DataTicket.create("dexrider_npc_civilian", Boolean.class);
    static final DataTicket<Integer> FORGE_FORM = DataTicket.create("dexrider_npc_forge_form", Integer.class);

    @Override
    public void addRenderData(RiderMob animatable, @Nullable Void relatedObject, R renderState, float partialTick) {
        renderState.addGeckolibData(WEAPON, animatable.hasWeaponOut());
        renderState.addGeckolibData(CIVILIAN, animatable.isCivilian());
        renderState.addGeckolibData(FORGE_FORM, animatable.riderForm());
    }

    /** El arma (hueso "weapon") solo se ve cuando la tiene fuera. */
    @Override
    public void adjustModelBonesForRender(RenderPassInfo<R> renderPassInfo, BoneSnapshots snapshots) {
        super.adjustModelBonesForRender(renderPassInfo, snapshots);
        boolean out = Boolean.TRUE.equals(renderPassInfo.renderState().getGeckolibData(WEAPON));
        snapshots.get("weapon").ifPresent(b -> b.skipRender(!out).skipChildrenRender(!out));
    }

    static final class EchoModel extends GeoModel<RiderMob> {
        private final KamenRide rider;
        private final Identifier model, texture;
        @Nullable private final Identifier civilModel, civilTexture;
        private static final Identifier ANIMATIONS = DexRider.id("entity/rider_echo");

        EchoModel(KamenRide rider) {
            this.rider = rider;
            this.model = DexRider.id("item/armor/" + rider.model);
            this.texture = DexRider.id("textures/item/armor/" + rider.model + ".png");
            // forma civil de los OC (solo ellos pueden quitarse el traje en este mundo)
            String civil = switch (rider) {
                case CIPHER -> "civil_takumi";
                case WARDEN -> "civil_isamu";
                case PSYGA -> "civil_nagisa";
                case ORGA -> "civil_genzo";
                case KAIXA -> "civil_tetsuya";
                default -> null;
            };
            this.civilModel = civil == null ? null : DexRider.id("item/armor/" + civil);
            this.civilTexture = civil == null ? null : DexRider.id("textures/item/armor/" + civil + ".png");
        }

        private boolean civil(GeoRenderState state) {
            return civilModel != null && Boolean.TRUE.equals(state.getGeckolibData(CIVILIAN));
        }

        /** Forge (mitades) e Izuku (Dragon, Pegasus, Titan, Rising) cambian de forma en combate: cada una tiene su modelo. */
        @Nullable
        private Identifier[] forge(GeoRenderState state) {
            Integer form = state.getGeckolibData(FORGE_FORM);
            if (form == null || form == 0) return null;
            if (rider == KamenRide.FORGE) return RiderArmorModel.resources(com.draxer.dexrider.rider.ForgeForms.model(form));
            if (rider == KamenRide.NINE) return RiderArmorModel.resources(com.draxer.dexrider.entity.NineForms.model(form));
            if (rider == KamenRide.CIPHER) return RiderArmorModel.resources(form == 1 ? "rider_cipher_accel" : "rider_cipher_blaster");
            return null;
        }

        @Override
        public Identifier getModelResource(GeoRenderState renderState) {
            if (civil(renderState)) return civilModel;
            Identifier[] f = forge(renderState);
            return f != null ? f[0] : model;
        }

        @Override
        public Identifier getTextureResource(GeoRenderState renderState) {
            if (civil(renderState)) return civilTexture;
            Identifier[] f = forge(renderState);
            return f != null ? f[1] : texture;
        }

        @Override
        public Identifier getAnimationResource(RiderMob animatable) {
            return ANIMATIONS;
        }
    }
}
