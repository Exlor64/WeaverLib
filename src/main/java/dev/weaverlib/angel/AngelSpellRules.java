package dev.weaverlib.angel;

/** Ordered wheel combinations and timing shared by the server and spell previews. */
public final class AngelSpellRules {
    public static final int HEALING = 0, SMITE = 1, REPEL = 2, CLEANSE = 3, CIRCLE = 4, FORGE = 5;
    public static final double HEAL_RADIUS = 3, CLEANSE_RADIUS = 3, WAVE_RADIUS = 6, SMITE_RANGE = 32;
    public static final int HEAL_TICKS = 80, CIRCLE_TICKS = 20, WAVE_TICKS = 12;
    public static final float HEAL_AMOUNT = 2, CIRCLE_DAMAGE = 8;
    private AngelSpellRules() {}

    public static int spell(int sequence, int rank) {
        if (rank < 2 || rank > 3) return -1;
        return switch (sequence) {
            case 1 -> HEALING; // DDP
            case 4 -> SMITE;   // DPP
            case 3 -> REPEL;   // DPD
            case 9 -> CLEANSE; // PDD
            case 12 -> CIRCLE; // PPD
            case 10 -> FORGE;  // PDP
            default -> -1;
        };
    }
    public static int cost(int spell) {
        return switch (spell) {
            case HEALING, CLEANSE -> 15;
            case SMITE -> 25;
            case REPEL -> 20;
            case CIRCLE -> 35;
            case FORGE -> 8;
            default -> 0;
        };
    }
    public static String key(int spell) {
        return "weaverlib.angel.spell." + switch (spell) {
            case HEALING -> "healing";
            case SMITE -> "smite";
            case REPEL -> "repel";
            case CLEANSE -> "cleanse";
            case CIRCLE -> "circle";
            case FORGE -> "forge";
            default -> "unknown";
        };
    }
    public static int smiteDamage(int rank) { return 10 * Math.clamp(rank, 1, 3); }
    public static boolean healingPulse(long age) { return age > 0 && age <= HEAL_TICKS && age % 20 == 0; }
    public static boolean circleActive(long age) { return age >= 0 && age < CIRCLE_TICKS; }
    public static double circleRadius(double age) { return .5 + 5.5 * Math.clamp(age / 10, 0, 1); }
    public static double waveRadius(double age) { return WAVE_RADIUS * Math.clamp(age / WAVE_TICKS, 0, 1); }
    public static int reducedWarp(int current, int reduction) { return Math.max(0, current - reduction); }

    /** A held stack may be partially forged; predicates only inspect payment availability. */
    public static int forgeCount(int count, java.util.function.DoublePredicate canPay) {
        int low = 0, high = Math.max(0, count);
        while (low < high) {
            int middle = low + (int)(((long)high - low + 1) / 2);
            if (canPay.test((double)middle * cost(FORGE))) low = middle;
            else high = middle - 1;
        }
        return low;
    }
}
