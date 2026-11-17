package dev.weaverlib.compat;

import com.Polarice3.Goety.api.items.magic.ITotem;
import com.Polarice3.Goety.api.items.magic.IWand;
import com.Polarice3.Goety.api.magic.ISpell;
import com.Polarice3.Goety.common.events.spell.ChangeSoulEnergyEvent;
import com.Polarice3.Goety.common.events.spell.GoetyEventFactory;
import com.Polarice3.Goety.common.items.magic.DarkWand;
import com.Polarice3.Goety.common.magic.spells.necromancy.SkeletonSpell;
import com.Polarice3.Goety.utils.SEHelper;
import com.Polarice3.Goety.utils.LichdomHelper;
import com.Polarice3.Goety.utils.TotemFinder;
import com.Polarice3.Goety.utils.WandUtil;
import com.Vivideru.Goety.common.magic.SpellTypeDamage;
import dev.weaverlib.WeaverLib;
import dev.weaverlib.WarpUnlock;
import dev.weaverlib.spell.WarpCasting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class GoetyCasting {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, WeaverLib.ID);
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Cast>> PENDING =
        ATTACHMENTS.register("goety_cast", () -> AttachmentType.builder(() -> Cast.EMPTY).build());

    private GoetyCasting() {}

    public static void register(IEventBus bus) {
        ATTACHMENTS.register(bus);
        NeoForge.EVENT_BUS.addListener(GoetyCasting::tick);
        NeoForge.EVENT_BUS.addListener(GoetyCasting::onDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, GoetyCasting::restoreFromEnergy);
    }

    public static boolean isLich(ServerPlayer player) {
        return LichdomHelper.isLich(player);
    }

    public static void start(ServerPlayer player) {
        if (player.hasData(PENDING) || player.isUsingItem()) {
            message(player, "goety_busy");
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof DarkWand wand) || !(wand.getSpell(stack) instanceof SkeletonSpell spell)) {
            message(player, "goety_focus");
            return;
        }
        ISpell started = GoetyEventFactory.onStartSpell(player, stack, spell);
        if (!(started instanceof SkeletonSpell) || wand.cannotCast(player, stack, started)) {
            message(player, "goety_blocked");
            return;
        }
        wand.setSpellConditions(started, stack, player);
        if (!canPay(player, cost(wand, player, stack))) {
            message(player, "goety_soul_low");
            return;
        }
        started.startSpell(player.serverLevel(), player, stack, WandUtil.getStats(player, started));
        var sound = started.CastingSound(player, stack);
        if (sound != null) {
            player.serverLevel().playSound(null, player, sound, SoundSource.PLAYERS,
                started.castingVolume(), started.castingPitch());
        }
        player.setData(PENDING, new Cast(player.serverLevel(), stack, IWand.getFocus(stack).copy(),
            0, Math.max(1, started.castDuration(player, stack))));
        message(player, "goety_casting");
    }

    private static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.hasData(PENDING)) {
            return;
        }
        Cast cast = player.getData(PENDING);
        if (!player.isAlive() || player.isSpectator() || player.serverLevel() != cast.level()
                || player.getMainHandItem() != cast.stack() || player.isUsingItem()
                || !WarpUnlock.check(player, false)
                || !ItemStack.matches(IWand.getFocus(cast.stack()), cast.focus())
                || !(cast.stack().getItem() instanceof DarkWand wand)
                || !(wand.getSpell(cast.stack()) instanceof SkeletonSpell skeleton)) {
            player.removeData(PENDING);
            message(player, "goety_interrupted");
            return;
        }

        ISpell spell = GoetyEventFactory.onCastingSpell(player, cast.stack(), skeleton, cast.ticks() + 1);
        if (!(spell instanceof SkeletonSpell) || wand.isOnCooldown(player, cast.stack())) {
            player.removeData(PENDING);
            message(player, "goety_blocked");
            return;
        }
        SpellTypeDamage.cast(spell, () -> spell.useSpell(cast.level(), player, cast.stack(), cast.ticks() + 1,
            WandUtil.getStats(player, spell)));
        if (cast.ticks() + 1 < cast.duration()) {
            player.setData(PENDING, new Cast(cast.level(), cast.stack(), cast.focus(), cast.ticks() + 1,
                cast.duration()));
            return;
        }
        player.removeData(PENDING);
        finish(player, wand, cast, spell);
    }

    private static void finish(ServerPlayer player, DarkWand wand, Cast cast, ISpell casting) {
        ItemStack stack = cast.stack();
        ISpell spell = GoetyEventFactory.onCastSpell(player, casting);
        if (!(spell instanceof SkeletonSpell) || wand.cannotCast(player, stack, spell)
                || !spell.conditionsMet(cast.level(), player, WandUtil.getStats(player, spell))) {
            message(player, "goety_blocked");
            return;
        }
        wand.setSpellConditions(spell, stack, player);
        if (!pay(player, cost(wand, player, stack))) {
            message(player, "goety_soul_low");
            return;
        }
        SpellTypeDamage.cast(spell, () -> spell.SpellResult(cast.level(), player, stack, WandUtil.getStats(player, spell)));
        int cooldown = spell.spellCooldown(player);
        if (!spell.hasCustomCooldown(player, stack, cast.focus(), cooldown)) {
            SEHelper.addCooldown(player, cast.focus().getItem(), cooldown);
        }
    }

    private static int cost(DarkWand wand, Player player, ItemStack stack) {
        // Dark Wand's native deduction applies the armor discount in decreaseSouls as well as SoulUse.
        return player.isCreative() ? 0 : Math.max(0, (int) (wand.SoulUse(player, stack) * SEHelper.soulDiscount(player)));
    }

    private static boolean canPay(ServerPlayer player, int cost) {
        return SoulPayment.canSpend(SoulPayment.shortfall(cost, SEHelper.getSoulAmountInt(player)),
            SoulIntegrity.percent(player));
    }

    private static boolean pay(ServerPlayer player, int cost) {
        if (cost <= 0) {
            return true;
        }
        var loss = new ChangeSoulEnergyEvent.Loss(player, cost);
        if (NeoForge.EVENT_BUS.post(loss).isCanceled()) {
            return false;
        }
        int due = Math.max(0, loss.getSoulChange());
        int stored = Math.max(0, SEHelper.getSoulAmountInt(player));
        int ownSoul = SoulPayment.shortfall(due, stored);
        int remaining = SoulIntegrity.percent(player);
        if (!SoulPayment.canSpend(ownSoul, remaining)) {
            return false;
        }
        int external = due - ownSoul;
        if (external > 0) {
            if (SEHelper.getSEActive(player)) {
                if (!SEHelper.decreaseSESouls(player, external)) {
                    return false;
                }
                SEHelper.sendSEUpdatePacket(player);
            } else {
                ItemStack totem = TotemFinder.FindTotem(player);
                if (totem.isEmpty() || ITotem.currentSouls(totem) < external) {
                    return false;
                }
                ITotem.decreaseSouls(totem, external);
                SEHelper.sendSoulChange(player, -external);
            }
        }
        if (ownSoul > 0) {
            SoulIntegrity.spend(player, ownSoul);
            WarpCasting.addTemporaryWarp(player, SoulPayment.warpGain(remaining, ownSoul));
        }
        return true;
    }

    private static void onDamage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getNewDamage() > 0 && player.hasData(PENDING)) {
            player.removeData(PENDING);
            message(player, "goety_interrupted");
        }
    }

    private static void restoreFromEnergy(ChangeSoulEnergyEvent.Gain event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getSoulChange() > 0) {
            event.setSoulChange(event.getSoulChange() - SoulIntegrity.restore(player, event.getSoulChange()));
        }
    }

    private static void message(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("weaverlib." + key), true);
    }

    private record Cast(ServerLevel level, ItemStack stack, ItemStack focus, int ticks, int duration) {
        private static final Cast EMPTY = new Cast(null, ItemStack.EMPTY, ItemStack.EMPTY, 0, 0);
    }
}
