package dev.weaverlib.sol;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

final class SunStone extends Item {
    SunStone(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer && Sol.unlocked(serverPlayer)) {
            player.displayClientMessage(Component.translatable("weaverlib.sol.already_unlocked"), true);
            return InteractionResultHolder.fail(player.getItemInHand(hand));
        }
        return super.use(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity eater) {
        if (eater instanceof ServerPlayer player) {
            if (Sol.unlocked(player)) {
                return stack;
            }
            Sol.unlock(player);
        }
        return super.finishUsingItem(stack, level, eater);
    }
}
