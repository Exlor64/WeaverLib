package dev.weaverlib.angel;

/** Shared, bootstrap-free limits for Retribution's first combat pass. */
public final class ChainCombatRules {
    public static final float HEALTH = 25, MELEE_DAMAGE = 4, TIGHTEN_DAMAGE = 4;
    public static final double MELEE_RANGE = 4, TARGET_RANGE = 16;
    public static final double MELEE_COST = 2, GRAPPLE_COST = 6, BIND_COST = 8, TIGHTEN_PER_TICK = .2;
    public static final int MAX_BINDS = 4, LIFETIME = 200, ATTACK_COOLDOWN = 12, HIT_DELAY = 4;
    private ChainCombatRules() {}
    public static float damaged(float health, float damage) {
        return Float.isFinite(damage) && damage > 0 ? Math.max(0, health - damage) : health;
    }
    public static int travelTicks(double distance) { return (int) Math.clamp(Math.ceil(distance / 2), 3, 10); }
    public static boolean pulse(long heldTicks) { return heldTicks > 0 && heldTicks % 20 == 0; }
    public static boolean expired(long age) { return age >= LIFETIME; }
    public record Pull(double x, double y, double z) {}
    public static Pull pull(double x, double y, double z, boolean ground) {
        if (ground) return new Pull(Math.clamp(x * .35, -.45, .45), Math.clamp(y * .35 - .15, -1.1, .05),
            Math.clamp(z * .35, -.45, .45));
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 1e-8) return new Pull(0, 0, 0);
        double strength = Math.min(.85, .25 + length * .045) / length;
        return new Pull(x * strength, y * strength, z * strength);
    }
    public static double swing(double age) {
        if (age < 0 || age >= 12) return 0;
        return Math.sin(Math.PI * age / 12);
    }
}
