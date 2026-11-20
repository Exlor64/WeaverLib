package dev.weaverlib.angel;

/** Server-authoritative channel lease. Renewing input never resets the visual or hit history. */
public record AngelDash(long started, long until) {
    public static final AngelDash NONE = new AngelDash(-1, 0);
    public static final int INPUT_TIMEOUT = 10;
    public static final double START_COST = 10;
    public static final double COST_PER_TICK = 10.0 / 20;

    public static AngelDash begin(long now) { return new AngelDash(now, now + INPUT_TIMEOUT); }
    public boolean active(long now) { return started >= 0 && now < until; }
    public AngelDash renew(long now) {
        return active(now) ? new AngelDash(started, now + INPUT_TIMEOUT) : this;
    }
}
