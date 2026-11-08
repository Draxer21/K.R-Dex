package com.draxer.dexrider.client;

import com.draxer.dexrider.DexRider;
import com.draxer.dexrider.item.DexArmorItem;
import com.draxer.dexrider.item.SuitLook;
import com.draxer.dexrider.item.SuitLooks;
import com.draxer.dexrider.item.SuitProfile;
import com.draxer.dexrider.rider.Suits;
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
 * Renderer 3D de la armadura de Dex, con el editor del traje (0.33).
 *
 * Capas en escala de grises (assets/dexrider/textures/item/armor/, las genera split_suit.py) que se tinen con
 * los colores del diseno puesto; las mismas para la Upgrade Form (prefijo dex_upgrade):
 *   _main (por segmento: casco, pecho, brazos, piernas, botas), _main_right/_left (Half-Cold Half-Hot),
 *   _accent, _silver, _inner (negro), _scarf, _crystal, _eyes / _eyes_plain (a plena luz si brillan).
 *   Acabados: _matte y _metal para main, accent, silver y scarf.
 * La base fija (dex_armor.png) solo deja ver el Driver, que siempre conserva su diseno.
 *
 * Piezas = huesos del modelo que se ocultan: cuernos (hornsStraight/Curved/Short/Inner), hombreras
 * (pauldronR/L, pauldronFlatR/L), bufanda (scarf, scarfLong), crest, booker, emotionCrystal, puas y alas de Sky.
 */
public final class DexArmorRenderer<R extends HumanoidRenderState & GeoRenderState> extends GeoArmorRenderer<DexArmorItem, R> {

    public static final DataTicket<String> EMOTION = DataTicket.create("dexrider_emotion", String.class);
    public static final DataTicket<Boolean> COMPLETE2 = DataTicket.create("dexrider_complete2", Boolean.class);
    public static final DataTicket<Boolean> UPGRADE = DataTicket.create("dexrider_upgrade", Boolean.class);
    public static final DataTicket<String> RIDER = DataTicket.create("dexrider_rider", String.class);
    public static final DataTicket<Boolean> HALF = DataTicket.create("dexrider_half", Boolean.class);
    public static final DataTicket<String> CIPHER_MODE = DataTicket.create("dexrider_cipher_mode", String.class);
    public static final DataTicket<SuitLooks.Resolved> LOOK = DataTicket.create("dexrider_look", SuitLooks.Resolved.class);
    /** Alas de Sky: nivel (0 = sin alas), cuanto estan abiertas (0-1) y el tiempo para el aleteo. */
    public static final DataTicket<Integer> SKY = DataTicket.create("dexrider_sky", Integer.class);
    public static final DataTicket<Float> SKY_OPEN = DataTicket.create("dexrider_sky_open", Float.class);
    public static final DataTicket<Float> SKY_TIME = DataTicket.create("dexrider_sky_time", Float.class);
    private static final java.util.Map<Integer, Float> SKY_OPENNESS = new java.util.HashMap<>();

    private static final SuitLooks.Resolved FACTORY = SuitLooks.factory(SuitProfile.BASE);
    private static final java.util.Map<String, Identifier> TEXTURES = new java.util.concurrent.ConcurrentHashMap<>();

    public DexArmorRenderer() {
        super(new RiderArmorModel());
        // zonas principales, una por segmento (en la mitad de Half-Cold Half-Hot, las dos mitades)
        withRenderLayer(new TintLayer<>(this, s -> !custom(s) || isHalf(s) ? null : tex(s, "main", true),
                (s, seg) -> look(s).color(zone(seg)), s -> look(s).on("glow_main")));
        withRenderLayer(new TintLayer<>(this, s -> isHalf(s) ? tex(s, "main_right", true) : null,
                (s, seg) -> look(s).color(SuitLook.HALF_RIGHT), s -> look(s).on("glow_main")));
        withRenderLayer(new TintLayer<>(this, s -> isHalf(s) ? tex(s, "main_left", true) : null,
                (s, seg) -> look(s).color(SuitLook.HALF_LEFT), s -> look(s).on("glow_main")));
        withRenderLayer(new TintLayer<>(this, s -> custom(s) ? tex(s, "accent", true) : null,
                (s, seg) -> look(s).color(SuitLook.ACCENT), s -> look(s).on("glow_accent")));
        withRenderLayer(new TintLayer<>(this, s -> custom(s) ? tex(s, "silver", true) : null,
                (s, seg) -> look(s).color(SuitLook.SILVER), s -> false));
        withRenderLayer(new TintLayer<>(this, s -> custom(s) ? tex(s, "inner", false) : null,
                (s, seg) -> look(s).color(SuitLook.INNER), s -> false));
        withRenderLayer(new TintLayer<>(this, s -> usesScarf(s) ? tex(s, "scarf", true) : null,
                (s, seg) -> look(s).color(SuitLook.SCARF), s -> false));
        withRenderLayer(new TintLayer<>(this, s -> usesCrystal(s) ? tex(s, "crystal", false) : null,
                (s, seg) -> look(s).color(SuitLook.CRYSTAL), s -> look(s).on("glow_crystal")));
        // lineas del visor (los huecos entre los lentes), con su propio color
        withRenderLayer(new TintLayer<>(this, s -> isDex(s) && visor(s) != null ? tex(s, "lines" + visor(s), false) : null,
                (s, seg) -> look(s).color(SuitLook.VISOR), s -> false));
        withRenderLayer(new TintLayer<>(this,
                s -> !custom(s) ? null : tex(s, eyesLayer(s), false),
                (s, seg) -> look(s).color(SuitLook.EYES), s -> look(s).on("glow_eyes")));
        // Ojos y luces de los demas Riders, a plena luz
        withRenderLayer(new TintLayer<>(this,
                state -> custom(state) ? null : RiderArmorModel.glowTexture(state),
                state -> 0xFFFFFF, true));
    }

    /** Dex en su forma de Dex (sin Upgrade). */
    private static boolean isDex(GeoRenderState state) {
        return RiderArmorModel.rider(state) == KamenRide.DEX && !Boolean.TRUE.equals(state.getGeckolibData(UPGRADE));
    }

    /** Dex o su Upgrade Form: los dos se pintan con el diseno del jugador. */
    private static boolean custom(GeoRenderState state) {
        return RiderArmorModel.rider(state) == KamenRide.DEX;
    }

    private static boolean usesScarf(GeoRenderState s) {
        if (!custom(s)) return false;
        return isDex(s) ? !"none".equals(look(s).part("scarf")) : !"none".equals(look(s).part("upg_cape"));
    }

    private static boolean usesCrystal(GeoRenderState s) {
        if (!custom(s)) return false;
        SuitLooks.Resolved l = look(s);
        return isDex(s) ? l.on("crystal") || l.on("frost_crystals") || l.on("cinder_flames") : "core".equals(l.part("upg_chest"));
    }

    /** Sufijo de las lineas del visor ("" = clasico, "_h_1_1"...), o null si no hay lineas. */
    private static String visor(GeoRenderState s) {
        SuitLooks.Resolved l = look(s);
        String v = l.part("visor_lines");
        if ("none".equals(v) || ("classic".equals(v) && !l.on("stripes"))) return null;
        if ("classic".equals(v)) return "";
        return "_" + v.charAt(0) + "_" + ("thick".equals(l.part("visor_thick")) ? 2 : 1) + "_" + ("wide".equals(l.part("visor_gap")) ? 3 : 1);
    }

    private static String eyesLayer(GeoRenderState s) {
        if (!isDex(s)) return "eyes";
        String v = visor(s);
        return v == null ? "eyes_plain" : "eyes" + v;
    }

    private static boolean isHalf(GeoRenderState state) {
        return isDex(state) && Boolean.TRUE.equals(state.getGeckolibData(HALF));
    }

    private static SuitLooks.Resolved look(GeoRenderState state) {
        SuitLooks.Resolved l = state.getGeckolibData(LOOK);
        return l == null ? FACTORY : l;
    }

    private static String zone(GeoArmorRenderer.ArmorSegment seg) {
        return switch (seg) {
            case HEAD -> SuitLook.HELMET;
            case CHEST -> SuitLook.CHEST;
            case LEFT_ARM, RIGHT_ARM -> SuitLook.ARMS;
            case LEFT_LEG, RIGHT_LEG -> SuitLook.LEGS;
            default -> SuitLook.BOOTS;
        };
    }

    /** dex_armor_<capa>[_matte|_metal].png (o dex_upgrade_...). */
    private static Identifier tex(GeoRenderState state, String layer, boolean finish) {
        String f = finish ? look(state).part("finish") : "normal";
        String name = (isDex(state) ? "dex_armor_" : "dex_upgrade_") + layer + ("normal".equals(f) ? "" : "_" + f);
        return TEXTURES.computeIfAbsent(name, n -> DexRider.id("textures/item/armor/" + n + ".png"));
    }

    private static int intData(GeoRenderState state, DataTicket<Integer> ticket, int fallback) {
        Integer v = state.getGeckolibData(ticket);
        return v == null ? fallback : v;
    }

    /** Lee el diseno puesto y la forma activa del peto de quien lleva la armadura. */
    @Override
    public void addRenderData(DexArmorItem animatable, RenderData relatedObject, R renderState, float partialTick) {
        ItemStack chest = relatedObject.entity().getItemBySlot(EquipmentSlot.CHEST);
        String formId = chest.get(DexComponents.FORM.get());
        FormRide form = formId == null ? null : FormRide.byId(formId);
        String emotion = chest.get(DexComponents.EMOTION.get());
        boolean upgrade = Boolean.TRUE.equals(chest.get(DexComponents.UPGRADE.get()));

        renderState.addGeckolibData(EMOTION, emotion == null ? "" : emotion);
        renderState.addGeckolibData(HALF, form == FormRide.HALF && !upgrade && emotion == null);
        String rider = chest.get(DexComponents.RIDER.get());
        renderState.addGeckolibData(RIDER, rider == null ? "dex" : rider);
        String cipherMode = chest.get(DexComponents.CIPHER_MODE.get());
        renderState.addGeckolibData(CIPHER_MODE, cipherMode == null ? "" : cipherMode);
        renderState.addGeckolibData(UPGRADE, upgrade);
        renderState.addGeckolibData(COMPLETE2, Boolean.TRUE.equals(chest.get(DexComponents.COMPLETE_LV2.get())));
        renderState.addGeckolibData(LOOK, SuitLooks.resolve(Suits.worn(chest), SuitLooks.stateFor(upgrade, emotion, form)));
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
        SuitLooks.Resolved look = look(state);
        String horns = look.part("horns");
        hide(snapshots, "hornsStraight", !"straight".equals(horns));
        hide(snapshots, "hornsCurved", !"curved".equals(horns));
        hide(snapshots, "hornsShort", !"short".equals(horns));
        hide(snapshots, "hornsInner", "none".equals(horns));
        hide(snapshots, "booker", !look.on("booker"));
        String pauldrons = look.part("pauldrons");
        for (String b : new String[]{"pauldronR", "pauldronL"}) hide(snapshots, b, !"layered".equals(pauldrons));
        for (String b : new String[]{"pauldronFlatR", "pauldronFlatL"}) hide(snapshots, b, !"flat".equals(pauldrons));
        String scarf = look.part("scarf");
        hide(snapshots, "scarf", "none".equals(scarf));
        hide(snapshots, "scarfLong", !"long".equals(scarf));
        hide(snapshots, "crest", !look.on("crest"));
        hide(snapshots, "emotionCrystal", !look.on("crystal"));
        hide(snapshots, "spikesHead", !look.on("spikes_head"));
        hide(snapshots, "spikesBody", !look.on("spikes_body"));
        hide(snapshots, "spikesRArm", !look.on("spikes_arms"));
        hide(snapshots, "spikesLArm", !look.on("spikes_arms"));
        // etapa 3: Upgrade y Form Rides
        String uh = look.part("upg_horns"), us = look.part("upg_shoulders"), uc = look.part("upg_chest"), cape = look.part("upg_cape");
        hidePart(snapshots, "upgHornsBlade", !"blade".equals(uh));
        hidePart(snapshots, "upgHornsTwin", !"twin".equals(uh));
        hidePart(snapshots, "upgShouldersCap", !"cap".equals(us));
        hidePart(snapshots, "upgShouldersBlades", !"blades".equals(us));
        hidePart(snapshots, "upgChestPlate", !"plate".equals(uc));
        hidePart(snapshots, "upgChestCore", !"core".equals(uc));
        hidePart(snapshots, "upgCape", !"cape".equals(cape));
        hidePart(snapshots, "upgScarf", !"scarf".equals(cape));
        hidePart(snapshots, "hydroFins", !look.on("fins"));
        hidePart(snapshots, "frostCrystals", !look.on("frost_crystals"));
        hidePart(snapshots, "cinderFlames", !look.on("cinder_flames"));
        hidePart(snapshots, "speedSpoiler", !look.on("spoiler"));
        hidePart(snapshots, "gasDucts", !look.on("ducts"));
        hidePart(snapshots, "terraPlates", !look.on("terra_plates"));
        // Alas de Sky
        int sky = look.on("wings") ? intData(state, SKY, 0) : 0;
        hide(snapshots, "skyWings", sky == 0);
        if (sky > 0) {
            Float o = state.getGeckolibData(SKY_OPEN), tm = state.getGeckolibData(SKY_TIME);
            float open = o == null ? 0 : o, time = tm == null ? 0 : tm;
            float fold = 1F - open;
            float flap = (float) Math.sin(time * 0.55F) * 0.38F * open;                // aleteo en el aire
            float breathe = (float) Math.sin(time * 0.08F) * 0.04F * fold;             // respiracion plegadas
            float y = fold * 0.3F + flap, z = fold * 1.25F + breathe;   // plegadas: colgando pegadas a la espalda
            snapshots.get("skyWingR").ifPresent(s -> s.setRotation(0, -y, -z));
            snapshots.get("skyWingL").ifPresent(s -> s.setRotation(0, y, z));
            float sc = sky == 1 ? 0.85F : sky == 2 ? 1.0F : 1.15F;
            snapshots.get("skyWings").ifPresent(s -> s.setScale(sc, sc, sc));
        }
    }

    /** Una pieza puede tener huesos extra en los brazos (nombre_1, nombre_2). */
    private static void hidePart(BoneSnapshots snapshots, String bone, boolean hidden) {
        hide(snapshots, bone, hidden);
        hide(snapshots, bone + "_1", hidden);
        hide(snapshots, bone + "_2", hidden);
    }

    private static void hide(BoneSnapshots snapshots, String bone, boolean hidden) {
        snapshots.get(bone).ifPresent(s -> s.skipRender(hidden).skipChildrenRender(hidden));
    }
}
