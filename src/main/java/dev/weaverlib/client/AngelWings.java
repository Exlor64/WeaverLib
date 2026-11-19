package dev.weaverlib.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.weaverlib.angel.Angels;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class AngelWings extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private final java.util.Map<java.util.UUID, Guard> guards = new java.util.HashMap<>();

    private record Guard(float amount, double lastTime) {}

    AngelWings(PlayerRenderer renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
            float swing, float speed, float partial, float age, float yaw, float pitch) {
        var view = Angels.view(player);
        if (!view.active() || !player.isAlive() || player.isInvisible()) return;
        double time = player.level().getGameTime() + partial;
        float flap = flap(player, age);
        float desired = view.defending() && !Angels.flying(player) ? 1 : 0;
        Guard previous = guards.get(player.getUUID());
        double elapsed = previous == null ? 1 : Math.clamp(time - previous.lastTime(), 0, 5);
        float guard = previous == null ? 0 : previous.amount();
        guard = Mth.lerp((float) Math.min(1, elapsed / 3), guard, desired);
        if (guards.size() > 128) guards.clear();
        guards.put(player.getUUID(), new Guard(guard, time));
        float attack = (float) ((time - view.swing()) / 12.0);
        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        for (int pair = 0; pair < view.rank(); pair++) {
            for (int side = -1; side <= 1; side += 2) {
                pose.pushPose();
                pose.translate(side * .15, .19 + pair * .24, .17);
                if (pair > 0) pose.mulPose(Axis.ZP.rotationDegrees(side * (pair == 1 ? 25 : -24)));
                pose.scale(1 - pair * .11F, 1 - pair * .11F, 1);
                float slash = view.wing() == pair * 2 + (side > 0 ? 1 : 0)
                    && attack >= 0 && attack < 1 ? Mth.sin(attack * Mth.PI) : 0;
                pose.mulPose(Axis.YP.rotationDegrees(side * (-14 + guard * 109 + slash * 128)));
                pose.mulPose(Axis.ZP.rotationDegrees(side * (flap * (1 - guard) - guard * 25 - slash * 14)));
                pose.mulPose(Axis.XP.rotationDegrees(guard * 22));
                pose.scale(side, 1, 1);
                float fold = Math.max(0, -flap) / 45;
                geometry(buffers.getBuffer(AngelMesh.layer()), pose, age, fold, guard);
                pose.popPose();
            }
        }
        pose.popPose();
    }

    private static float flap(AbstractClientPlayer player, float age) {
        var view = Angels.view(player);
        double vertical = player.getDeltaMovement().y;
        if (!Angels.flying(player)) return Mth.sin(age * .09F) * 3;
        if (!view.fast()) return Mth.sin(age * .65F) * 32 + (float) Math.clamp(-vertical * 12, -8, 8);
        double pitch = player.getXRot();
        double speed = player.getDeltaMovement().length();
        double effort = Math.clamp((pitch < 0 ? -pitch / 55 : 0) + (1.05 - speed) * 1.6, .08, 1);
        return (float) (Mth.sin(age * (.35F + (float) effort * .27F)) * (8 + effort * 27) - 10);
    }

    static void geometry(VertexConsumer buffer, PoseStack pose, float age, float fold, float guard) {
        // The shoulder drives the upper wing. The elbow folds the forewing on the recovery stroke.
        feather(buffer, pose.last(), new Vec3(0, 0, 0), new Vec3(.86, -.35, .08), .16, 0xF3BA59);
        for (int i = 0; i < 7; i++) {
            double f = i / 6.0;
            Vec3 root = new Vec3(.07 + f * .67, -.03 - f * .29, .035);
            Vec3 tip = root.add(.04 + .13 * f, .59 + .16 * f, .10);
            feather(buffer, pose.last(), root, tip, .115, i % 2 == 0 ? 0xFFF0B8 : 0xF4D482);
        }
        pose.pushPose();
        pose.translate(.78, -.31, .07);
        pose.mulPose(Axis.YP.rotationDegrees(-fold * 38));
        pose.mulPose(Axis.ZP.rotationDegrees(-fold * 18 + Mth.sin(age * .13F) * 2));
        pose.pushPose();
        pose.translate(.30, -.12, .025);
        pose.mulPose(Axis.YP.rotationDegrees(-fold * 21));
        pose.mulPose(Axis.ZP.rotationDegrees(fold * 12));
        for (int i = 0; i < 9; i++) {
            double f = i / 8.0;
            Vec3 root = new Vec3(f * .70 - .30, -f * .27 + .12, f * .10 - .025);
            Vec3 tip = root.add(.28 + f * .28, .72 - f * .45, .10 + f * .08);
            feather(buffer, pose.last(), root, tip, .12 - f * .035, i % 2 == 0 ? 0xFFF6D1 : 0xFBDD99);
        }
        pose.popPose();
        for (int i = 0; i < 8; i++) {
            double f = i / 7.0;
            Vec3 root = new Vec3(f * .80, -f * .31, -.02);
            feather(buffer, pose.last(), root, root.add(.23, .34 - f * .12, .12), .09, 0xFFE7A6);
        }
        pose.popPose();
    }

    private static void feather(VertexConsumer buffer, PoseStack.Pose pose, Vec3 root, Vec3 tip,
            double width, int color) {
        Vec3 direction = tip.subtract(root);
        Vec3 across = new Vec3(direction.y, -direction.x, 0).normalize().scale(width);
        Vec3 middle = root.lerp(tip, .38).add(0, 0, .04);
        Vec3 taper = root.lerp(tip, .78);
        Vec3 left = middle.add(across);
        Vec3 right = middle.subtract(across);
        Vec3 leftTip = taper.add(across.scale(.56));
        Vec3 rightTip = taper.subtract(across.scale(.56));
        AngelMesh.quad(buffer, pose, 0xFFD16B, 0, .16F, 0, .16F,
            root, middle.add(across.scale(1.38)), tip, middle.subtract(across.scale(1.38)));
        AngelMesh.quad(buffer, pose, color, .89F, .96F, .85F, .94F, root, left, leftTip, taper);
        AngelMesh.quad(buffer, pose, AngelMesh.mix(color, 0xC58437, .18F),
            .89F, .94F, .86F, .92F, root, middle, rightTip, right);
        AngelMesh.quad(buffer, pose, color, .96F, .85F, .51F, .85F, taper, leftTip, tip, rightTip);
        AngelMesh.quad(buffer, pose, 0xFFF9E8, .55F, .82F, .43F, .66F,
            root, middle.add(across.scale(.12)), tip, taper.subtract(across.scale(.10)));
    }
}
