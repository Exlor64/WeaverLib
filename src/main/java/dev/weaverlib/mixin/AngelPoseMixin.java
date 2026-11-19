package dev.weaverlib.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.weaverlib.angel.Angels;
import dev.weaverlib.client.AngelFlightFrame;
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
        if (!view.active() || !view.fast()) return;
        // Fast flight always uses a diving body frame, even before vanilla fall-flying ticks catch up.
        // Apply roll only AFTER yaw and pitch so it spins around the head-to-feet axis.
        pose.mulPose(Axis.YP.rotationDegrees(180 - player.getViewYRot(partial)));
        pose.mulPose(Axis.XP.rotationDegrees(-90 - player.getViewXRot(partial)));
        double remaining = view.spin() - player.level().getGameTime() - partial;
        pose.mulPose(Axis.YP.rotation((float) AngelFlightFrame.roll(remaining)));
        callback.cancel();
    }
}
