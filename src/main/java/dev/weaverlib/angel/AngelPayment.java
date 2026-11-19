package dev.weaverlib.angel;

import java.util.ArrayList;
import java.util.List;

/** Plans payment without changing either reserve. Stores stage withdrawals until commit. */
public final class AngelPayment {
    public interface Store {
        double available();
        boolean stage(double amount);
        void commit();
    }

    private AngelPayment() {}

    public static double spend(double reserve, double cost, double emberPerGrace, List<? extends Store> stores,
            boolean commit) {
        if (!Double.isFinite(reserve) || reserve < 0 || !Double.isFinite(cost) || cost < 0) return -1;
        if (AngelEnergy.canPay(reserve, cost)) return commit ? AngelEnergy.afterPayment(reserve, cost) : reserve;
        if (!Double.isFinite(emberPerGrace) || emberPerGrace <= 0) return -1;
        double missing = (cost - reserve) * emberPerGrace;
        if (!Double.isFinite(missing)) return -1;
        List<Store> used = new ArrayList<>();
        for (Store store : stores) {
            double available = store.available();
            if (!Double.isFinite(available) || available <= 0) continue;
            double amount = Math.min(missing, available);
            if (!store.stage(amount)) return -1;
            used.add(store);
            missing -= amount;
            if (missing <= 1.0E-9) break;
        }
        if (missing > 1.0E-9) return -1;
        if (commit) used.forEach(Store::commit);
        return 0;
    }
}
