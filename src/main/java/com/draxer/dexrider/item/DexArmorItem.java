package com.draxer.dexrider.item;

import com.draxer.dexrider.client.DexArmorRenderer;
import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.renderer.GeoArmorRenderer;
import com.geckolib.util.GeckoLibUtil;
import com.google.common.base.Suppliers;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Pieza de la armadura de Dex con modelo 3D de GeckoLib.
 * Las cuatro piezas comparten un solo modelo (dex_armor.geo.json); GeckoLib dibuja solo
 * los huesos de la pieza correspondiente (armorHead, armorBody + brazos, piernas, botas).
 */
public class DexArmorItem extends Item implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** Las Properties ya llegan con humanoidArmor(...) aplicado desde DexItems. */
    public DexArmorItem(Properties properties) {
        super(properties);
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            // Se crea la primera vez que hace falta y se reutiliza (solo existe en el cliente)
            private final Supplier<DexArmorRenderer<?>> renderer = Suppliers.memoize(DexArmorRenderer::new);

            @Nullable
            @Override
            public GeoArmorRenderer<?, ?> getGeoArmorRenderer(ItemStack itemStack, EquipmentSlot equipmentSlot) {
                return this.renderer.get();
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Sin animaciones por ahora (el modelo esta listo para agregarlas en Blockbench).
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
