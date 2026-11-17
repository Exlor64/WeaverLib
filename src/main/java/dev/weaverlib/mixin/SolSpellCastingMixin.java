package dev.weaverlib.mixin;

import com.leclowndu93150.thaumaturge.api.spell.SpellSummary;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.content.spell.casting.SpellCasting;
import com.leclowndu93150.thaumaturge.content.spell.item.FocusItems;
import dev.weaverlib.sol.Sol;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = SpellCasting.class, remap = false)
public abstract class SolSpellCastingMixin {
    @Redirect(method = "affordable", at = @At(value = "INVOKE",
        target = "Lcom/leclowndu93150/thaumaturge/content/spell/item/FocusItems;visSplit(Lcom/leclowndu93150/thaumaturge/api/spell/SpellSummary;FLnet/minecraft/core/HolderLookup$Provider;)Ljava/util/Map;"))
    private static Map<ResourceKey<IAspect>, Integer> weaverlib$affordable(SpellSummary summary, float factor,
            HolderLookup.Provider registries, Player player, ItemStack wand, SpellSummary spellSummary,
            float originalFactor) {
        return FocusItems.visSplit(summary, player instanceof ServerPlayer caster
            ? Sol.visCost(caster, factor) : factor, registries);
    }
}
