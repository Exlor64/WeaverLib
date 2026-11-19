package dev.weaverlib.compat;

import com.rekindled.embers.api.capabilities.EmbersCapabilities;
import com.rekindled.embers.util.CapabilityCompat;
import dev.weaverlib.angel.AngelPayment;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.component.CustomData;

/** Loaded only when Embers is present. Its legacy item capability initializes NBT on lookup. */
public final class EmberFuel {
    private EmberFuel() {}

    public static List<AngelPayment.Store> stores(ServerPlayer player) {
        List<AngelPayment.Store> stores = new ArrayList<>();
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var original = inventory.getItem(slot);
            if (original.isEmpty()) continue;
            // Query and withdraw on a copy: failed payments must leave even initialization NBT untouched.
            var working = original.copy();
            var cap = CapabilityCompat.getCapability(working, EmbersCapabilities.EMBER_CAPABILITY).orElse(null);
            if (cap == null || !(cap.getEmber() > 0)) continue;
            stores.add(new AngelPayment.Store() {
                public double available() { return cap.removeAmount(cap.getEmber(), false); }

                public boolean stage(double amount) {
                    return cap.removeAmount(amount, true) + 1.0E-9 >= amount;
                }

                public void commit() {
                    // Placed storage also carries BlockEntityData. Keep it consistent so another
                    // capability lookup or placing the cell cannot restore its pre-payment charge.
                    if (working.has(DataComponents.BLOCK_ENTITY_DATA)) {
                        CustomData.update(DataComponents.BLOCK_ENTITY_DATA, working, cap::writeToNBT);
                    }
                    var tag = working.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                    if (tag.contains("BlockEntityTag", 10)) {
                        var block = tag.getCompound("BlockEntityTag");
                        cap.writeToNBT(block);
                        tag.put("BlockEntityTag", block);
                        working.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                    }
                    original.applyComponents(working.getComponentsPatch());
                }
            });
        }
        return stores;
    }
}
