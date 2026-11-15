package dev.weaverlib.mixin;

import com.leclowndu93150.thaumaturge.api.items.RechargeAccess;
import com.leclowndu93150.thaumaturge.content.aura.BlockEntityRechargePedestal;
import com.leclowndu93150.thaumaturge.content.wands.ItemWand;
import dev.weaverlib.spell.Inscriptions;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockEntityRechargePedestal.class, remap = false)
public abstract class RechargePedestalMixin {
    @Shadow private int counter;

    @Inject(method = "tickServer", at = @At("RETURN"))
    private void weaverlib$chargeInscribedWand(ServerLevel level, CallbackInfo callback) {
        if (counter % 10 != 0) {
            return;
        }
        // Thaumaturge's wand branch fills aspect reservoirs and skips the generic Vis battery.
        var pedestal = (BlockEntityRechargePedestal) (Object) this;
        var stack = pedestal.getItem();
        if (stack.getItem() instanceof ItemWand && Inscriptions.get(stack) != null
                && RechargeAccess.drawFromAura(level, stack, pedestal.getBlockPos(), null, 5) > 0) {
            pedestal.setItem(stack);
        }
    }
}
