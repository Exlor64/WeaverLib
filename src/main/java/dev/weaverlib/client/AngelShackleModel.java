package dev.weaverlib.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/** Geometry supplied by Ex, exported from Blockbench; animated links are rendered independently. */
final class AngelShackleModel {
    private AngelShackleModel() {}
	static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition Shackle = partdefinition.addOrReplaceChild("Shackle", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -2.0F, -2.0F, 1.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(0, 6).addBox(2.0F, -2.0F, -2.0F, 1.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(8, 26).addBox(1.75F, -1.5F, -2.75F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(26, 9).addBox(1.75F, -1.5F, 1.75F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(12, 26).addBox(-2.75F, -1.5F, 1.75F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(16, 26).addBox(-2.75F, -1.5F, -2.75F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(24, 24).addBox(-0.5F, -2.0F, 3.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 26).addBox(-0.5F, -2.0F, 5.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

		Shackle.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(4, 26).addBox(0.0F, -1.0F, -1.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5F, 0.5F, 5.25F, -0.8697F, -0.0086F, 0.0136F));

		Shackle.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(26, 6).addBox(0.0F, -1.0F, -1.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5F, -2.0F, 4.25F, -0.829F, 0.0F, 0.0F));

		Shackle.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(26, 3).addBox(0.0F, -1.0F, -1.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5F, -0.25F, 4.25F, 0.829F, 0.0F, 0.0F));

		Shackle.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(26, 0).addBox(0.0F, -1.0F, -1.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5F, -2.75F, 5.5F, 0.7854F, 0.0F, 0.0F));

		Shackle.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(10, 6).addBox(-3.0F, -2.0F, -2.0F, 1.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 5.0F, 0.0F, -1.5708F, 0.0F));

		Shackle.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(10, 0).addBox(-3.0F, -2.0F, -2.0F, 1.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -1.5708F, 0.0F));

		PartDefinition chain = Shackle.addOrReplaceChild("chain", CubeListBuilder.create(), PartPose.offset(0.0F, -1.0F, 4.75F));

		chain.addOrReplaceChild("cube_r7", CubeListBuilder.create().texOffs(20, 0).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.25F, 0.0F, 0.0F, 0.0F, -0.7854F, 0.0F));

		chain.addOrReplaceChild("cube_r8", CubeListBuilder.create().texOffs(20, 4).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.25F, 0.0F, 4.5F, 0.0F, 0.7854F, 0.0F));

		chain.addOrReplaceChild("cube_r9", CubeListBuilder.create().texOffs(20, 2).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.25F, 0.0F, 4.5F, 0.0F, -0.7854F, 0.0F));

		chain.addOrReplaceChild("cube_r10", CubeListBuilder.create().texOffs(0, 20).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.25F, 0.0F, 0.0F, 0.0F, 0.7854F, 0.0F));

		chain.addOrReplaceChild("cube_r11", CubeListBuilder.create().texOffs(8, 12).addBox(-1.0F, -1.0F, -1.0F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.75F, 0.0F, 1.75F, 0.0F, -1.5708F, 0.0F));

		chain.addOrReplaceChild("cube_r12", CubeListBuilder.create().texOffs(0, 12).addBox(-1.0F, -1.0F, -1.0F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.75F, 0.0F, 1.75F, 0.0F, -1.5708F, 0.0F));

		PartDefinition chain2 = chain.addOrReplaceChild("chain2", CubeListBuilder.create(), PartPose.offsetAndRotation(-0.5F, -0.5F, 3.25F, 0.0F, 0.0F, 1.5708F));

		chain2.addOrReplaceChild("cube_r13", CubeListBuilder.create().texOffs(20, 10).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.25F, 0.0F, 0.0F, 0.0F, -0.7854F, 0.0F));

		chain2.addOrReplaceChild("cube_r14", CubeListBuilder.create().texOffs(20, 8).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.25F, 0.0F, 4.5F, 0.0F, 0.7854F, 0.0F));

		chain2.addOrReplaceChild("cube_r15", CubeListBuilder.create().texOffs(20, 6).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.25F, 0.0F, 4.5F, 0.0F, -0.7854F, 0.0F));

		chain2.addOrReplaceChild("cube_r16", CubeListBuilder.create().texOffs(6, 20).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.25F, 0.0F, 0.0F, 0.0F, 0.7854F, 0.0F));

		chain2.addOrReplaceChild("cube_r17", CubeListBuilder.create().texOffs(8, 14).addBox(-1.0F, -1.0F, -1.0F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.75F, 0.0F, 1.75F, 0.0F, -1.5708F, 0.0F));

		chain2.addOrReplaceChild("cube_r18", CubeListBuilder.create().texOffs(0, 14).addBox(-1.0F, -1.0F, -1.0F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.75F, 0.0F, 1.75F, 0.0F, -1.5708F, 0.0F));

		PartDefinition chain3 = chain2.addOrReplaceChild("chain3", CubeListBuilder.create(), PartPose.offsetAndRotation(-0.5F, 0.0F, 4.0F, 0.0F, 0.0F, 1.5708F));

		chain3.addOrReplaceChild("cube_r19", CubeListBuilder.create().texOffs(6, 22).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.25F, 0.0F, 0.0F, 0.0F, -0.7854F, 0.0F));

		chain3.addOrReplaceChild("cube_r20", CubeListBuilder.create().texOffs(0, 22).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.25F, 0.0F, 4.5F, 0.0F, 0.7854F, 0.0F));

		chain3.addOrReplaceChild("cube_r21", CubeListBuilder.create().texOffs(18, 20).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.25F, 0.0F, 4.5F, 0.0F, -0.7854F, 0.0F));

		chain3.addOrReplaceChild("cube_r22", CubeListBuilder.create().texOffs(12, 20).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.25F, 0.0F, 0.0F, 0.0F, 0.7854F, 0.0F));

		chain3.addOrReplaceChild("cube_r23", CubeListBuilder.create().texOffs(8, 16).addBox(-1.0F, -1.0F, -1.0F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.75F, 0.0F, 1.75F, 0.0F, -1.5708F, 0.0F));

		chain3.addOrReplaceChild("cube_r24", CubeListBuilder.create().texOffs(0, 16).addBox(-1.0F, -1.0F, -1.0F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.75F, 0.0F, 1.75F, 0.0F, -1.5708F, 0.0F));

		PartDefinition chain4 = chain3.addOrReplaceChild("chain4", CubeListBuilder.create(), PartPose.offsetAndRotation(-0.5F, -0.5F, 3.75F, 0.0F, 0.0F, 1.5708F));

		chain4.addOrReplaceChild("cube_r25", CubeListBuilder.create().texOffs(6, 24).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.25F, 0.0F, 0.0F, 0.0F, -0.7854F, 0.0F));

		chain4.addOrReplaceChild("cube_r26", CubeListBuilder.create().texOffs(0, 24).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.25F, 0.0F, 4.5F, 0.0F, 0.7854F, 0.0F));

		chain4.addOrReplaceChild("cube_r27", CubeListBuilder.create().texOffs(18, 22).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.25F, 0.0F, 4.5F, 0.0F, -0.7854F, 0.0F));

		chain4.addOrReplaceChild("cube_r28", CubeListBuilder.create().texOffs(12, 22).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.25F, 0.0F, 0.0F, 0.0F, 0.7854F, 0.0F));

		chain4.addOrReplaceChild("cube_r29", CubeListBuilder.create().texOffs(16, 14).addBox(-1.0F, -1.0F, -1.0F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.75F, 0.0F, 1.75F, 0.0F, -1.5708F, 0.0F));

		chain4.addOrReplaceChild("cube_r30", CubeListBuilder.create().texOffs(16, 12).addBox(-1.0F, -1.0F, -1.0F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.75F, 0.0F, 1.75F, 0.0F, -1.5708F, 0.0F));

		PartDefinition chain5 = chain4.addOrReplaceChild("chain5", CubeListBuilder.create(), PartPose.offsetAndRotation(-0.5F, -0.5F, 3.25F, 0.0F, 0.0F, 1.5708F));

		chain5.addOrReplaceChild("cube_r31", CubeListBuilder.create().texOffs(24, 16).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.25F, 0.0F, 0.0F, 0.0F, -0.7854F, 0.0F));

		chain5.addOrReplaceChild("cube_r32", CubeListBuilder.create().texOffs(24, 14).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.25F, 0.0F, 4.5F, 0.0F, 0.7854F, 0.0F));

		chain5.addOrReplaceChild("cube_r33", CubeListBuilder.create().texOffs(24, 12).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.25F, 0.0F, 4.5F, 0.0F, -0.7854F, 0.0F));

		chain5.addOrReplaceChild("cube_r34", CubeListBuilder.create().texOffs(12, 24).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.25F, 0.0F, 0.0F, 0.0F, 0.7854F, 0.0F));

		chain5.addOrReplaceChild("cube_r35", CubeListBuilder.create().texOffs(0, 18).addBox(-1.0F, -1.0F, -1.0F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.75F, 0.0F, 1.75F, 0.0F, -1.5708F, 0.0F));

		chain5.addOrReplaceChild("cube_r36", CubeListBuilder.create().texOffs(16, 16).addBox(-1.0F, -1.0F, -1.0F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.75F, 0.0F, 1.75F, 0.0F, -1.5708F, 0.0F));

		PartDefinition chain6 = chain5.addOrReplaceChild("chain6", CubeListBuilder.create(), PartPose.offsetAndRotation(-0.5F, 0.0F, 4.0F, 0.0F, 0.0F, 1.5708F));

		chain6.addOrReplaceChild("cube_r37", CubeListBuilder.create().texOffs(24, 22).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.25F, 0.0F, 0.0F, 0.0F, -0.7854F, 0.0F));

		chain6.addOrReplaceChild("cube_r38", CubeListBuilder.create().texOffs(24, 20).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.25F, 0.0F, 4.5F, 0.0F, 0.7854F, 0.0F));

		chain6.addOrReplaceChild("cube_r39", CubeListBuilder.create().texOffs(24, 18).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.25F, 0.0F, 4.5F, 0.0F, -0.7854F, 0.0F));

		chain6.addOrReplaceChild("cube_r40", CubeListBuilder.create().texOffs(18, 24).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.25F, 0.0F, 0.0F, 0.0F, 0.7854F, 0.0F));

		chain6.addOrReplaceChild("cube_r41", CubeListBuilder.create().texOffs(16, 18).addBox(-1.0F, -1.0F, -1.0F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.75F, 0.0F, 1.75F, 0.0F, -1.5708F, 0.0F));

		chain6.addOrReplaceChild("cube_r42", CubeListBuilder.create().texOffs(8, 18).addBox(-1.0F, -1.0F, -1.0F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.75F, 0.0F, 1.75F, 0.0F, -1.5708F, 0.0F));

		return LayerDefinition.create(meshdefinition, 32, 32);
	}

}
