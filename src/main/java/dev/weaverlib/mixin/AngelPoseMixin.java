package dev.weaverlib.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.weaverlib.angel.Angels;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class AngelPoseMixin {
    @Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
        at = @At("TAIL"))
    private void weaverlib$spin(AbstractClientPlayer player, PoseStack pose, float age, float yaw,
            float partial, float scale, CallbackInfo callback) {
        var view = Angels.view(player);
        double remaining = view.spin() - player.level().getGameTime() - partial;
        if (view.active() && view.fast() && remaining > 0) {
            pose.mulPose(Axis.YP.rotationDegrees((float) (remaining * 60)));
        }
    }
}
