package dev.weaverlib.mixin;

import dev.weaverlib.spell.Inscriptions;
import net.minecraft.world.item.crafting.BannerDuplicateRecipe;
import net.minecraft.world.item.crafting.BookCloningRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.MapCloningRecipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({BannerDuplicateRecipe.class, BookCloningRecipe.class, MapCloningRecipe.class})
public abstract class CloningRecipeMixin {
    @Inject(method = "matches(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/world/level/Level;)Z",
        at = @At("HEAD"), cancellable = true)
    private void weaverlib$preventBatteryCopy(CraftingInput input, Level level,
            CallbackInfoReturnable<Boolean> callback) {
        if (input.items().stream().anyMatch(stack -> Inscriptions.get(stack) != null)) {
            callback.setReturnValue(false);
        }
    }
}
