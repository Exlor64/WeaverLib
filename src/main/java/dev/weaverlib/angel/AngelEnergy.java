package dev.weaverlib.angel;

/** Costs are measured in energy points, independent of the frame rate. */
public final class AngelEnergy {
    private AngelEnergy() {}

    public static double capacity(int rank) {
        return switch (rank) {
            case 1 -> 100;
            case 2 -> 200;
            case 3 -> 350;
            default -> 0;
        };
    }

    public static double sunlightPerSecond(int rank) {
        return switch (rank) {
            case 1 -> 2;
            case 2 -> 4;
            case 3 -> 6;
            default -> 0;
        };
    }

    public static boolean canPay(double energy, double cost) {
        return Double.isFinite(energy) && Double.isFinite(cost) && cost >= 0 && energy + 1.0E-9 >= cost;
    }

    public static double afterPayment(double energy, double cost) {
        return canPay(energy, cost) ? Math.max(0, energy - cost) : energy;
    }

    public static double upkeep(boolean flying, boolean fast) {
        return (.25 + (fast ? 2 : flying ? .5 : 0)) / 20;
    }
}
