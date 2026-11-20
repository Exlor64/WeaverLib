package dev.weaverlib.client;

import java.util.ArrayList;
import java.util.List;

/** Shared feather shapes for solid vanes, attached flames and motion-trail anchors. No game dependencies. */
public final class AngelFeathers {
    public record Feather(int index, double span, double start, double width, double length, double lean,
                          int normalColor, boolean covert) {}
    public static final List<Feather> ALL = layout();
    public static final int GOLD_COUNT = 5;
    private AngelFeathers() {}

    private static List<Feather> layout() {
        var result = new ArrayList<Feather>(49);
        for (int i = 0; i < 12; i++) result.add(new Feather(result.size(), .40 + i * .056,
            .04, .055, .93 - i * .026, .055 + i * .004, i % 2 == 0 ? 0xFFF4C6 : 0xFFE3A2, false));
        for (int i = 0; i < 11; i++) {
            double u = .025 + i * .052;
            result.add(new Feather(result.size(), u, .015, .048, .84 + u * .22, .025, 0xFFEAB0, false));
        }
        for (int row = 0; row < 2; row++) for (int i = 0; i < 13; i++)
            result.add(new Feather(result.size(), .018 + i * .075, -.035 + row * .11, .045,
                .38 + row * .10, .012, row == 0 ? 0xFFF9DE : 0xF9D58D, true));
        return List.copyOf(result);
    }

    public static long seed(long most, long least, int pair, int side) {
        return mix(most ^ Long.rotateLeft(least, 17) ^ (pair + 1L) * 0x9E3779B97F4A7C15L
            ^ (side < 0 ? 0x6A09E667F3BCC909L : 0xBB67AE8584CAA73BL));
    }

    /** Exactly five scattered accents per wing, stable across frames, clients and reconnects. */
    public static long goldMask(long seed) {
        long mask = 0;
        for (long n = 0; Long.bitCount(mask) < GOLD_COUNT; n++) {
            int index = (int)Math.floorMod(mix(seed + n * 0x9E3779B97F4A7C15L), ALL.size());
            mask |= 1L << index;
        }
        return mask;
    }

    public static boolean golden(long mask, Feather feather) { return (mask & 1L << feather.index()) != 0; }

    public static int pyraColor(Feather feather, boolean gold) {
        if (gold) return feather.index() % 2 == 0 ? 0xFFDE8C : 0xEBC064;
        return switch (feather.index() % 4) {
            case 0 -> 0xD65B2E;
            case 1 -> 0xC44B29;
            case 2 -> 0xE16B35;
            default -> feather.covert() ? 0xB84127 : 0xCF542B;
        };
    }

    public static double growth(AngelWingMotion.Joints joints, Feather feather) {
        return AngelWingMotion.featherGrowth(feather.span(), joints.growth());
    }

    public static double width(AngelWingMotion.Joints joints, Feather feather, double along) {
        double born = growth(joints, feather);
        return feather.width() * (.20 + .80 * born)
            * Math.sin(Math.PI * Math.pow(Math.clamp(along, 0, 1), .65));
    }

    /** Span offset is measured on the same curved wing surface as the solid feather. */
    public static AngelWingMotion.Point point(AngelWingMotion.Joints joints, Feather feather,
            double along, double spanOffset) {
        double born = growth(joints, feather);
        return joints.surface(feather.span() + feather.lean() * born * along * along + spanOffset,
            feather.start() + feather.length() * born * along);
    }

    public static AngelWingMotion.Point normal(AngelWingMotion.Joints joints, Feather feather, double along) {
        var a = point(joints, feather, along, -.003);
        var b = point(joints, feather, along, .003);
        var c = point(joints, feather, along - .01, 0);
        var d = point(joints, feather, along + .01, 0);
        double ax = b.x() - a.x(), ay = b.y() - a.y(), az = b.z() - a.z();
        double bx = d.x() - c.x(), by = d.y() - c.y(), bz = d.z() - c.z();
        double x = ay * bz - az * by, y = az * bx - ax * bz, z = ax * by - ay * bx;
        double length = Math.sqrt(x*x + y*y + z*z);
        return length < 1e-8 ? new AngelWingMotion.Point(0,0,1)
            : new AngelWingMotion.Point(x/length,y/length,z/length);
    }

    private static long mix(long value) {
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }
}
