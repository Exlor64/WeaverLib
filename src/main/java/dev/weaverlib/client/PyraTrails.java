package dev.weaverlib.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.weaverlib.angel.Angels;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.Vec3;

/** Short, bounded world-space ribbons capture actual wing motion, including the diving/spinning pose. */
final class PyraTrails {
    private record Key(UUID player, int pair, int side, int lane) {}
    private record Sample(long tick, Vec3 a, Vec3 b) {}
    private static final Map<Key, ArrayDeque<Sample>> TRAILS = new HashMap<>();
    private static ClientLevel world;
    private PyraTrails() {}

    private static void world(ClientLevel level) {
        if (level != world) { TRAILS.clear(); world = level; }
    }

    static void tick(ClientLevel level) {
        world(level);
        if (level == null) return;
        long now = level.getGameTime();
        TRAILS.entrySet().removeIf(entry -> entry.getValue().isEmpty()
            || now - entry.getValue().peekLast().tick() > 2);
    }

    static void sample(AbstractClientPlayer player, PoseStack pose, AngelWingMotion.Joints joints,
            double time, int pair, int side) {
        var client = Minecraft.getInstance();
        world(client.level);
        if (joints.growth() < .25 || player.distanceToSqr(client.gameRenderer.getMainCamera().getPosition()) > 64 * 64)
            return;
        if (TRAILS.size() > 2304) TRAILS.clear();
        long tick = player.level().getGameTime();
        Vec3 camera = client.gameRenderer.getMainCamera().getPosition();
        for (int lane = 0; lane < 3; lane++) {
            var history = TRAILS.computeIfAbsent(new Key(player.getUUID(), pair, side, lane), key -> new ArrayDeque<>());
            double u = .35 + lane * .27;
            Vec3 a = global(pose, PyraWings.point(joints, u - .055, .78, time, 0), camera);
            Vec3 b = global(pose, PyraWings.point(joints, u + .055, .85, time, 0), camera);
            var previous = history.peekLast();
            if (previous != null && (tick < previous.tick || tick - previous.tick > 2
                    || a.distanceToSqr(previous.a) > 8 * 8)) history.clear();
            previous = history.peekLast();
            // Multiple passes in the same frame must not age or multiply the trail.
            if (previous != null && previous.tick == tick) history.removeLast();
            history.addLast(new Sample(tick, a, b));
            while (history.size() > 12) history.removeFirst();
        }
    }

    private static Vec3 global(PoseStack pose, Vec3 local, Vec3 camera) {
        var p = pose.last().pose().transformPosition((float)local.x, (float)local.y, (float)local.z,
            new org.joml.Vector3f());
        return new Vec3(p.x, p.y, p.z).add(camera);
    }

    static void render(VertexConsumer buffer, PoseStack.Pose pose, Vec3 camera, long tick, float partial) {
        var level = Minecraft.getInstance().level;
        world(level);
        if (level == null) return;
        var iterator = TRAILS.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var player = level.getPlayerByUUID(entry.getKey().player());
            var history = entry.getValue();
            if (player == null || !player.isAlive() || !Angels.view(player).active() || !Angels.inferno(player)
                    || player.isInvisible() || entry.getKey().pair() >= Angels.view(player).rank()
                    || history.isEmpty() || tick - history.peekLast().tick > 2) {
                iterator.remove();
                continue;
            }
            Sample previous = null;
            for (var current : history) {
                if (previous != null) {
                    double ageA = tick + partial - previous.tick, ageB = tick + partial - current.tick;
                    double fadeA = Math.clamp(1 - ageA / 12, 0, 1), fadeB = Math.clamp(1 - ageB / 12, 0, 1);
                    // Mild buoyancy curls the cooling tail upward instead of freezing copies of the wing in space.
                    Vec3 liftA = new Vec3(0, ageA * ageA * .0018, 0);
                    Vec3 liftB = new Vec3(0, ageB * ageB * .0018, 0);
                    PyraFire.quad(buffer, pose, PyraFire.TRAIL,
                        current.a.add(liftB).subtract(camera), current.b.add(liftB).subtract(camera),
                        previous.b.add(liftA).subtract(camera), previous.a.add(liftA).subtract(camera),
                        0, ageB / 14, 1, ageA / 14, fadeB * fadeB * .52, fadeA * fadeA * .52);
                }
                previous = current;
            }
        }
    }
}
