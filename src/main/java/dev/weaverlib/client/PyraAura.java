package dev.weaverlib.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;

/** A close-fitting, low-opacity flame skin, transformed separately with every animated body part. */
final class PyraAura {
    private PyraAura() {}

    static void render(VertexConsumer buffer, PoseStack pose, PlayerModel<AbstractClientPlayer> model,
            AbstractClientPlayer player, double strength) {
        double alpha = .27 * strength;
        boolean slim = player.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM;
        part(buffer, pose, model.head, -4, -8, -4, 4, 0, 4,
            pad(player, EquipmentSlot.HEAD), alpha);
        part(buffer, pose, model.body, -4, 0, -2, 4, 12, 2,
            pad(player, EquipmentSlot.CHEST), alpha);
        part(buffer, pose, model.rightArm, slim ? -2 : -3, -2, -2,
            1, 10, 2, pad(player, EquipmentSlot.CHEST), alpha);
        part(buffer, pose, model.leftArm, -1, -2, -2,
            slim ? 2 : 3, 10, 2, pad(player, EquipmentSlot.CHEST), alpha);
        part(buffer, pose, model.rightLeg, -2, 0, -2, 2, 12, 2,
            pad(player, EquipmentSlot.LEGS), alpha);
        part(buffer, pose, model.leftLeg, -2, 0, -2, 2, 12, 2,
            pad(player, EquipmentSlot.LEGS), alpha);
    }

    private static double pad(AbstractClientPlayer player, EquipmentSlot slot) {
        if (slot == EquipmentSlot.LEGS && !player.getItemBySlot(EquipmentSlot.FEET).isEmpty()) return .065;
        return player.getItemBySlot(slot).isEmpty() ? slot == EquipmentSlot.HEAD ? .04 : .025 : slot == EquipmentSlot.HEAD ? .075 : .070;
    }

    private static void part(VertexConsumer buffer, PoseStack pose, ModelPart part,
            double x0, double y0, double z0, double x1, double y1, double z1, double pad, double alpha) {
        if (!part.visible || part.skipDraw) return;
        pose.pushPose();
        part.translateAndRotate(pose);
        box(buffer, pose.last(), x0 / 16 - pad, y0 / 16 - pad, z0 / 16 - pad,
            x1 / 16 + pad, y1 / 16 + pad, z1 / 16 + pad, alpha);
        pose.popPose();
    }

    private static void box(VertexConsumer b, PoseStack.Pose pose,
            double x0, double y0, double z0, double x1, double y1, double z1, double alpha) {
        Vec3 a = new Vec3(x0,y1,z0), c = new Vec3(x1,y1,z0), d = new Vec3(x1,y0,z0), e = new Vec3(x0,y0,z0);
        Vec3 f = new Vec3(x0,y1,z1), g = new Vec3(x1,y1,z1), h = new Vec3(x1,y0,z1), i = new Vec3(x0,y0,z1);
        // UV grows upward on each limb; the material carries sparse flowing red wisps, not a solid shell.
        PyraFire.quad(b,pose,PyraFire.AURA,a,c,d,e,0,0,1,1,alpha,alpha);
        PyraFire.quad(b,pose,PyraFire.AURA,g,f,i,h,0,0,1,1,alpha,alpha);
        PyraFire.quad(b,pose,PyraFire.AURA,f,a,e,i,0,0,1,1,alpha,alpha);
        PyraFire.quad(b,pose,PyraFire.AURA,c,g,h,d,0,0,1,1,alpha,alpha);
        PyraFire.quad(b,pose,PyraFire.AURA,e,d,h,i,0,0,1,1,alpha*.65,alpha*.65);
        PyraFire.quad(b,pose,PyraFire.AURA,f,g,c,a,0,0,1,1,alpha*.65,alpha*.65);
    }
}
