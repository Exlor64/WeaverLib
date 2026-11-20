package dev.weaverlib.angel;

/** Pyra trades large Grace expenditures for heavy damage; Divinity retains its own balance. */
public final class PyraCombat {
    public static final double TRANSFORM_COST = 20;
    public static final double MELEE_COST = 15;
    public static final float MELEE_DAMAGE = 15;
    public static final double ARC_COST = 20;
    public static final float ARC_DAMAGE = 24;
    public static final double FIREBALL_START = 30;
    public static final double FIREBALL_PER_TICK = 3;
    public static final double DASH_BLAST_COST = 40;
    public static final double COLLISION_PER_DAMAGE = 2;
    public static final double DASH_START_COST = 30;
    public static final double DASH_PER_TICK = 30.0 / 20;
    public static final float DASH_DAMAGE = 30;
    public static final double MANTLE_MULTIPLIER = 3;
    private PyraCombat() {}

    public static double fireballPower(double invested) {
        return Double.isFinite(invested) ? Math.max(0, (invested - FIREBALL_START) / 100) : 0;
    }
    public static double fireballDamage(double invested) { return 32 + 48 * fireballPower(invested); }
    public static double fireballRadius(double invested) { return 2.5 + 3.5 * Math.sqrt(fireballPower(invested)); }
    public static double fireballOrb(double invested) { return .45 + .60 * Math.cbrt(fireballPower(invested)); }

    /** The standing cost includes the persistent flame aura; slow/fast flight total 4/7 Grace per second. */
    public static double upkeep(boolean flying, boolean fast) {
        return (2 + (fast ? 5 : flying ? 2 : 0)) / 20.0;
    }
}
