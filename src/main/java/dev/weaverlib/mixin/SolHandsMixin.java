package dev.weaverlib.mixin;

import dev.weaverlib.sol.SolCombat;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class SolHandsMixin<T extends LivingEntity> extends HumanoidModel<T> {
    private SolHandsMixin(net.minecraft.client.model.geom.ModelPart root) {
        super(root);
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void weaverlib$handsTogether(T entity, float swing, float speed, float age,
            float yaw, float pitch, CallbackInfo callback) {
        // The first-person renderer sets up its arms with an animation age of zero.
        if (age == 0 || !(entity instanceof Player player) || !SolCombat.view(player).handsTogether()) {
            return;
        }
        float aimYaw = Mth.clamp(yaw * Mth.DEG_TO_RAD, -0.7F, 0.7F);
        float aimPitch = -Mth.HALF_PI + pitch * Mth.DEG_TO_RAD;
        leftArm.setRotation(aimPitch, aimYaw + 0.40F, 0);
        rightArm.setRotation(aimPitch, aimYaw - 0.40F, 0);
        PlayerModel<?> model = (PlayerModel<?>) (Object) this;
        model.leftSleeve.copyFrom(leftArm);
        model.rightSleeve.copyFrom(rightArm);
    }
}
