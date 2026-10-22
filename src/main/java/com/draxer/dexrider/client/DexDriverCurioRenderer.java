package com.draxer.dexrider.client;

import com.draxer.dexrider.DexRider;
import com.draxer.dexrider.rider.Transformation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * Dibuja el Dex Driver en la cintura cuando esta en la ranura de cinturon de Curios.
 * Mientras estas transformado se oculta, porque la armadura 3D ya trae su propio Driver.
 */
public class DexDriverCurioRenderer implements ICurioRenderer.HumanoidRender {

    private static final Identifier TEXTURE = DexRider.id("textures/entity/dex_driver_belt.png");
    private final DexDriverBeltModel model;

    public DexDriverCurioRenderer() {
        this.model = new DexDriverBeltModel(Minecraft.getInstance().getEntityModels().bakeLayer(DexDriverBeltModel.LAYER));
    }

    @Override
    public HumanoidModel<HumanoidRenderState> getModel(ItemStack stack, SlotContext slotContext) {
        boolean transformed = slotContext.entity() instanceof Player p && Transformation.isTransformed(p);
        this.model.body.visible = !transformed;
        return this.model;
    }

    @Override
    public Identifier getModelTexture(ItemStack stack, SlotContext slotContext) {
        return TEXTURE;
    }
}
