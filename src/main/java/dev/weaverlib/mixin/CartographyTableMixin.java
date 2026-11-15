package dev.weaverlib.mixin;

import dev.weaverlib.spell.Inscriptions;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CartographyTableMenu.class)
public abstract class CartographyTableMixin {
    @Shadow @Final private ResultContainer resultContainer;

    @Inject(method = "setupResultSlot", at = @At("HEAD"), cancellable = true)
    private void weaverlib$preventBatteryCopy(ItemStack source, ItemStack ingredient, ItemStack result,
            CallbackInfo callback) {
        if (ingredient.is(Items.MAP) && Inscriptions.get(source) != null) {
            resultContainer.removeItemNoUpdate(2);
            ((CartographyTableMenu) (Object) this).broadcastChanges();
            callback.cancel();
        }
    }
}
