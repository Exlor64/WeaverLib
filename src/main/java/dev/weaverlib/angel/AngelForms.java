package dev.weaverlib.angel;

/** Server-authoritative wheel combinations. Unwritten or inaccessible sequences return -1. */
public final class AngelForms {
    public static final int DIVINITY = 0;
    public static final int INFERNO = 1;
    private AngelForms() {}

    public static int invocation(int sequence, int rank) {
        if (rank < 1 || rank > 3 || sequence < 0 || sequence > 26) return -1;
        if (sequence == 0) return DIVINITY;
        if (sequence == 13 && rank >= 2) return INFERNO;
        return -1;
    }
}
