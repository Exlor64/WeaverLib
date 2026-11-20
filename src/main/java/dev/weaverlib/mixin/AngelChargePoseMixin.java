package dev.weaverlib.mixin;

import dev.weaverlib.angel.Angels;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Both hands cradle the charge. Wing poses remain independent and never become a ground shield. */
@Mixin(HumanoidModel.class)
abstract class AngelChargePoseMixin {
    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void weaverlib$charge(LivingEntity entity, float limbSwing, float amount, float age,
            float yaw, float pitch, CallbackInfo ci) {
        if (!(entity instanceof Player player) || !Angels.view(player).active() || !Angels.inferno(player)
                || Angels.flying(player) || !Angels.charge(player).active()) return;
        float raise = (float)Math.toRadians(-78 + Math.clamp(pitch, -65, 65) * .75);
        float breathe = (float)Math.sin(age * .12) * .018F;
        rightArm.xRot = raise + breathe;
        leftArm.xRot = raise - breathe;
        rightArm.yRot = -.32F;
        leftArm.yRot = .32F;
        rightArm.zRot = .06F;
        leftArm.zRot = -.06F;
    }
}
