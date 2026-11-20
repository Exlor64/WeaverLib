package dev.weaverlib.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.weaverlib.angel.Angels;
import dev.weaverlib.client.AngelFlightFrame;
import dev.weaverlib.client.AngelFlightMath;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class AngelPoseMixin {
    @Unique
    private final java.util.Map<java.util.UUID, double[]> weaverlib$rolls = new java.util.HashMap<>();
    @Unique
    private Object weaverlib$rollWorld;

    @Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
        at = @At("HEAD"), cancellable = true)
    private void weaverlib$flightPose(AbstractClientPlayer player, PoseStack pose, float age, float yaw,
            float partial, float scale, CallbackInfo callback) {
        var view = Angels.view(player);
        if (weaverlib$rollWorld != player.level()) {
            weaverlib$rolls.clear();
            weaverlib$rollWorld = player.level();
        }
        if (!view.active() || !view.fast()) {
            weaverlib$rolls.remove(player.getUUID());
            return;
        }
        // Fast flight always uses a diving body frame, even before vanilla fall-flying ticks catch up.
        // Apply roll only AFTER yaw and pitch so it spins around the head-to-feet axis.
        pose.mulPose(Axis.YP.rotationDegrees(180 - player.getViewYRot(partial)));
        pose.mulPose(Axis.XP.rotationDegrees(-90 - player.getViewXRot(partial)));
        double time = player.level().getGameTime() + partial;
        long started = Angels.dashStarted(player);
        if (weaverlib$rolls.size() > 128) weaverlib$rolls.clear();
        double[] state = weaverlib$rolls.computeIfAbsent(player.getUUID(), id -> new double[]{time, 0});
        state[1] = view.spin() > time && started >= 0 ? AngelFlightFrame.channelRoll(time - started)
            : AngelFlightMath.settleRoll(state[1], Math.clamp(time - state[0], 0, 5));
        state[0] = time;
        pose.mulPose(Axis.YP.rotation((float) state[1]));
        callback.cancel();
    }
}
