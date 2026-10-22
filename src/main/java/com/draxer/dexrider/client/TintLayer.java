package com.draxer.dexrider.client;

import com.draxer.dexrider.item.DexArmorItem;
import com.geckolib.renderer.GeoArmorRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Capa de la armadura que vuelve a dibujar las mismas piezas con una textura en escala de grises
 * tenida con un color (el que elige el jugador). Copia el recorrido de huesos de GeoArmorRenderer.
 *
 * Si la funcion de textura devuelve null, la capa no se dibuja.
 * fullBright = true: se dibuja a plena luz, para que los ojos se vean encendidos de noche.
 */
public class TintLayer<R extends HumanoidRenderState & GeoRenderState>
        extends GeoRenderLayer<DexArmorItem, GeoArmorRenderer.RenderData, R> {

    private static final int FULL_BRIGHT = 0xF000F0;

    private final GeoArmorRenderer<DexArmorItem, R> armorRenderer;
    private final Function<R, Identifier> texture;
    private final ToIntFunction<R> color;
    private final boolean fullBright;

    public TintLayer(GeoArmorRenderer<DexArmorItem, R> renderer, Function<R, Identifier> texture,
                     ToIntFunction<R> color, boolean fullBright) {
        super(renderer);
        this.armorRenderer = renderer;
        this.texture = texture;
        this.color = color;
        this.fullBright = fullBright;
    }

    @Override
    protected Identifier getTextureResource(R renderState) {
        return this.texture.apply(renderState);
    }

    @Override
    public void submitRenderTask(RenderPassInfo<R> renderPassInfo, SubmitNodeCollector renderTasks) {
        if (!renderPassInfo.willRender()) return;

        final R renderState = renderPassInfo.renderState();
        final EquipmentSlot slot = renderState.getGeckolibData(GeoArmorRenderer.CURRENT_SLOT);
        if (slot == null) return;

        final Identifier texture = getTextureResource(renderState);
        if (texture == null) return;   // capa desactivada en este momento
        final RenderType renderType = RenderTypes.armorCutoutNoCull(texture);
        final int argb = 0xFF000000 | (this.color.applyAsInt(renderState) & 0xFFFFFF);
        final int light = this.fullBright ? FULL_BRIGHT : renderPassInfo.packedLight();
        final int overlay = renderPassInfo.packedOverlay();

        renderTasks.order(1).submitCustomGeometry(renderPassInfo.poseStack(), renderType, (pose, vertexConsumer) -> {
            renderPassInfo.poseStack().pushPose();
            renderPassInfo.poseStack().last().set(pose);
            renderPassInfo.renderPosed(() -> {
                for (GeoArmorRenderer.ArmorSegment segment : this.armorRenderer.getSegmentsForSlot(renderState, slot)) {
                    renderPassInfo.model().getBone(this.armorRenderer.getBoneNameForSegment(renderState, segment))
                            .ifPresent(bone -> bone.positionAndRender(renderPassInfo, vertexConsumer, light, overlay, argb));
                }
            });
            renderPassInfo.poseStack().popPose();
        });
    }
}
