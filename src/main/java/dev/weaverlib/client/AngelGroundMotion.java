package dev.weaverlib.client;

/** Frame-rate independent transitions for resting, takeoff and ordinary falls. No movement or energy effects. */
public final class AngelGroundMotion {
    public static final int IDLE_TICKS = 100;
    public record Pose(double folded, double airborne, double falling, double jumpAge, boolean takeoff) {
        public double rate() { return jumpAge >= 0 && jumpAge < 18 ? .35 : .14 + .26 * falling; }
        public double effort() { return jumpAge >= 0 && jumpAge < 18 ? .95 : .55 + .45 * falling; }
    }
    private boolean initialized, wasGrounded;
    private double lastTime, folded, airborne, falling, jumpAt = -1000;
    private long seenJump = -1;

    public Pose sample(double now, long lastAction, long serverJump, boolean onGround,
            boolean canBeat, boolean flying, boolean combat, double verticalSpeed) {
        boolean takeoff = false;
        double elapsed = initialized ? Math.clamp(now - lastTime, 0, 5) : 0;
        if (!initialized || now < lastTime) {
            folded = !flying && !canBeat && !combat && now - lastAction >= IDLE_TICKS ? 1 : 0;
            airborne = canBeat && !flying ? 1 : 0;
            initialized = true;
            wasGrounded = onGround;
        }
        if (!flying && wasGrounded && !onGround && canBeat && verticalSpeed > .05) {
            jumpAt = now;
            takeoff = true;
        }
        if (serverJump > seenJump && serverJump <= now) {
            seenJump = serverJump;
            if (!flying && serverJump >= 0 && now - serverJump >= 0 && now - serverJump < 18) {
                // An authoritative confirmation must not replay an already predicted local wingbeat.
                if (serverJump - jumpAt > 6) {
                    jumpAt = serverJump;
                    takeoff = true;
                }
            }
        }
        double targetFold = !flying && !canBeat && !combat && now - lastAction >= IDLE_TICKS ? 1 : 0;
        folded = approach(folded, targetFold, elapsed, targetFold > folded ? 7 : 1.6);
        airborne = approach(airborne, canBeat && !flying ? 1 : 0, elapsed, 2);
        double fall = canBeat && !flying ? fallStrength(verticalSpeed) : 0;
        falling = approach(falling, fall, elapsed, 2.5);
        lastTime = now;
        wasGrounded = onGround;
        return new Pose(folded, airborne, falling, now - jumpAt, takeoff);
    }

    public static double fallStrength(double verticalSpeed) {
        return AngelWingMotion.smooth((-verticalSpeed - .04) / 1.2);
    }
    private static double approach(double current, double target, double ticks, double response) {
        return current + (target - current) * (1 - Math.exp(-ticks / response));
    }
}
