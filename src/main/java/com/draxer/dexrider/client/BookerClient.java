package com.draxer.dexrider.client;

import com.draxer.dexrider.DexRider;
import com.draxer.dexrider.item.DexWeaponItem;
import com.draxer.dexrider.registry.DexItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;
import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.jspecify.annotations.Nullable;

/**
 * Ride Booker 3D en la mano (solo cliente).
 *
 *  - Propiedad de modelo {@code dexrider:booker_morph}: 0 = espada, 1 = pistola. items/ride_booker.json elige uno de
 *    los 6 cuadros (ride_booker_m0..m5, generados por tools/ride_booker_model.py) para animar la transformacion.
 *  - En forma pistola el brazo apunta hacia donde miras (pose propia, META-INF/enumextensions.json).
 */
@EventBusSubscriber(modid = DexRider.MODID, value = Dist.CLIENT)
public final class BookerClient {
    private BookerClient() {}

    /** Parametros de la pose "apuntar con una mano" (los lee el extensor de enums de NeoForge). */
    @SuppressWarnings("unused")
    public static final class EnumParams {
        public static final EnumProxy<HumanoidModel.ArmPose> GUN_AIM = new EnumProxy<>(
                HumanoidModel.ArmPose.class, false, false, (IArmPoseTransformer) (model, state, arm) -> {
                    boolean right = arm == HumanoidArm.RIGHT;
                    ModelPart a = right ? model.rightArm : model.leftArm;
                    a.yRot = (right ? -0.1F : 0.1F) + model.head.yRot;
                    a.xRot = -(float) Math.PI / 2.0F + model.head.xRot;
                });
    }

    /** Avance de la transformacion, con la fraccion del tick para que se vea suave. */
    public record Morph() implements RangeSelectItemModelProperty {
        public static final MapCodec<Morph> MAP_CODEC = MapCodec.unit(new Morph());

        @Override
        public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
            ClientLevel lvl = level != null ? level : Minecraft.getInstance().level;
            if (lvl == null) return DexWeaponItem.isGunMode(stack) ? 1.0F : 0.0F;
            double time = lvl.getGameTime() + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
            return DexWeaponItem.morph(stack, time);
        }

        @Override
        public MapCodec<Morph> type() {
            return MAP_CODEC;
        }
    }

    @SubscribeEvent
    static void registerProperties(RegisterRangeSelectItemModelPropertyEvent event) {
        event.register(DexRider.id("booker_morph"), Morph.MAP_CODEC);
    }

    @SubscribeEvent
    static void registerExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public HumanoidModel.@Nullable ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                if (stack.isEmpty() || !DexWeaponItem.isGunMode(stack)) return null;
                double time = entity.level().getGameTime();
                return DexWeaponItem.morph(stack, time) >= 0.5F ? EnumParams.GUN_AIM.getValue() : null;
            }
        }, DexItems.RIDE_BOOKER.get());
    }
}
