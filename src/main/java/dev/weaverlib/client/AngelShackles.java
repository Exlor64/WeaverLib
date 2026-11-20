package dev.weaverlib.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.weaverlib.angel.Angels;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import dev.weaverlib.client.ShackleChainPhysics.Point;

/** Four independent, world-space chain simulations attached to the actual animated player limbs. */
final class AngelShackles extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("weaverlib", "textures/entity/angel_shackles.png");
    private final ModelPart cuff;
    private final ModelPart[] links = new ModelPart[6];
    private final Map<UUID, State> states = new HashMap<>();
    private Object world;

    private static final class State {
        final ShackleChainPhysics[] chains = new ShackleChainPhysics[4];
        long tick = Long.MIN_VALUE;
        final double scale;
        List<AABB> blocks = List.of();
        State(double scale) {
            this.scale = scale;
            for (int i = 0; i < 4; i++) chains[i] = new ShackleChainPhysics((i < 2 ? .165 : .135) * scale);
        }
    }
    private record Capsule(Point a, Point b, double radius) {}

    AngelShackles(PlayerRenderer renderer) {
        super(renderer);
        cuff = AngelShackleModel.createBodyLayer().bakeRoot().getChild("Shackle");
        cuff.y = 0;
        links[0] = cuff.getChild("chain");
        for (int i = 1; i < links.length; i++) links[i] = links[i - 1].getChild("chain" + (i + 1));
        for (var link : links) {
            link.x = link.y = link.z = 0;
            link.xRot = link.yRot = link.zRot = 0;
            link.visible = false;
        }
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
            float swing, float speed, float partial, float age, float yaw, float pitch) {
        if (world != player.level()) { states.clear(); world = player.level(); }
        if (!Angels.view(player).active() || !Angels.retribution(player) || !player.isAlive() || player.isInvisible()) {
            states.remove(player.getUUID());
            return;
        }
        if (states.size() > 128) states.clear();
        State state = states.computeIfAbsent(player.getUUID(), id -> new State(player.getScale()));
        if (state.scale != player.getScale()) { state = new State(player.getScale()); states.put(player.getUUID(), state); }
        final State current = state;
        boolean live = PyraWingFire.inWorldPass();
        double time = player.level().getGameTime() + partial;
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        var model = getParentModel();
        ModelPart[] limbs = {model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
        Point[] anchors = new Point[4];
        boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
        boolean armor = !player.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
        boolean boots = !player.getItemBySlot(EquipmentSlot.FEET).isEmpty();
        List<Capsule> body = new ArrayList<>(6);
        for (int i = 0; i < 4; i++) {
            boolean arm = i < 2;
            double x = arm ? (i == 0 ? -1 : 1) * (slim ? .5 : 1) / 16.0 : 0;
            pose.pushPose();
            limbs[i].translateAndRotate(pose);
            body.add(new Capsule(point(pose, camera, x, .04, 0), point(pose, camera, x, .70, 0),
                (arm ? armor ? .205 : .145 : boots ? .205 : .15) * player.getScale()));
            pose.translate(x, .74, 0);
            pose.mulPose(Axis.YP.rotationDegrees(i % 2 == 0 ? -90 : 90));
            double width = arm ? armor ? 1.6 : slim ? 1.04 : 1.18 : boots ? 1.6 : 1.18;
            pose.scale((float) width, 1, (float) (arm && !armor && slim ? 1.18 : width));
            cuff.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, OverlayTexture.NO_OVERLAY);
            anchors[i] = point(pose, camera, 0, -.0625, .31);
            if (live && arm) ChainMesh.capture(player, i, new Vec3(anchors[i].x(), anchors[i].y(), anchors[i].z()));
            if (!live) {
                // Inventory/entity previews are in GUI space, so they must not feed the live world solver.
                pose.translate(0, -.0625, .31);
                pose.mulPose(Axis.XP.rotationDegrees(-90));
                for (int n = 0; n < 6; n++) {
                    pose.pushPose();
                    pose.translate(0, 0, (n + .5) * (arm ? .14 : .115));
                    drawLink(links[n], pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),
                        light, arm ? .14 : .115, arm ? .14 : .115, n);
                    pose.popPose();
                }
            }
            pose.popPose();
        }
        if (!live) return;
        pose.pushPose();
        model.body.translateAndRotate(pose);
        body.add(new Capsule(point(pose, camera, 0, .08, 0), point(pose, camera, 0, .65, 0), .26 * player.getScale()));
        pose.popPose();
        pose.pushPose();
        model.head.translateAndRotate(pose);
        Point head = point(pose, camera, 0, -.25, 0);
        body.add(new Capsule(head, head, .27 * player.getScale()));
        pose.popPose();
        if (state.tick != player.level().getGameTime()) {
            state.tick = player.level().getGameTime();
            List<AABB> blocks = new ArrayList<>();
            for (var shape : player.level().getBlockCollisions(player, player.getBoundingBox().inflate(1.8 * player.getScale())))
                blocks.addAll(shape.toAabbs());
            state.blocks = blocks;
        }
        ShackleChainPhysics.Collision collision = (p, radius) -> {
            for (var capsule : body) p = ShackleChainPhysics.outsideCapsule(p, capsule.a, capsule.b, capsule.radius + radius);
            for (var block : current.blocks) p = outsideBox(p, block.inflate(radius));
            return p;
        };
        // In still world air, movement creates relative drag naturally; no looping sine animation.
        Point air = new Point(0, 0, 0);
        var buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        for (int i = 0; i < 4; i++) {
            var chain = state.chains[i];
            chain.advance(time, anchors[i], air, collision);
            double swingAge = time - dev.weaverlib.angel.RetributionCombat.swing(player);
            boolean mainWrist = i == (player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? 0 : 1);
            double envelope = dev.weaverlib.angel.ChainCombatRules.swing(swingAge);
            if (mainWrist && envelope > .001) {
                Vec3 start = new Vec3(anchors[i].x(), anchors[i].y(), anchors[i].z()).subtract(camera);
                ChainMesh.whip(new PoseStack(), buffers.getBuffer(ChainMesh.layer()), light, start,
                    Vec3.directionFromRotation(0, player.getViewYRot(partial)), swingAge, i == 0 ? 1 : -1);
                continue;
            }
            for (int n = 0; n < ShackleChainPhysics.LINKS; n++) {
                Point a = chain.point(n), b = chain.point(n + 1);
                if (a == null || b == null) continue;
                Point delta = b.subtract(a);
                double length = delta.length();
                if (length < 1e-6) continue;
                // Undo the player's limb/body transforms: chains keep world gravity during rolls and dives.
                PoseStack chainPose = new PoseStack();
                chainPose.translate((a.x() + b.x()) * .5 - camera.x, (a.y() + b.y()) * .5 - camera.y,
                    (a.z() + b.z()) * .5 - camera.z);
                chainPose.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 0, 1),
                    new Vector3f((float) (delta.x() / length), (float) (delta.y() / length), (float) (delta.z() / length))));
                drawLink(links[n], chainPose, buffer, light, chain.linkLength(), length, n);
            }
        }
        states.keySet().removeIf(id -> player.level().getPlayerByUUID(id) == null);
    }

    private static void drawLink(ModelPart link, PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer buffer,
            int light, double width, double length, int n) {
        pose.mulPose(Axis.ZP.rotationDegrees(n % 2 * 90));
        pose.scale((float) (width / .235), (float) (width / .235), (float) (length / .235));
        pose.translate(0, .03125, -.140625);
        link.visible = true;
        link.render(pose, buffer, light, OverlayTexture.NO_OVERLAY);
        link.visible = false;
    }

    private static Point point(PoseStack pose, Vec3 camera, double x, double y, double z) {
        Vector3f p = pose.last().pose().transformPosition((float) x, (float) y, (float) z, new Vector3f());
        return new Point(p.x + camera.x, p.y + camera.y, p.z + camera.z);
    }

    private static Point outsideBox(Point p, AABB b) {
        if (p.x() <= b.minX || p.x() >= b.maxX || p.y() <= b.minY || p.y() >= b.maxY
                || p.z() <= b.minZ || p.z() >= b.maxZ) return p;
        double[] distance = {p.x() - b.minX, b.maxX - p.x(), p.y() - b.minY, b.maxY - p.y(), p.z() - b.minZ, b.maxZ - p.z()};
        int face = 0;
        for (int i = 1; i < 6; i++) if (distance[i] < distance[face]) face = i;
        return switch (face) {
            case 0 -> new Point(b.minX, p.y(), p.z());
            case 1 -> new Point(b.maxX, p.y(), p.z());
            case 2 -> new Point(p.x(), b.minY, p.z());
            case 3 -> new Point(p.x(), b.maxY, p.z());
            case 4 -> new Point(p.x(), p.y(), b.minZ);
            default -> new Point(p.x(), p.y(), b.maxZ);
        };
    }
}
