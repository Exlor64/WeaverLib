package dev.weaverlib.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.weaverlib.sol.Sol;
import dev.weaverlib.sol.SolCombat;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class SolWings extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final Vec3[] PRIMARIES = {
        new Vec3(0.25, 0.81, 0.22), new Vec3(0.47, 0.76, 0.26),
        new Vec3(0.70, 0.72, 0.29), new Vec3(0.95, 0.62, 0.32),
        new Vec3(1.21, 0.46, 0.34), new Vec3(1.45, 0.28, 0.37),
        new Vec3(1.69, 0.04, 0.41), new Vec3(1.90, -0.28, 0.44),
        new Vec3(2.04, -0.63, 0.47), new Vec3(2.06, -0.98, 0.50)
    };
    private static final Vec3[] LEADING_EDGE = {
        new Vec3(0.02, 0.09, 0.02), new Vec3(0.30, -0.17, 0.06),
        new Vec3(0.65, -0.49, 0.13), new Vec3(1.03, -0.76, 0.21),
        new Vec3(1.45, -0.87, 0.29), new Vec3(1.80, -0.92, 0.39),
        PRIMARIES[9]
    };

    SolWings(PlayerRenderer renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poses, MultiBufferSource buffers, int light, AbstractClientPlayer player,
            float swing, float speed, float tickDelta, float age, float yaw, float pitch) {
        var view = SolCombat.view(player);
        if (!view.unlocked() || player.isInvisible() || !player.isAlive() || player.isSleeping()) {
            return;
        }
        float charge = view.reserve() / (float) Sol.MAX_RESERVE;
        int tint = charge < 0.7F
            ? SolMesh.mix(0x636773, SolMesh.GOLD, charge / 0.7F)
            : SolMesh.mix(SolMesh.GOLD, 0xFFFFFF, (charge - 0.7F) / 0.3F);
        float opacity = Mth.lerp(charge, 0.60F, 0.98F);
        float spread = switch (view.mode()) {
            case OFF -> 25F;
            case RANGED -> 12F;
            case MELEE -> -5F;
        };
        var buffer = buffers.getBuffer(SolMesh.layer());
        poses.pushPose();
        getParentModel().body.translateAndRotate(poses);
        for (int side = -1; side <= 1; side += 2) {
            poses.pushPose();
            poses.translate(side * 0.13F, 0.19F, 0.15F);
            float flutter = Mth.sin(age * 0.13F) * 4 + speed * Mth.sin(swing * 0.67F) * 7;
            poses.mulPose(Axis.YP.rotationDegrees(-(spread + flutter) * side));
            poses.mulPose(Axis.ZP.rotationDegrees(Mth.sin(age * 0.085F) * 2.5F * side));
            poses.scale(side, 1, 1);
            drawWing(buffer, poses.last(), tint, charge, opacity, age);
            poses.popPose();
        }
        poses.popPose();
    }

    private static void drawWing(VertexConsumer buffer, PoseStack.Pose pose, int tint, float charge,
            float opacity, float age) {
        int shadow = SolMesh.mix(tint, 0x3C3541, 0.20F - charge * 0.12F);
        int ridge = SolMesh.mix(tint, 0xFFF8E4, 0.13F + charge * 0.42F);
        for (int i = 1; i < LEADING_EDGE.length; i++) {
            Vec3 a = LEADING_EDGE[i - 1];
            Vec3 b = LEADING_EDGE[i];
            SolMesh.quad(buffer, pose, shadow, opacity * .86F, opacity * .86F, opacity * .48F, opacity * .48F,
                a, b, covertEdge(i), covertEdge(i - 1));
            SolMesh.quad(buffer, pose, ridge, .12F + charge * .32F, .12F + charge * .32F, 0, 0,
                a, b, b.add(-0.07, 0.05, 0.02), a.add(-0.04, 0.06, 0.02));
        }

        for (int i = PRIMARIES.length - 1; i >= 0; i--) {
            float f = i / (float) (PRIMARIES.length - 1);
            Vec3 root = new Vec3(.05 + 1.51 * f, .11 - 1.16 * f + .26 * f * f, .025 + .29 * f);
            Vec3 tip = PRIMARIES[i].add(0, Mth.sin(age * .16F + i * .42F) * .028, 0);
            feather(buffer, pose, root, tip, .111 + .046 * f, tint, shadow, ridge, opacity, charge);
        }

        for (int i = LEADING_EDGE.length - 2; i >= 0; i--) {
            Vec3 a = LEADING_EDGE[i];
            Vec3 b = LEADING_EDGE[i + 1];
            Vec3 root = a.lerp(b, .38).add(-.03, .035, -.012);
            Vec3 tip = root.add(.20 + .047 * i, .39 - .018 * i, .20);
            feather(buffer, pose, root, tip, .106, tint, shadow, ridge, opacity * .85F, charge * .55F);
        }
    }

    private static Vec3 covertEdge(int index) {
        int primary = Math.round(index * (PRIMARIES.length - 1F) / (LEADING_EDGE.length - 1F));
        return LEADING_EDGE[index].lerp(PRIMARIES[primary], .44).add(0, 0, .045);
    }

    private static void feather(VertexConsumer buffer, PoseStack.Pose pose, Vec3 root, Vec3 tip, double width,
            int tint, int shadow, int ridge, float opacity, float glow) {
        Vec3 direction = tip.subtract(root);
        Vec3 side = new Vec3(direction.y, -direction.x, 0).normalize().scale(width);
        Vec3 shoulder = root.lerp(tip, .36).add(.04, -.012, .05);
        Vec3 taper = root.lerp(tip, .76).add(.07, -.028, .037);
        Vec3 left = shoulder.add(side);
        Vec3 right = shoulder.subtract(side);
        Vec3 leftTip = taper.add(side.scale(.62));
        Vec3 rightTip = taper.subtract(side.scale(.62));
        if (glow > 0) {
            SolMesh.quad(buffer, pose, tint, 0, glow * .19F, 0, glow * .17F,
                root, shoulder.add(side.scale(1.40)), tip, shoulder.subtract(side.scale(1.40)));
        }
        SolMesh.quad(buffer, pose, tint, opacity * .73F, opacity * .94F, opacity * .78F, opacity * .95F,
            root, left, leftTip, taper);
        SolMesh.quad(buffer, pose, shadow, opacity * .73F, opacity * .95F, opacity * .68F, opacity * .92F,
            root, shoulder, rightTip, right);
        SolMesh.quad(buffer, pose, tint, opacity * .95F, opacity * .78F, opacity * .26F, opacity * .66F,
            taper, leftTip, tip, rightTip);
        SolMesh.quad(buffer, pose, ridge, opacity * .43F, opacity * .90F, opacity * .36F, opacity * .50F,
            root, shoulder.add(side.scale(.16)), tip, taper.subtract(side.scale(.12)));
    }
}
