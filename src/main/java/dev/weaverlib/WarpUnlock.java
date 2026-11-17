package dev.weaverlib;

import com.leclowndu93150.thaumaturge.api.warp.WarpHelper;
import dev.weaverlib.sol.Sol;
import com.leclowndu93150.thaumaturge.api.warp.WarpType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.Codec;
import dev.weaverlib.spell.EternalSpells;
import dev.weaverlib.compat.GoetyCasting;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class WarpUnlock {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, WeaverLib.ID);
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> UNLOCKED =
        ATTACHMENTS.register("warp_casting_unlocked",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).copyOnDeath().build());

    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> CANVAS_ACCESS =
        ATTACHMENTS.register("canvas_access", () -> AttachmentType.builder(() -> false)
            .sync((holder, player) -> holder == player, ByteBufCodecs.BOOL).build());
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> ALL_RUNES =
        ATTACHMENTS.register("all_runes_unlocked", () -> AttachmentType.builder(() -> false)
            .serialize(Codec.BOOL).copyOnDeath()
            .sync((holder, player) -> holder == player, ByteBufCodecs.BOOL).build());

    // The action bar is one line wide, so show the two sentences in sequence.
    private static final int REVELATION_LINE_TICKS = 55;
    private record Revelation(int showAt, boolean closingLine, boolean lich) {}

    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Revelation>> REVELATION =
        ATTACHMENTS.register("warp_revelation",
            () -> AttachmentType.builder(() -> new Revelation(0, false, false)).build());

    private static final SimpleCommandExceptionType BAD_ACTION =
        new SimpleCommandExceptionType(Component.translatable("weaverlib.warp_bad_action"));
    private static final DynamicCommandExceptionType PLAYER_OFFLINE =
        new DynamicCommandExceptionType(name -> Component.translatable("weaverlib.warp_player_offline", name));

    private static final ModConfigSpec CONFIG;
    private static final ModConfigSpec.IntValue REQUIRED_WARP;
    static {
        var builder = new ModConfigSpec.Builder();
        REQUIRED_WARP = builder.comment(
            "Normal plus Permanent Thaumaturge Warp required to permanently learn rune and word casting.",
            "Goety lichdom also unlocks casting when Goety is installed.",
            "Set to 0 to allow casting without earning the unlock."
        ).defineInRange("requiredWarp", 50, 0, Integer.MAX_VALUE);
        CONFIG = builder.build();
    }

    private WarpUnlock() {}

    static void register(IEventBus modBus, ModContainer container) {
        ATTACHMENTS.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, CONFIG);
        NeoForge.EVENT_BUS.addListener(WarpUnlock::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(WarpUnlock::onLogin);
        NeoForge.EVENT_BUS.addListener(WarpUnlock::onRespawn);
        NeoForge.EVENT_BUS.addListener(WarpUnlock::registerCommand);
    }

    public static boolean canOpenCanvas(Player player) {
        return player.getExistingData(CANVAS_ACCESS).orElse(false);
    }

    public static boolean hasAllRunes(Player player) {
        return player.getExistingData(ALL_RUNES).orElse(false);
    }

    private static void syncCanvasAccess(ServerPlayer player) {
        boolean unlocked = check(player, false);
        if (canOpenCanvas(player) != unlocked) {
            player.setData(CANVAS_ACCESS, unlocked);
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncCanvasAccess(player);
        }
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncCanvasAccess(player);
        }
    }

    private static void registerCommand(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("weaverrunes")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("unlockall")
                .executes(context -> revealRunes(context.getSource(), context.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(context -> revealRunes(context.getSource(), EntityArgument.getPlayer(context, "player"))))));

        event.getDispatcher().register(Commands.literal("weaverwarp")
            .requires(source -> source.hasPermission(2))
            .then(Commands.argument("action", StringArgumentType.word())
                .executes(context -> {
                    var source = context.getSource();
                    return setAccess(source, source.getPlayerOrException(),
                        StringArgumentType.getString(context, "action"));
                })
                .then(Commands.argument("player", StringArgumentType.word())
                    .executes(context -> {
                        var source = context.getSource();
                        String name = StringArgumentType.getString(context, "player");
                        ServerPlayer player = source.getServer().getPlayerList().getPlayerByName(name);
                        if (player == null) {
                            throw PLAYER_OFFLINE.create(name);
                        }
                        return setAccess(source, player, StringArgumentType.getString(context, "action"));
                    }))));
    }

    private static int revealRunes(CommandSourceStack source, ServerPlayer target) {
        target.setData(ALL_RUNES, true);
        target.sendSystemMessage(Component.translatable("weaverlib.runes_all_unlocked"));
        if (source.getEntity() != target) {
            source.sendSuccess(() -> Component.translatable("weaverlib.runes_all_unlocked_for",
                target.getDisplayName()), false);
        }
        return 1;
    }

    private static int setAccess(CommandSourceStack source, ServerPlayer target, String action)
            throws CommandSyntaxException {
        boolean unlocked = switch (action) {
            case "lock" -> false;
            case "unlock" -> true;
            default -> throw BAD_ACTION.create();
        };
        target.setData(UNLOCKED, unlocked);
        syncCanvasAccess(target);
        if (!unlocked) {
            EternalSpells.clear(target);
        }
        target.removeData(REVELATION);
        target.sendSystemMessage(Component.translatable(unlocked
            ? "weaverlib.warp_unlocked" : "weaverlib.warp_manual_lock"));
        if (source.getEntity() != target) {
            source.sendSuccess(() -> Component.translatable(unlocked
                ? "weaverlib.warp_command_unlocked" : "weaverlib.warp_command_locked", target.getDisplayName()), false);
        }
        return 1;
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.isAlive() && !player.isSpectator()) {
            if (player.tickCount % 20 == 0) {
                syncCanvasAccess(player);
            }
            showRevelation(player);
        }
    }

    private static void showRevelation(ServerPlayer player) {
        if (!player.hasData(REVELATION)) {
            return;
        }
        Revelation line = player.getData(REVELATION);
        if (player.tickCount - line.showAt() < 0) {
            return;
        }
        if (line.closingLine()) {
            var weave = Component.translatable("weaverlib.warp_revelation_weave").withStyle(ChatFormatting.ITALIC);
            player.displayClientMessage(Component.translatable("weaverlib.warp_revelation_pull", weave), true);
            player.removeData(REVELATION);
        } else {
            player.displayClientMessage(Component.translatable(line.lich()
                ? "weaverlib.lich_revelation_change" : "weaverlib.warp_revelation_change"), true);
            player.setData(REVELATION, new Revelation(player.tickCount + REVELATION_LINE_TICKS, true, line.lich()));
        }
    }

    public static boolean check(ServerPlayer player, boolean showProgress) {
        if (Sol.unlocked(player)) {
            if (showProgress) {
                player.displayClientMessage(Component.translatable("weaverlib.sol.weaving_blocked"), true);
            }
            return false;
        }
        int required = REQUIRED_WARP.get();
        // An absent attachment means natural progression. Stored false means an explicit operator lock.
        if (player.hasData(UNLOCKED)) {
            boolean unlocked = player.getData(UNLOCKED);
            if (!unlocked && showProgress) {
                player.displayClientMessage(Component.translatable("weaverlib.warp_manual_lock"), true);
            }
            return unlocked;
        }
        if (WeaverLib.hasGoety() && GoetyCasting.isLich(player)) {
            return grantNaturalUnlock(player, true);
        }
        if (required == 0) {
            return true;
        }

        var warp = WarpHelper.getWarp(player);
        long current = (long) warp.get(WarpType.NORMAL) + warp.get(WarpType.PERMANENT);
        if (current >= required) {
            return grantNaturalUnlock(player, false);
        }
        if (showProgress) {
            player.displayClientMessage(Component.translatable("weaverlib.warp_locked", current, required), true);
        }
        return false;
    }
    private static boolean grantNaturalUnlock(ServerPlayer player, boolean lich) {
        player.setData(UNLOCKED, true);
        player.setData(REVELATION, new Revelation(player.tickCount + 1, false, lich));
        return true;
    }

}
