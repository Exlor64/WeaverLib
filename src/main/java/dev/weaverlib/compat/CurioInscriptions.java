package dev.weaverlib.compat;

import java.util.function.Predicate;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

public final class CurioInscriptions {
    private CurioInscriptions() {}

    public static ItemStack find(Player player, Predicate<ItemStack> predicate) {
        return CuriosApi.getCuriosInventory(player).flatMap(handler ->
            handler.findCurios(predicate).stream()
                .filter(result -> !result.slotContext().cosmetic()
                    && handler.isSlotActive(result.slotContext().identifier(), result.slotContext().index()))
                .findFirst()
                .map(SlotResult::stack)).orElse(ItemStack.EMPTY);
    }
}
