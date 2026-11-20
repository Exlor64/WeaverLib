package dev.weaverlib.angel;

/** Paid charge and a short input lease; clients never choose damage or invested energy. */
public record PyraCharge(long started, long until, long paidAt, double invested) {
    public static final double START_COST = 20;
    public static final double PER_TICK = 2.5;
    public static final int LEASE = 10;
    public static final PyraCharge NONE = new PyraCharge(-1, -1, -1, 0);

    public static PyraCharge begin(long now) { return begin(now, START_COST); }
    public static PyraCharge begin(long now, double initial) {
        return new PyraCharge(now, now + LEASE, now, initial);
    }
    public boolean held(long now) { return started >= 0 && now >= started && now < until; }
    public PyraCharge renew(long now) {
        return held(now) ? new PyraCharge(started, now + LEASE, paidAt, invested) : NONE;
    }
    public double nextCost(long now) {
        return held(now) && now > paidAt ? PER_TICK : 0;
    }
    /** Call only after nextCost has been paid. Duplicate packets/ticks cannot increase the charge. */
    public PyraCharge paid(long now) {
        return paid(now, nextCost(now));
    }
    public PyraCharge paid(long now, double amount) {
        return held(now) && now > paidAt && Double.isFinite(amount) && amount > 0
            ? new PyraCharge(started, until, now, invested + amount) : this;
    }
    public static double power(double invested) {
        return Double.isFinite(invested) ? Math.max(0, (invested - START_COST) / 80) : 0;
    }
    public static double damage(double invested) { return 24 + 40 * power(invested); }
    public static double radius(double invested) { return .20 + 1.20 * Math.sqrt(power(invested)); }
    public static double range(double invested) { return 12 + 24 * Math.sqrt(power(invested)); }
    public static double orbRadius(double invested) { return .15 + .43 * Math.cbrt(power(invested)); }
}
