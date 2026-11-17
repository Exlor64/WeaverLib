package dev.weaverlib.mixin;

import com.Polarice3.Goety.api.magic.ISpell;
import com.Polarice3.Goety.common.magic.SpellStat;
import dev.weaverlib.sol.Sol;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.Polarice3.Goety.utils.WandUtil", remap = false)
public abstract class SolGoetyPotencyMixin {
    @Inject(method = "getStats", at = @At("RETURN"), cancellable = true)
    private static void weaverlib$reducePotency(LivingEntity caster, ISpell spell,
            CallbackInfoReturnable<SpellStat> result) {
        if (caster instanceof ServerPlayer player && Sol.unlocked(player)) {
            SpellStat stats = result.getReturnValue();
            result.setReturnValue(new SpellStat(stats.getPotency() / 2, stats.getDuration(), stats.getRange(),
                stats.getRadius(), stats.getBurning(), stats.getVelocity()));
        }
    }
}
