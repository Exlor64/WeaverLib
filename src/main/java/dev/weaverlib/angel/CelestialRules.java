package dev.weaverlib.angel;

/** Timing and size scaling for the first Retribution wheel spells. No game bootstrap. */
public final class CelestialRules {
    public static final int SEAL_TICKS = 300, CHAIN_TICKS = 300, GROUNDS_TICKS = 200,
        SKY_TICKS = 600, DAMOCLES_TICKS = 600, RETALIATION_TICKS = 400;
    private CelestialRules() {}
    public static boolean daylight(long time){return Math.floorMod(time,24000)<12000;}
    public static double scale(double volume) {
        return Double.isFinite(volume) ? Math.max(1, Math.cbrt(Math.max(0, volume) / 2)) : 1;
    }
    public static double sealCost(double scale) { return 40 * Math.max(1, scale); }
    public static double sealUpkeep(double scale) { return 3 * Math.max(1, scale) / 20; }
    public static double sealSlow(double scale) { return Math.min(.9, .1 * Math.max(1, scale)); }
    public static double retaliation(double accumulated, double taken) {
        return Double.isFinite(taken) && taken > 0 ? Math.min(Float.MAX_VALUE/4, accumulated + taken) : accumulated;
    }
}
