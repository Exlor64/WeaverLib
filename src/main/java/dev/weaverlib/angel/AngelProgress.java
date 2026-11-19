package dev.weaverlib.angel;

import dev.weaverlib.WeaverLib;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AngelProgress {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(WeaverLib.ID);
    private static final DeferredItem<PrimeToken> TOKEN = ITEMS.registerItem("prime_angel_token", PrimeToken::new,
        new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

    private AngelProgress() {}

    static void register(IEventBus bus) {
        ITEMS.register(bus);
        NeoForge.EVENT_BUS.addListener(AngelProgress::advanced);
    }

    static boolean counted(AdvancementHolder advancement) {
        return advancement.value().display().isPresent()
            && !advancement.id().getPath().startsWith("recipes/");
    }

    public static int advancements(ServerPlayer player) {
        int completed = 0;
        for (var advancement : player.server.getAdvancements().getAllAdvancements()) {
            if (counted(advancement) && player.getAdvancements().getOrStartProgress(advancement).isDone()) {
                completed++;
            }
        }
        return completed;
    }

    public static int points(ServerPlayer player) {
        return AngelLevels.points(advancements(player), Angels.data(player).getInt("Blessings"));
    }

    static void refresh(ServerPlayer player) {
        int current = Angels.rank(player);
        if (current == 0 || Angels.data(player).contains("StageOverride")) return;
        int total = points(player);
        int rank = Math.max(current, AngelLevels.rankAt(total));
        if (rank > current) {
            Angels.data(player).putInt("Rank", rank);
            player.sendSystemMessage(Component.translatable("weaverlib.angel.promoted",
                Component.translatable("weaverlib.angel.rank." + rank), total));
        }
        Angels.sync(player);
    }

    private static void advanced(AdvancementEvent.AdvancementProgressEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && counted(event.getAdvancement())) refresh(player);
    }

    public static boolean appointPrime(ServerPlayer player) {
        if (Angels.rank(player) == 0 && !Angels.awaken(player)) return false;
        Angels.data(player).putBoolean("Prime", true);
        ItemStack token = new ItemStack(TOKEN.get());
        CustomData.update(DataComponents.CUSTOM_DATA, token, tag -> {
            tag.putUUID("PrimeOwner", player.getUUID());
            tag.putString("PrimeName", player.getName().getString());
        });
        if (!player.getInventory().add(token)) player.drop(token, false);
        player.sendSystemMessage(Component.translatable("weaverlib.angel.prime_granted"));
        return true;
    }

    static InteractionResult bless(ItemStack token, ServerPlayer prime, ServerPlayer recipient) {
        var binding = token.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!Angels.data(prime).getBoolean("Prime") || Angels.rank(prime) == 0
                || !binding.hasUUID("PrimeOwner") || !binding.getUUID("PrimeOwner").equals(prime.getUUID())) {
            prime.sendSystemMessage(Component.translatable("weaverlib.angel.token_bound"));
            return InteractionResult.FAIL;
        }
        if (prime == recipient || Angels.rank(recipient) == 0 || !prime.isAlive() || !recipient.isAlive()
                || prime.isSpectator() || recipient.isSpectator()) {
            prime.sendSystemMessage(Component.translatable("weaverlib.angel.bless_target"));
            return InteractionResult.FAIL;
        }
        long day = Math.floorDiv(prime.server.overworld().getDayTime(), 24000);
        var data = Angels.data(prime);
        if (!AngelLevels.dayReady(day, data.contains("LastBlessDay"), data.getLong("LastBlessDay"))) {
            prime.sendSystemMessage(Component.translatable("weaverlib.angel.bless_used"));
            return InteractionResult.FAIL;
        }
        int sourcePoints = points(prime);
        int targetPoints = points(recipient);
        if (!AngelLevels.mayBless(sourcePoints, targetPoints)) {
            prime.sendSystemMessage(Component.translatable("weaverlib.angel.bless_cap", sourcePoints));
            return InteractionResult.FAIL;
        }
        var targetData = Angels.data(recipient);
        int gained = Math.max(0, targetData.getInt("Blessings"));
        targetData.putInt("Blessings", gained + 1);
        data.putLong("LastBlessDay", day);
        refresh(recipient);
        recipient.sendSystemMessage(Component.translatable("weaverlib.angel.bless_received", prime.getDisplayName(),
            targetPoints + 1));
        prime.sendSystemMessage(Component.translatable("weaverlib.angel.bless_given", recipient.getDisplayName(),
            targetPoints + 1));
        return InteractionResult.SUCCESS;
    }

    public static void status(ServerPlayer player) {
        int completed = advancements(player);
        int bonus = Math.max(0, Angels.data(player).getInt("Blessings"));
        player.sendSystemMessage(Component.translatable("weaverlib.angel.progress", completed, bonus,
            AngelLevels.points(completed, bonus), Component.translatable("weaverlib.angel.rank." + Angels.rank(player))));
        if (Angels.data(player).contains("StageOverride")) {
            player.sendSystemMessage(Component.translatable("weaverlib.angel.stage_override"));
        }
    }
}
