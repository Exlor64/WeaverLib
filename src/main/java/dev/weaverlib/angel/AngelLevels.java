package dev.weaverlib.angel;

public final class AngelLevels {
    public static final int ENTRY = 25;
    public static final int ARCHANGEL = 75;
    public static final int SERAPHIM = 150;

    private AngelLevels() {}

    public static int points(int advancements, int blessings) {
        return (int) Math.min(Integer.MAX_VALUE, (long) Math.max(0, advancements) + Math.max(0, blessings));
    }

    public static int rankAt(int points) {
        return points >= SERAPHIM ? 3 : points >= ARCHANGEL ? 2 : 1;
    }

    public static boolean mayBless(int primePoints, int recipientPoints) {
        return recipientPoints >= 0 && recipientPoints < primePoints;
    }

    public static boolean dayReady(long day, boolean alreadyBlessed, long lastDay) {
        return !alreadyBlessed || day > lastDay;
    }
}
