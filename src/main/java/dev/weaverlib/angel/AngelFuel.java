package dev.weaverlib.angel;

import dev.weaverlib.compat.EmberFuel;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

final class AngelFuel {
    private static final ModConfigSpec CONFIG;
    private static final ModConfigSpec.DoubleValue EMBER_PER_GRACE;
    static {
        var builder = new ModConfigSpec.Builder();
        EMBER_PER_GRACE = builder.comment("Ember spent from carried Embers storage per missing point of angel Grace.",
            "Personal Grace pays first. Carried energy covers only the shortfall; it does not refill Grace.")
            .defineInRange("emberPerGrace", 100.0, .0001, 1000000.0);
        CONFIG = builder.build();
    }

    private AngelFuel() {}

    static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, CONFIG, "weaverlib-angels-server.toml");
    }

    static boolean pay(ServerPlayer player, double cost, boolean commit) {
        double reserve = Angels.energy(player);
        if (AngelEnergy.canPay(reserve, cost)) {
            if (commit) Angels.setEnergy(player, AngelEnergy.afterPayment(reserve, cost));
            return true;
        }
        if (!Double.isFinite(cost) || cost < 0) return false;
        var stores = ModList.get().isLoaded("embers") ? EmberFuel.stores(player) : List.<AngelPayment.Store>of();
        double after = AngelPayment.spend(reserve, cost, EMBER_PER_GRACE.get(), stores, commit);
        if (after < 0) return false;
        if (commit) {
            Angels.setEnergy(player, after);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
        }
        return true;
    }
}
