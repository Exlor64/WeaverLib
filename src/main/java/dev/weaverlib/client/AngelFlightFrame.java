package dev.weaverlib.client;

import net.minecraft.world.phys.Vec3;

/** The same yaw, pitch and longitudinal roll used by the diving player renderer. */
public record AngelFlightFrame(Vec3 forward, Vec3 right, Vec3 up) {
    public static double roll(double remainingSpinTicks) {
        return Math.max(0, remainingSpinTicks) * Math.PI / 3;
    }

    public static AngelFlightFrame of(double yaw, double pitch, double roll) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        Vec3 forward = new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
        Vec3 right = new Vec3(Math.cos(y), 0, Math.sin(y));
        Vec3 up = forward.cross(right);
        return new AngelFlightFrame(forward,
            right.scale(Math.cos(roll)).add(up.scale(Math.sin(roll))),
            up.scale(Math.cos(roll)).subtract(right.scale(Math.sin(roll))));
    }

    public Vec3 point(Vec3 center, double axial, double radius, double angle) {
        return center.add(forward.scale(axial))
            .add(right.scale(radius * Math.cos(angle))).add(up.scale(radius * Math.sin(angle)));
    }
}
