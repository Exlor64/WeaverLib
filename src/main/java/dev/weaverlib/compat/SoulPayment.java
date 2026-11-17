package dev.weaverlib.compat;

final class SoulPayment {
    private SoulPayment() {}

    static int shortfall(int cost, int stored) {
        return Math.max(0, cost - Math.max(0, stored));
    }

    static boolean canSpend(int amount, int current) {
        return amount < current;
    }

    static int warpGain(int before, int spent) {
        int warp = 0;
        for (int point = before - 1; point >= before - spent; point--) {
            warp += 1 + (100 - point) / 20;
        }
        return warp;
    }

}
