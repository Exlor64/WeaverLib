import dev.weaverlib.angel.ChainCombatRules;

/** Restraint health, held damage timing, launch travel and bounded pulling. No Minecraft classes. */
public final class ChainCombatRulesTest {
    private static int checks;
    private static void check(boolean ok) { checks++; if (!ok) throw new AssertionError("Chain combat check " + checks); }
    public static void run() {
        check(ChainCombatRules.HEALTH == 25);
        float hp = ChainCombatRules.HEALTH;
        for (int i = 0; i < 24; i++) { hp = ChainCombatRules.damaged(hp, 1); check(hp > 0); }
        check(ChainCombatRules.damaged(hp, 1) == 0);
        check(ChainCombatRules.damaged(25, 40) == 0);
        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -10, 0})
            check(ChainCombatRules.damaged(25, invalid) == 25);
        check(ChainCombatRules.MELEE_DAMAGE == 4 && ChainCombatRules.TIGHTEN_DAMAGE == 4);
        int pulses = 0;
        for (int t = 1; t <= 199; t++) {
            if (ChainCombatRules.pulse(t)) pulses++;
            check(ChainCombatRules.pulse(t) == (t % 20 == 0));
            check(!ChainCombatRules.expired(t));
        }
        check(pulses == 9 && !ChainCombatRules.pulse(0));
        check(ChainCombatRules.expired(200));
        check(ChainCombatRules.TIGHTEN_PER_TICK * 20 == 4);
        int previous = 0;
        for (int distance = 0; distance <= 640; distance++) {
            int travel = ChainCombatRules.travelTicks(distance / 20.0);
            check(travel >= 3 && travel <= 10 && travel >= previous); previous = travel;
        }
        for (int x = -32; x <= 32; x++) for (int y = -26; y <= 26; y++) for (int z = -4; z <= 4; z++) {
            var p = ChainCombatRules.pull(x, y, z, false);
            double speed = Math.sqrt(p.x()*p.x()+p.y()*p.y()+p.z()*p.z());
            check(Double.isFinite(speed) && speed <= .8500001);
            check(p.x()*x+p.y()*y+p.z()*z >= 0);
            var g = ChainCombatRules.pull(x, y, z, true);
            check(Math.abs(g.x()) <= .45 && Math.abs(g.z()) <= .45 && g.y() >= -1.1 && g.y() <= .05);
            if (y <= 0) check(g.y() < 0);
        }
        check(ChainCombatRules.swing(-1) == 0 && ChainCombatRules.swing(12) == 0);
        check(ChainCombatRules.swing(4) > .8);
        System.out.println("Passed " + checks + " restraint health, damage timing and pull checks.");
    }
    public static void main(String[] args) { run(); }
}
