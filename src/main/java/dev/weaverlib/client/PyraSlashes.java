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

/** The actual attacking wing's leading edge leaves a short-lived, animated flame cut in world space. */
final class PyraSlashes {
    private record Key(UUID player, long swing) {}
    private record Edge(long tick, Vec3 inner, Vec3 outer) {}
    private static final Map<Key, ArrayDeque<Edge>> CUTS = new HashMap<>();
    private static ClientLevel world;
    private PyraSlashes() {}

    static void tick(ClientLevel level) {
        if (world != level) { CUTS.clear(); world = level; }
        if (level == null) return;
        long now = level.getGameTime();
        CUTS.entrySet().removeIf(e -> e.getValue().isEmpty() || now - e.getValue().peekLast().tick > 18);
    }

    static void sample(AbstractClientPlayer player, PoseStack pose, AngelWingMotion.Joints joints,
            double time, double attack) {
        if (attack < .12 || attack > .8 || joints.growth() < .95) return;
        tick((ClientLevel)player.level());
        var key = new Key(player.getUUID(), Angels.view(player).swing());
        if (CUTS.size() >= 128 && !CUTS.containsKey(key)) CUTS.remove(CUTS.keySet().iterator().next());
        var edges = CUTS.computeIfAbsent(key, k -> new ArrayDeque<>());
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        long now = player.level().getGameTime();
        if (!edges.isEmpty() && edges.peekLast().tick == now) edges.removeLast();
        Vec3 a = world(pose, PyraWings.point(joints, .63, .03, time, 0), camera);
        Vec3 b = world(pose, PyraWings.point(joints, 1.03, .11, time, 0), camera);
        if (!edges.isEmpty() && a.distanceToSqr(edges.peekLast().inner) > 8 * 8) edges.clear();
        edges.addLast(new Edge(now,a,b));
        while (edges.size() > 12) edges.removeFirst();
    }

    private static Vec3 world(PoseStack pose, Vec3 p, Vec3 camera) {
        var t = pose.last().pose().transformPosition((float)p.x,(float)p.y,(float)p.z,new org.joml.Vector3f());
        return new Vec3(t.x,t.y,t.z).add(camera);
    }

    static void render(VertexConsumer buffer, PoseStack.Pose pose, Vec3 camera, long tick, float partial) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        for (var entry : CUTS.entrySet()) {
            var player = level.getPlayerByUUID(entry.getKey().player());
            if (player == null || !player.isAlive() || player.isInvisible() || !Angels.view(player).active()
                    || !Angels.inferno(player)) continue;
            Edge previous = null;
            for (var edge : entry.getValue()) {
                if (previous != null) {
                    double a = Math.clamp(1 - (tick + partial - previous.tick) / 20, 0, 1);
                    double b = Math.clamp(1 - (tick + partial - edge.tick) / 20, 0, 1);
                    PyraFire.quad(buffer,pose,PyraFire.TRAIL,
                        previous.inner.subtract(camera), previous.outer.subtract(camera),
                        edge.outer.subtract(camera),edge.inner.subtract(camera),0,.08,1,.88,a*.90,b*.90);
                }
                previous = edge;
            }
        }
    }
}
