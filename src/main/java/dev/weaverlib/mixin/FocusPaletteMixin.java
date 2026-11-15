package dev.weaverlib.mixin;

import com.leclowndu93150.thaumaturge.api.spell.part.SpellPart;
import com.leclowndu93150.thaumaturge.client.screen.casters.focal.FocalLayout;
import com.leclowndu93150.thaumaturge.client.screen.casters.focal.PartPalette;
import dev.weaverlib.WeaverLib;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PartPalette.class)
abstract class FocusPaletteMixin {
    @Shadow private List<ResourceKey<SpellPart>> entries;
    @Shadow private int scroll;

    @Shadow
    private int maxScroll(FocalLayout layout) {
        throw new AssertionError();
    }

    @Inject(method = "refresh", at = @At("RETURN"))
    private void weaverlib$omitWeaverParts(CallbackInfo ci) {
        entries = entries.stream().filter(key -> !WeaverLib.ID.equals(key.location().getNamespace())).toList();
        scroll = Math.min(scroll, maxScroll(null));
    }
}
