package dev.weaverlib.angel;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

final class PrimeToken extends Item {
    PrimeToken(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
            InteractionHand hand) {
        if (!(target instanceof Player)) return InteractionResult.PASS;
        if (player instanceof ServerPlayer prime && target instanceof ServerPlayer recipient) {
            return AngelProgress.bless(stack, prime, recipient);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (data.hasUUID("PrimeOwner")) {
            lines.add(Component.translatable("weaverlib.angel.token_owner", data.getString("PrimeName")));
        } else {
            lines.add(Component.translatable("weaverlib.angel.token_unbound"));
        }
        lines.add(Component.translatable("weaverlib.angel.token_hint"));
        lines.add(Component.translatable("weaverlib.angel.token_limit"));
    }
}
