package dev.weaverlib.mixin;

import com.leclowndu93150.thaumaturge.api.spell.Spell;
import com.leclowndu93150.thaumaturge.api.spell.cast.SpellState;
import com.leclowndu93150.thaumaturge.api.spell.cast.SpellTarget;
import com.leclowndu93150.thaumaturge.api.spell.cast.SpellStat;
import com.leclowndu93150.thaumaturge.content.spell.engine.SpellEngine;
import dev.weaverlib.spell.Inscriptions;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = SpellEngine.class, remap = false)
public abstract class SpellEngineMixin {
    @Redirect(method = "execute", at = @At(value = "INVOKE",
        target = "Lcom/leclowndu93150/thaumaturge/api/spell/cast/SpellState;with(Lcom/leclowndu93150/thaumaturge/api/spell/cast/SpellStat;F)Lcom/leclowndu93150/thaumaturge/api/spell/cast/SpellState;"))
    private static SpellState weaverlib$carryInscription(SpellState state, SpellStat stat, float value,
            LivingEntity caster, ItemStack source, Spell spell, List<SpellTarget> targets, float power) {
        return Inscriptions.tagSource(state.with(stat, value), source, spell);
    }
}
