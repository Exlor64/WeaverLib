package dev.weaverlib.client;

import net.minecraft.world.phys.Vec3;

/** The same yaw, pitch and longitudinal roll used by the diving player renderer. */
public record AngelFlightFrame(Vec3 forward, Vec3 right, Vec3 up) {
    public static double roll(double remainingSpinTicks) {
        return AngelFlightMath.roll(remainingSpinTicks);
    }

    public static double channelRoll(double elapsedTicks) {
        return AngelFlightMath.channelRoll(elapsedTicks);
    }

    public static AngelFlightFrame of(double yaw, double pitch, double roll) {
        var basis = AngelFlightMath.of(yaw, pitch, roll);
        return new AngelFlightFrame(vector(basis.forward()), vector(basis.right()), vector(basis.up()));
    }

    private static Vec3 vector(AngelFlightMath.Vector v) {
        return new Vec3(v.x(), v.y(), v.z());
    }

    public Vec3 point(Vec3 center, double axial, double radius, double angle) {
        return center.add(forward.scale(axial))
            .add(right.scale(radius * Math.cos(angle))).add(up.scale(radius * Math.sin(angle)));
    }
}
