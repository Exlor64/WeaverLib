package dev.weaverlib.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.weaverlib.angel.Angels;
import dev.weaverlib.client.AngelFlightPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class AngelPoseMixin {
    @Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
        at = @At("HEAD"), cancellable = true)
    private void weaverlib$flightPose(AbstractClientPlayer player, PoseStack pose, float age, float yaw,
            float partial, float scale, CallbackInfo callback) {
        var view = Angels.view(player);
        var flight = AngelFlightPose.sample(player, partial);
        if (!view.active() || (!view.fast() && flight.blend() <= 0 && flight.roll() == 0)) return;
        // Interpolate the visual body frame in both directions, independent of vanilla's pose flag.
        pose.mulPose(Axis.YP.rotationDegrees(180 - flight.yaw()));
        pose.mulPose(Axis.XP.rotationDegrees(flight.tilt()));
        pose.mulPose(Axis.YP.rotation((float) flight.roll()));
        callback.cancel();
    }
}
