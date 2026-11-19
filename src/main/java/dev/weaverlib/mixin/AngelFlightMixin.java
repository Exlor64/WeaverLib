package dev.weaverlib.mixin;

import dev.weaverlib.angel.Angels;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class AngelFlightMixin {
    @Inject(method = "updateFallFlying", at = @At("HEAD"), cancellable = true)
    private void weaverlib$angelFlight(CallbackInfo callback) {
        if ((Object) this instanceof Player player && Angels.view(player).fast() && player.isAlive()
                && !player.onGround() && !player.isInWater() && !player.isPassenger()) {
            player.startFallFlying();
            callback.cancel();
        }
    }
}
