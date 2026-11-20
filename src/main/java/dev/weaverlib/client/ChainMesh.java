package dev.weaverlib.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleFunction;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** The supplied six interlocking link models, reused for attacks, tethers and restraints. */
final class ChainMesh {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("weaverlib", "textures/entity/angel_shackles.png");
    private final ModelPart[] links = new ModelPart[6];
    private record Wrist(Object world, long tick, Vec3 position) {}
    private static final Map<UUID, Wrist> WRISTS = new HashMap<>();
    private static final ChainMesh INSTANCE = new ChainMesh();
    private ChainMesh() {
        links[0] = AngelShackleModel.createBodyLayer().bakeRoot().getChild("Shackle").getChild("chain");
        for (int i = 1; i < 6; i++) links[i] = links[i - 1].getChild("chain" + (i + 1));
        for (var link : links) {
            link.x = link.y = link.z = link.xRot = link.yRot = link.zRot = 0;
            link.visible = false;
        }
    }
    static RenderType layer() { return RenderType.entityCutoutNoCull(TEXTURE); }
    static void capture(Player p, int limb, Vec3 anchor) {
        if (limb != (p.getMainArm() == HumanoidArm.RIGHT ? 0 : 1)) return;
        if (WRISTS.size() > 128) WRISTS.clear();
        WRISTS.put(p.getUUID(), new Wrist(p.level(), p.level().getGameTime(), anchor));
    }
    static Vec3 wrist(Player p, float partial) {
        Wrist wrist = WRISTS.get(p.getUUID());
        if (wrist != null && wrist.world == p.level() && p.level().getGameTime() - wrist.tick <= 2) return wrist.position;
        Vec3 forward = p.getViewVector(partial).multiply(1, 0, 1).normalize();
        Vec3 side = forward.cross(new Vec3(0, 1, 0)).scale(p.getMainArm() == HumanoidArm.RIGHT ? .42 : -.42);
        return p.getPosition(partial).add(side).add(0, .80 * p.getScale(), 0);
    }
    static void path(PoseStack pose, VertexConsumer buffer, int light, DoubleFunction<Vec3> curve, int count, double width) {
        count = Math.clamp(count, 1, 180);
        for (int i = 0; i < count; i++) {
            Vec3 a = curve.apply(i / (double) count), b = curve.apply((i + 1) / (double) count);
            Vec3 direction = b.subtract(a);
            double length = direction.length();
            if (length < 1e-5) continue;
            pose.pushPose();
            Vec3 middle = a.lerp(b, .5);
            pose.translate(middle.x, middle.y, middle.z);
            pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 0, 1),
                new Vector3f((float) (direction.x / length), (float) (direction.y / length), (float) (direction.z / length))));
            pose.mulPose(Axis.ZP.rotationDegrees(i % 2 * 90));
            pose.scale((float) (width / .235), (float) (width / .235), (float) (length / .235));
            pose.translate(0, .03125, -.140625);
            var link = INSTANCE.links[i % 6];
            link.visible = true;
            link.render(pose, buffer, light, OverlayTexture.NO_OVERLAY);
            link.visible = false;
            pose.popPose();
        }
    }
    static void tether(PoseStack pose, VertexConsumer buffer, int light, Vec3 start, Vec3 end, double slack) {
        path(pose, buffer, light, t -> start.lerp(end, t).add(0, -Math.sin(Math.PI * t) * slack, 0),
            (int) Math.ceil(start.distanceTo(end) / .14), .14);
    }
    static void whip(PoseStack pose, VertexConsumer buffer, int light, Vec3 start, Vec3 forward, double age, int side) {
        double envelope = dev.weaverlib.angel.ChainCombatRules.swing(age);
        if (envelope <= .001) return;
        Vec3 right = forward.cross(new Vec3(0, 1, 0)).scale(side);
        double angle = Math.clamp((4 - age) / 4, -1, 1) * 1.35;
        Vec3 tip = start.add(forward.scale(Math.cos(angle) * (1 + envelope * 3) * envelope))
            .add(right.scale(Math.sin(angle) * (1 + envelope * 3) * envelope))
            .add(0, -.9 * (1 - envelope) + .18 * envelope, 0);
        path(pose, buffer, light, t -> start.lerp(tip, t)
                .add(right.scale(Math.sin(Math.PI * t) * .24 * envelope))
                .add(forward.scale(Math.sin(Math.PI * t) * .45 * envelope)).add(0, -Math.sin(Math.PI * t) * .18, 0),
            (int) Math.ceil(start.distanceTo(tip) / .14), .14);
    }
}
