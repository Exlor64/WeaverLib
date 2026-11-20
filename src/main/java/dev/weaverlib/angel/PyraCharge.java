package dev.weaverlib.angel;

/** Paid charge and a short input lease; clients never choose damage or invested energy. */
public record PyraCharge(long started, long until, long paidAt, double invested) {
    public static final double START_COST = 5;
    public static final double PER_TICK = 1.25;
    public static final float MELEE_DAMAGE = 5 * 1.35F;
    public static final int LEASE = 10;
    public static final PyraCharge NONE = new PyraCharge(-1, -1, -1, 0);

    public static PyraCharge begin(long now) { return new PyraCharge(now, now + LEASE, now, START_COST); }
    public boolean held(long now) { return started >= 0 && now >= started && now < until; }
    public PyraCharge renew(long now) {
        return held(now) ? new PyraCharge(started, now + LEASE, paidAt, invested) : NONE;
    }
    public double nextCost(long now) {
        return held(now) && now > paidAt ? PER_TICK : 0;
    }
    /** Call only after nextCost has been paid. Duplicate packets/ticks cannot increase the charge. */
    public PyraCharge paid(long now) {
        return held(now) && now > paidAt
            ? new PyraCharge(started, until, now, invested + nextCost(now)) : this;
    }
    public static double power(double invested) {
        return Double.isFinite(invested) ? Math.max(0, (invested - START_COST) / 95) : 0;
    }
    public static double damage(double invested) { return 8 + 24 * power(invested); }
    public static double radius(double invested) { return .20 + 1.20 * Math.sqrt(power(invested)); }
    public static double range(double invested) { return 12 + 24 * Math.sqrt(power(invested)); }
    public static double orbRadius(double invested) { return .15 + .43 * Math.cbrt(power(invested)); }
}
