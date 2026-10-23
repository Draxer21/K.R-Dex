package com.draxer.dexrider.client;

import com.draxer.dexrider.DexRider;
import com.draxer.dexrider.item.DexArmorItem;
import com.draxer.dexrider.item.SuitConfig;
import com.draxer.dexrider.registry.DexComponents;
import com.draxer.dexrider.rider.FormRide;
import com.draxer.dexrider.rider.KamenRide;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.GeoArmorRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Renderer 3D de la armadura de Dex, con personalizacion.
 *
 * Capas (assets/dexrider/textures/item/armor/):
 *   dex_armor.png             base fija: negro, plateado, blanco y el Driver
 *   dex_armor_main.png        se tine con el color principal (o el de la Form Ride activa)
 *   dex_armor_main_right/left la misma capa partida por la mitad (Half-Cold Half-Hot: blanco hielo / rojo fuego)
 *   dex_armor_accent.png      se tine con el color de acentos (cuernos, X del pecho, guanteletes...)
 *   dex_armor_eyes.png        se tine con el color de ojos y se dibuja a plena luz (brillan de noche)
 *   dex_armor_eyes_plain.png  igual, pero sin las franjas horizontales
 *
 * Variantes de piezas = huesos del modelo que se ocultan: hornsStraight, hornsCurved, hornsShort,
 * hornsInner (los centrales) y booker.
 */
public final class DexArmorRenderer<R extends HumanoidRenderState & GeoRenderState> extends GeoArmorRenderer<DexArmorItem, R> {

    public static final DataTicket<String> EMOTION = DataTicket.create("dexrider_emotion", String.class);
    public static final DataTicket<Boolean> COMPLETE2 = DataTicket.create("dexrider_complete2", Boolean.class);
    public static final DataTicket<Boolean> UPGRADE = DataTicket.create("dexrider_upgrade", Boolean.class);
    public static final DataTicket<Integer> MAIN_COLOR = DataTicket.create("dexrider_main_color", Integer.class);
    public static final DataTicket<Integer> EYE_COLOR = DataTicket.create("dexrider_eye_color", Integer.class);
    public static final DataTicket<Integer> ACCENT_COLOR = DataTicket.create("dexrider_accent_color", Integer.class);
    public static final DataTicket<String> HORNS = DataTicket.create("dexrider_horns", String.class);
    public static final DataTicket<Boolean> EYE_STRIPES = DataTicket.create("dexrider_eye_stripes", Boolean.class);
    public static final DataTicket<String> RIDER = DataTicket.create("dexrider_rider", String.class);
    public static final DataTicket<Boolean> HALF = DataTicket.create("dexrider_half", Boolean.class);
    public static final DataTicket<String> CIPHER_MODE = DataTicket.create("dexrider_cipher_mode", String.class);
    public static final DataTicket<Boolean> SHOW_BOOKER = DataTicket.create("dexrider_show_booker", Boolean.class);
    /** Alas de Sky: nivel (0 = sin alas), cuanto estan abiertas (0-1) y el tiempo para el aleteo. */
    public static final DataTicket<Integer> SKY = DataTicket.create("dexrider_sky", Integer.class);
    public static final DataTicket<Float> SKY_OPEN = DataTicket.create("dexrider_sky_open", Float.class);
    public static final DataTicket<Float> SKY_TIME = DataTicket.create("dexrider_sky_time", Float.class);
    private static final java.util.Map<Integer, Float> SKY_OPENNESS = new java.util.HashMap<>();

    private static final Identifier MAIN = DexRider.id("textures/item/armor/dex_armor_main.png");
    private static final Identifier MAIN_RIGHT = DexRider.id("textures/item/armor/dex_armor_main_right.png");
    private static final Identifier MAIN_LEFT = DexRider.id("textures/item/armor/dex_armor_main_left.png");
    private static final Identifier ACCENT = DexRider.id("textures/item/armor/dex_armor_accent.png");
    private static final Identifier EYES = DexRider.id("textures/item/armor/dex_armor_eyes.png");
    private static final Identifier EYES_PLAIN = DexRider.id("textures/item/armor/dex_armor_eyes_plain.png");

    public DexArmorRenderer() {
        super(new RiderArmorModel());
        // Capas tenidas: solo en la forma de Dex (los demas Riders traen sus colores en la textura)
        withRenderLayer(new TintLayer<>(this, state -> !isDex(state) || isHalf(state) ? null : MAIN,
                state -> intData(state, MAIN_COLOR, SuitConfig.DEFAULT.mainColor()), false));
        // Half-Cold Half-Hot: lado derecho hielo, lado izquierdo fuego
        withRenderLayer(new TintLayer<>(this, state -> isHalf(state) ? MAIN_RIGHT : null,
                state -> FormRide.HALF.suitColor(), false));
        withRenderLayer(new TintLayer<>(this, state -> isHalf(state) ? MAIN_LEFT : null,
                state -> FormRide.HALF_FIRE_COLOR, false));
        withRenderLayer(new TintLayer<>(this, state -> isDex(state) ? ACCENT : null, state -> intData(state, ACCENT_COLOR, SuitConfig.DEFAULT.accentColor()), false));
        withRenderLayer(new TintLayer<>(this,
                state -> !isDex(state) ? null : Boolean.FALSE.equals(state.getGeckolibData(EYE_STRIPES)) ? EYES_PLAIN : EYES,
                state -> intData(state, EYE_COLOR, SuitConfig.DEFAULT.eyeColor()), true));
        // Ojos y luces de los demas Riders, a plena luz
        withRenderLayer(new TintLayer<>(this,
                state -> isDex(state) ? null : RiderArmorModel.glowTexture(state),
                state -> 0xFFFFFF, true));
    }

    private static boolean isDex(GeoRenderState state) {
        return RiderArmorModel.rider(state) == KamenRide.DEX && !Boolean.TRUE.equals(state.getGeckolibData(UPGRADE));
    }

    private static boolean isHalf(GeoRenderState state) {
        return Boolean.TRUE.equals(state.getGeckolibData(HALF));
    }

    private static int intData(GeoRenderState state, DataTicket<Integer> ticket, int fallback) {
        Integer v = state.getGeckolibData(ticket);
        return v == null ? fallback : v;
    }

    /** Lee la personalizacion y la forma activa del peto de quien lleva la armadura. */
    @Override
    public void addRenderData(DexArmorItem animatable, RenderData relatedObject, R renderState, float partialTick) {
        ItemStack chest = relatedObject.entity().getItemBySlot(EquipmentSlot.CHEST);
        SuitConfig suit = chest.get(DexComponents.SUIT.get());
        if (suit == null) suit = SuitConfig.DEFAULT;
        String formId = chest.get(DexComponents.FORM.get());
        FormRide form = formId == null ? null : FormRide.byId(formId);

        String emotion = chest.get(DexComponents.EMOTION.get());
        int[] emo = com.draxer.dexrider.rider.DexEmotions.colors(emotion);
        renderState.addGeckolibData(EMOTION, emotion == null ? "" : emotion);
        renderState.addGeckolibData(MAIN_COLOR, emo != null ? emo[0] : form != null ? form.suitColor() : suit.mainColor());
        renderState.addGeckolibData(HALF, form == FormRide.HALF);
        String rider = chest.get(DexComponents.RIDER.get());
        renderState.addGeckolibData(RIDER, rider == null ? "dex" : rider);
        String cipherMode = chest.get(DexComponents.CIPHER_MODE.get());
        renderState.addGeckolibData(CIPHER_MODE, cipherMode == null ? "" : cipherMode);
        renderState.addGeckolibData(UPGRADE, Boolean.TRUE.equals(chest.get(DexComponents.UPGRADE.get())));
        renderState.addGeckolibData(COMPLETE2, Boolean.TRUE.equals(chest.get(DexComponents.COMPLETE_LV2.get())));
        renderState.addGeckolibData(EYE_COLOR, emo != null ? emo[1] : suit.eyeColor());
        renderState.addGeckolibData(ACCENT_COLOR, emo != null ? emo[2] : suit.accentColor());
        renderState.addGeckolibData(HORNS, suit.horns().id());
        renderState.addGeckolibData(EYE_STRIPES, suit.eyeStripes());
        renderState.addGeckolibData(SHOW_BOOKER, suit.showBooker());
        // alas de Sky: plegadas en el suelo, se abren en el aire (suavizado por entidad)
        var entity = relatedObject.entity();
        int sky = 0;
        float open = 0;
        if (form == FormRide.SKY) {
            Integer lv = chest.get(DexComponents.FORM_LEVEL.get());
            sky = lv == null ? 1 : Math.max(1, Math.min(3, lv));
            boolean airborne = !entity.onGround() && !entity.isInWater()
                    || (entity instanceof net.minecraft.world.entity.player.Player pl && pl.getAbilities().flying);
            float prev = SKY_OPENNESS.getOrDefault(entity.getId(), 0F);
            open = prev + ((airborne ? 1F : 0F) - prev) * 0.15F;
            if (SKY_OPENNESS.size() > 256) SKY_OPENNESS.clear();
            SKY_OPENNESS.put(entity.getId(), open);
        }
        renderState.addGeckolibData(SKY, sky);
        renderState.addGeckolibData(SKY_OPEN, open);
        renderState.addGeckolibData(SKY_TIME, entity.tickCount + partialTick);
    }

    /** Oculta las variantes de piezas que el jugador no eligio. */
    @Override
    public void adjustModelBonesForRender(RenderPassInfo<R> renderPassInfo, BoneSnapshots snapshots) {
        super.adjustModelBonesForRender(renderPassInfo, snapshots);
        R state = renderPassInfo.renderState();
        SuitConfig.Horns horns = SuitConfig.Horns.byId(String.valueOf(state.getGeckolibData(HORNS)));

        hide(snapshots, "hornsStraight", horns != SuitConfig.Horns.STRAIGHT);
        hide(snapshots, "hornsCurved", horns != SuitConfig.Horns.CURVED);
        hide(snapshots, "hornsShort", horns != SuitConfig.Horns.SHORT);
        hide(snapshots, "hornsInner", horns == SuitConfig.Horns.NONE);
        hide(snapshots, "booker", Boolean.FALSE.equals(state.getGeckolibData(SHOW_BOOKER)));
        // Emotion: cristal en la frente (las dos) y puas (solo Violent)
        String emo = state.getGeckolibData(EMOTION);
        boolean violent = "violent".equals(emo), any = emo != null && !emo.isEmpty();
        hide(snapshots, "emotionCrystal", !any);
        for (String b : new String[]{"spikesHead", "spikesBody", "spikesRArm", "spikesLArm"}) hide(snapshots, b, !violent);
        // Alas de Sky
        int sky = intData(state, SKY, 0);
        hide(snapshots, "skyWings", sky == 0);
        if (sky > 0) {
            Float o = state.getGeckolibData(SKY_OPEN), tm = state.getGeckolibData(SKY_TIME);
            float open = o == null ? 0 : o, time = tm == null ? 0 : tm;
            float fold = 1F - open;
            float flap = (float) Math.sin(time * 0.55F) * 0.38F * open;                // aleteo en el aire
            float breathe = (float) Math.sin(time * 0.08F) * 0.04F * fold;             // respiracion plegadas
            float y = fold * 1.42F + flap, z = fold * 0.75F + breathe;
            snapshots.get("skyWingR").ifPresent(s -> s.setRotation(0, -y, -z));
            snapshots.get("skyWingL").ifPresent(s -> s.setRotation(0, y, z));
            float sc = sky == 1 ? 0.85F : sky == 2 ? 1.0F : 1.15F;
            snapshots.get("skyWings").ifPresent(s -> s.setScale(sc, sc, sc));
        }
    }

    private static void hide(BoneSnapshots snapshots, String bone, boolean hidden) {
        snapshots.get(bone).ifPresent(s -> s.skipRender(hidden).skipChildrenRender(hidden));
    }
}
