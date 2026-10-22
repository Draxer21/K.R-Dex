package com.draxer.dexrider.client;

import com.draxer.dexrider.DexRider;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/**
 * El Dex Driver puesto en el cinturon (ranura de Curios), cuando NO estas transformado.
 * Solo tiene piezas en el torso: la correa y el Driver de tu boceto. Todo lo demas va vacio.
 * Textura: assets/dexrider/textures/entity/dex_driver_belt.png (256x128, 4 px por unidad).
 */
public class DexDriverBeltModel extends HumanoidModel<HumanoidRenderState> {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(DexRider.id("dex_driver_belt"), "main");

    public DexDriverBeltModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();

        // Partes vacias (el Driver solo va en la cintura)
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));

        // Torso: correa + Driver (coordenadas del torso: y=0 hombros, y=12 cadera, frente = -z)
        root.addOrReplaceChild("body", CubeListBuilder.create()
                        // Driver: placa frontal con todo el diseno pintado (cuerpo, placas laterales, aletas)
                        .texOffs(0, 0).addBox(-5.0F, 8.6F, -3.3F, 10, 5, 1)
                        // Correa alrededor de la cintura
                        .texOffs(0, 8).addBox(-4.5F, 10.2F, -2.5F, 9, 2, 5, new CubeDeformation(0.05F)),
                PartPose.ZERO);

        // Tamanio "logico" 64x32; la imagen real es 256x128 (4x mas detalle)
        return LayerDefinition.create(mesh, 64, 32);
    }
}
