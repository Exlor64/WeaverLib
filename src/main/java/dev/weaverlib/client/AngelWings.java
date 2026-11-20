package dev.weaverlib.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.weaverlib.angel.Angels;
import dev.weaverlib.angel.PyraCombat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.phys.Vec3;

final class AngelWings extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private final Map<UUID, Motion> motions = new HashMap<>();
    private ClientLevel world;

    private static final class Motion {
        double lastTime, phase, flight, fast, effort = .7, guard, previousSpeed, vertical, dive, dash, gather;
        double previousY;
        final AngelGroundMotion ground = new AngelGroundMotion();
        boolean hasPosition;
        long lastBirthParticle = Long.MIN_VALUE;
        Motion(double time) { lastTime = time; }
    }

    AngelWings(PlayerRenderer renderer) { super(renderer); }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
            float swing, float speed, float partial, float age, float yaw, float pitch) {
        var view = Angels.view(player);
        if (!view.active() || !player.isAlive() || player.isInvisible()) {
            motions.remove(player.getUUID());
            return;
        }
        if (world != player.level()) {
            motions.clear();
            world = (ClientLevel) player.level();
        }
        double time = player.level().getGameTime() + partial;
        if (motions.size() > 128) motions.clear();
        Motion m = motions.computeIfAbsent(player.getUUID(), id -> new Motion(time));
        double elapsed = Math.clamp(time - m.lastTime, 0, 3);
        m.lastTime = time;
        double velocity = player.getDeltaMovement().length();
        double acceleration = elapsed > .001 ? (velocity - m.previousSpeed) / elapsed : 0;
        m.previousSpeed = velocity;
        boolean flying = Angels.flying(player);
        double renderY = player.getPosition(partial).y;
        double verticalSpeed = player.getDeltaMovement().y;
        if (player != Minecraft.getInstance().player && m.hasPosition && elapsed > .001) {
            double displacement = renderY - m.previousY;
            if (Math.abs(displacement) <= 3) verticalSpeed = displacement / elapsed;
        }
        m.previousY = renderY;
        m.hasPosition = true;
        var activity = Angels.wingMotion(player);
        double attack = (time - view.swing()) / 12;
        boolean combat = view.defending() || Angels.charge(player).active() || attack >= 0 && attack < 1;
        boolean airborne = !player.onGround() && !player.isInWater() && !player.isInLava()
            && !player.onClimbable() && !player.isPassenger();
        var ground = m.ground.sample(time, activity.activity(), activity.jumped(), player.onGround(),
            airborne, flying, combat, verticalSpeed);
        double effort = view.fast() ? Math.clamp(.18 + Math.max(0, -player.getXRot()) / 65
            + Math.max(0, 1.15 - velocity) * .65 + Math.max(0, acceleration) * 5, .08, 1)
            : flying ? .88 : ground.effort();
        double blend = 1 - Math.exp(-elapsed / 4);
        m.effort += (effort - m.effort) * blend;
        m.vertical += (verticalSpeed - m.vertical) * blend;
        m.flight += ((flying ? 1 : 0) - m.flight) * blend;
        m.fast = AngelFlightPose.sample(player, partial).blend();
        double diving = view.fast() ? AngelWingMotion.diveAmount(player.getViewXRot(partial), player.getDeltaMovement().y) : 0;
        m.dive += (diving - m.dive) * blend;
        m.dash += ((view.fast() && view.spin() > time ? 1 : 0) - m.dash) * (1 - Math.exp(-elapsed / 2.5));
        m.guard += ((view.defending() && !flying && !Angels.inferno(player) ? 1 : 0) - m.guard) * (1 - Math.exp(-elapsed / 2.2));
        // Integrating phase prevents animation jumps when speed or flight mode changes.
        double hoverRate = AngelWingMotion.hoverRate(m.vertical);
        double flightRate = hoverRate + m.fast * (.29 + m.effort * .13 - hoverRate);
        double groundRate = .075 + ground.airborne() * (ground.rate() - .075);
        if (ground.takeoff()) m.phase = 0;
        m.phase = (m.phase + elapsed * (groundRate + m.flight * (flightRate - groundRate))) % Math.TAU;
        double wingFlight = m.flight + ground.airborne() * (1 - m.flight);
        double resting = ground.folded() * (1 - ground.airborne()) * (1 - m.flight);
        var formation = Angels.formation(player);
        long tick = player.level().getGameTime();
        boolean emitBirth = m.lastBirthParticle != tick && tick % (formation.dim() ? 5 : 2) == 0;
        boolean fire = Angels.inferno(player);
        var aerialCharge = Angels.charge(player);
        if (fire && flying && !view.fast() && aerialCharge.active() && aerialCharge.fireball()) {
            m.gather = Math.clamp(time - aerialCharge.started(), 0, 18);
        } else m.gather = Math.max(0, m.gather - elapsed * 3);
        // Draw all depth-writing feathers before their color-only fringe and fire overlays.
        // Each pass uses the same skeleton, without reacquiring or flushing a buffer mid-wing.
        for (int pass = 0; pass < (fire ? 3 : 2); pass++) {
            var buffer = buffers.getBuffer(pass == 0 ? AngelMesh.wingLayer()
                : pass == 1 ? AngelMesh.layer() : PyraFire.layer());
            if (pass == 2) {
                double strength = formation.started() < 0 ? 1
                    : AngelWingMotion.smooth((time - formation.started()) / 18);
                PyraAura.render(buffer, pose, getParentModel(), player, strength * (formation.dim() ? .65 : 1));
            }
            pose.pushPose();
            getParentModel().body.translateAndRotate(pose);
            for (int pair = 0; pair < view.rank(); pair++) {
                for (int side = -1; side <= 1; side += 2) {
                    double strike = view.wing() == pair * 2 + (side > 0 ? 1 : 0) ? attack : -1;
                    var normal = AngelWingMotion.sample(m.phase, wingFlight, m.fast, m.effort, m.guard, strike, pair, side);
                    normal = AngelWingMotion.resting(normal, resting, m.phase, pair);
                    normal = AngelWingMotion.aerial(normal, m.dive, m.dash, pair, side);
                    var charge = Angels.charge(player);
                    if (fire && flying && !view.fast()) {
                        if (strike >= 0) normal = PyraWingMotion.slash(normal, strike);
                        if (m.gather > 0) normal = PyraWingMotion.gather(normal,
                            m.gather, pair, side, yaw, player.getViewXRot(partial));
                    }
                    double growth = formation.started() < 0 ? 1
                        : AngelWingMotion.birthProgress(time - formation.started(), pair);
                    var joints = AngelWingMotion.growing(normal, growth);
                    pose.pushPose();
                    pose.scale(side, 1, 1);
                    long pattern = AngelFeathers.seed(player.getUUID().getMostSignificantBits(),
                        player.getUUID().getLeastSignificantBits(), pair, side);
                    if (pass == 0) geometry(buffer, pose, joints, fire);
                    else if (pass == 1) glowGeometry(buffer, pose, joints, fire);
                    else {
                        PyraWings.geometry(buffer, pose, joints, time + pair * 11, pattern);
                        if (charge.active() && charge.fireball() && PyraWingFire.inWorldPass()) {
                            var tip = joints.surface(1, 0);
                            double merge = AngelWingMotion.smooth((time - charge.started() - 10) / 8);
                            double radius = PyraCombat.fireballOrb(charge.energy()) / Math.sqrt(view.rank() * 2);
                            Vec3 at = new Vec3(tip.x(), tip.y(), tip.z());
                            PyraBeamVisual.sphere(buffer, pose.last(), at, radius, time, PyraFire.WING, .7 * (1 - merge));
                            PyraBeamVisual.sphere(buffer, pose.last(), at, radius * .55, time, PyraFire.CORE, .9 * (1 - merge));
                        }
                        if (PyraWingFire.inWorldPass()) {
                            PyraWingFire.capture(player, pose, joints, pattern, pair, side, false);
                            PyraTrails.sample(player, pose, joints, time, pair, side);
                            if (strike >= 0) PyraSlashes.sample(player, pose, joints, time, strike);
                        }
                    }
                    if (pass == 1 && !fire && growth < 1) {
                        birthGlow(buffer, pose, joints, growth, formation.dim());
                        if (emitBirth && growth > .02) birthParticles(pose, player, joints, growth);
                    }
                    pose.popPose();
                }
            }
            pose.popPose();
        }
        if (emitBirth) m.lastBirthParticle = tick;
    }

    private static void birthGlow(VertexConsumer buffer, PoseStack pose, AngelWingMotion.Joints joints,
            double growth, boolean dim) {
        var p = joints.root();
        Vec3 root = new Vec3(p.x(), p.y(), p.z() + .035);
        float alpha = (float) ((dim ? .24 : .88) * (1 - AngelWingMotion.smooth((growth - .72) / .28)));
        double radius = .065 + Math.sin(growth * Math.PI) * .075;
        for (int i = 0; i < 8; i++) {
            double a = i * Math.TAU / 8, b = (i + 1) * Math.TAU / 8;
            Vec3 edgeA = root.add(Math.cos(a) * radius, Math.sin(a) * radius, 0);
            Vec3 edgeB = root.add(Math.cos(b) * radius, Math.sin(b) * radius, 0);
            AngelMesh.quad(buffer, pose.last(), 0xFFCB65, alpha, 0, 0, alpha,
                root, edgeA, edgeB, root);
        }
        AngelMesh.quad(buffer, pose.last(), 0xFFF8D4, 0, alpha, 0, alpha,
            root.add(-radius * .36, 0, .002), root.add(0, -radius * .65, .002),
            root.add(radius * .36, 0, .002), root.add(0, radius * .65, .002));
    }

    private static void birthParticles(PoseStack pose, AbstractClientPlayer player,
            AngelWingMotion.Joints joints, double growth) {
        var client = Minecraft.getInstance();
        Vec3 camera = client.gameRenderer.getMainCamera().getPosition();
        if (player.distanceToSqr(camera) > 64 * 64) return;
        var local = joints.surface(Math.clamp(growth, .04, .98), .08);
        var transformed = pose.last().pose().transformPosition((float) local.x(), (float) local.y(),
            (float) local.z(), new org.joml.Vector3f());
        Vec3 at = new Vec3(transformed.x, transformed.y, transformed.z).add(camera);
        var random = player.getRandom();
        player.level().addParticle(ParticleTypes.FLAME,
            at.x + (random.nextDouble() - .5) * .06, at.y, at.z + (random.nextDouble() - .5) * .06,
            (random.nextDouble() - .5) * .016, .012, (random.nextDouble() - .5) * .016);
        player.level().addParticle(ParticleTypes.END_ROD, at.x, at.y, at.z, 0, .006, 0);
    }

    static void geometry(VertexConsumer buffer, PoseStack pose, AngelWingMotion.Joints joints) {
        geometry(buffer, pose, joints, false);
    }

    static void geometry(VertexConsumer buffer, PoseStack pose, AngelWingMotion.Joints joints, boolean pyra) {
        for (var feather : AngelFeathers.ALL) {
            feather(buffer, pose.last(), joints, feather,
                pyra ? AngelFeathers.pyraColor(feather) : feather.normalColor(), pyra, false);
        }
    }

    static void glowGeometry(VertexConsumer buffer, PoseStack pose, AngelWingMotion.Joints joints, boolean pyra) {
        for (var feather : AngelFeathers.ALL) {
            feather(buffer, pose.last(), joints, feather,
                pyra ? AngelFeathers.pyraColor(feather) : feather.normalColor(), pyra, true);
        }
    }

    private static Vec3 featherPoint(AngelWingMotion.Joints joints, AngelFeathers.Feather feather,
            double along, double offset, double ridge) {
        var p = AngelFeathers.point(joints, feather, along, offset);
        return new Vec3(p.x(), p.y(), p.z() - ridge);
    }

    private static void feather(VertexConsumer buffer, PoseStack.Pose pose, AngelWingMotion.Joints joints,
            AngelFeathers.Feather feather, int color, boolean pyra, boolean glow) {
        double forming = AngelFeathers.growth(joints, feather);
        if (forming <= .001) return;
        float alpha = (float) Math.min(1, forming * 2);
        int quill = pyra ? 0xFFCD60 : 0xFFFBEA;
        int fringe = pyra ? 0xDE4A2C : 0xFFB63E;
        if (pyra) {
            color = AngelMesh.mix(0, color, .70F);
            fringe = AngelMesh.mix(0, fringe, .65F);
        }
        float fringeRoot = pyra ? .08F : .20F, fringeTip = pyra ? .06F : .16F;
        for (int band = 0; band < 5; band++) {
            double a = band / 5.0, b = (band + 1) / 5.0;
            double wA = AngelFeathers.width(joints, feather, a), wB = AngelFeathers.width(joints, feather, b);
            Vec3 leftA = featherPoint(joints, feather, a, -wA, .012);
            Vec3 rightA = featherPoint(joints, feather, a, wA, .012);
            Vec3 leftB = featherPoint(joints, feather, b, -wB, .012);
            Vec3 rightB = featherPoint(joints, feather, b, wB, .012);
            if (!glow) {
                float body = (pyra ? .97F : .92F) * alpha, tip = (pyra ? .91F : .84F) * alpha;
                AngelMesh.quad(buffer, pose, color, body, body, tip, tip, leftA, rightA, rightB, leftB);
                if (pyra) {
                    for (int face : new int[]{-1, 1}) {
                        AngelMesh.quad(buffer, pose, quill, .94F * alpha, .94F * alpha, .80F * alpha, .80F * alpha,
                            veinPoint(joints, feather, a, -.004, face, .016), veinPoint(joints, feather, a, .004, face, .016),
                            veinPoint(joints, feather, b, .003, face, .016), veinPoint(joints, feather, b, -.003, face, .016));
                        AngelMesh.quad(buffer, pose, 0xFFF1B3, .94F * alpha, .94F * alpha, .80F * alpha, .80F * alpha,
                            veinPoint(joints, feather, a, -.0015, face, .021), veinPoint(joints, feather, a, .0015, face, .021),
                            veinPoint(joints, feather, b, .0012, face, .021), veinPoint(joints, feather, b, -.0012, face, .021));
                    }
                } else AngelMesh.quad(buffer, pose, quill, .72F * alpha, .72F * alpha, .48F * alpha, .48F * alpha,
                    featherPoint(joints, feather, a, -.004, .025), featherPoint(joints, feather, a, .004, .025),
                    featherPoint(joints, feather, b, .003, .025), featherPoint(joints, feather, b, -.003, .025));
            }
            if (glow) {
                AngelMesh.quad(buffer, pose, fringe, fringeRoot * alpha, 0, 0, fringeTip * alpha,
                    leftA, featherPoint(joints, feather, a, -wA - .016, .006),
                    featherPoint(joints, feather, b, -wB - .012, .006), leftB);
                AngelMesh.quad(buffer, pose, fringe, fringeRoot * alpha, 0, 0, fringeTip * alpha,
                    rightA, featherPoint(joints, feather, a, wA + .016, .006),
                    featherPoint(joints, feather, b, wB + .012, .006), rightB);
            }
        }
        if (pyra && !glow) veins(buffer, pose, joints, feather, forming, alpha);
    }

    private static Vec3 veinPoint(AngelWingMotion.Joints joints, AngelFeathers.Feather feather,
            double along, double offset, int face, double lift) {
        var p = AngelFeathers.point(joints, feather, along, offset);
        var n = AngelFeathers.normal(joints, feather, along);
        return new Vec3(p.x(), p.y(), p.z() - .012)
            .add(n.x() * face * lift, n.y() * face * lift, n.z() * face * lift);
    }

    /** Branching gold barbs stay on each vane, including during folding and the twisting melee strike. */
    private static void veins(VertexConsumer buffer, PoseStack.Pose pose, AngelWingMotion.Joints joints,
            AngelFeathers.Feather feather, double forming, float alpha) {
        int pairs = feather.covert() ? 2 : 4;
        for (int branch = 0; branch < pairs; branch++) {
            double root = .15 + branch * (.64 / Math.max(1, pairs - 1));
            for (int side : new int[]{-1, 1}) {
                for (int segment = 0; segment < 3; segment++) {
                    double a = segment / 3.0, b = (segment + 1) / 3.0;
                    double ta = root + a * .12, tb = root + b * .12;
                    double ua = side * AngelFeathers.width(joints, feather, ta) * .87 * Math.pow(a, .8);
                    double ub = side * AngelFeathers.width(joints, feather, tb) * .87 * Math.pow(b, .8);
                    for (int face : new int[]{-1, 1}) {
                        Vec3 p = veinPoint(joints, feather, ta, ua, face, .018);
                        Vec3 q = veinPoint(joints, feather, tb, ub, face, .018);
                        Vec3 tangent = q.subtract(p);
                        if (tangent.lengthSqr() < 1e-10) continue;
                        var n = AngelFeathers.normal(joints, feather, (ta + tb) * .5);
                        Vec3 across = new Vec3(n.x(), n.y(), n.z()).cross(tangent).normalize();
                        double wa = .0024 * forming * (1 - a * .45), wb = .0024 * forming * (1 - b * .45);
                        AngelMesh.quad(buffer, pose, 0xFFD773, .88F * alpha, .88F * alpha, .72F * alpha, .72F * alpha,
                            p.subtract(across.scale(wa)), p.add(across.scale(wa)),
                            q.add(across.scale(wb)), q.subtract(across.scale(wb)));
                    }
                }
            }
        }
    }
}
