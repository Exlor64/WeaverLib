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
    private static final Vec3[] TIPS = {
        new Vec3(0.29, 0.60, 0.28), new Vec3(0.47, 0.50, 0.32),
        new Vec3(0.68, 0.39, 0.34), new Vec3(0.88, 0.25, 0.36),
        new Vec3(1.08, 0.12, 0.38), new Vec3(1.29, -0.02, 0.41),
        new Vec3(1.49, -0.20, 0.43), new Vec3(1.68, -0.43, 0.45),
        new Vec3(1.83, -0.73, 0.47), new Vec3(1.87, -0.98, 0.48)
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
        float opacity = Mth.lerp(charge, 0.52F, 0.97F);
        float spread = switch (view.mode()) {
            case OFF -> 25F;
            case RANGED -> 12F;
            case MELEE -> -5F;
        };
        var buffer = buffers.getBuffer(SolMesh.layer());
        poses.pushPose();
        getParentModel().body.translateAndRotate(poses);
        for (int side : new int[] {-1, 1}) {
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
        for (int i = TIPS.length - 1; i >= 0; i--) {
            float fraction = i / (float) (TIPS.length - 1);
            Vec3 root = new Vec3(0.06 + 1.18 * fraction, 0.17 - 0.89 * fraction + 0.14 * fraction * fraction,
                0.025 + 0.29 * fraction);
            Vec3 tip = TIPS[i].add(0, Mth.sin(age * 0.17F + i * 0.45F) * 0.025 * fraction, 0);
            Vec3 middle = root.lerp(tip, 0.50).add(0.065, -0.02, 0.04);
            double width = 0.055 + 0.050 * fraction;
            Vec3 spread = new Vec3(width, width * 0.60, 0.02);
            Vec3 edgeLight = spread.scale(1.55);
            if (charge > 0) {
                SolMesh.quad(buffer, pose, tint, 0, charge * 0.24F, 0, charge * 0.24F,
                    root, middle.add(edgeLight), tip, middle.subtract(edgeLight));
            }
            SolMesh.quad(buffer, pose, tint, opacity * 0.88F, opacity * 0.60F, opacity * 0.08F,
                opacity * 0.82F, root, middle.add(spread), tip, middle.subtract(spread));
            Vec3 rim = spread.scale(0.21).add(0, 0, 0.008);
            int spine = SolMesh.mix(tint, 0xFFFFFF, charge * 0.60F);
            SolMesh.quad(buffer, pose, spine, opacity, opacity * 0.7F, opacity * 0.26F, opacity * 0.6F,
                root, middle.add(rim), tip, middle.subtract(rim));
        }
    }
}
