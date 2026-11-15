package dev.weaverlib;

import com.leclowndu93150.thaumaturge.api.warp.WarpHelper;
import com.leclowndu93150.thaumaturge.api.warp.WarpType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.Codec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

final class WarpUnlock {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, WeaverLib.ID);
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> UNLOCKED =
        ATTACHMENTS.register("warp_casting_unlocked",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).copyOnDeath().build());

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
            "Set to 0 to allow casting without earning the unlock."
        ).defineInRange("requiredWarp", 50, 0, Integer.MAX_VALUE);
        CONFIG = builder.build();
    }

    private WarpUnlock() {}

    static void register(IEventBus modBus, ModContainer container) {
        ATTACHMENTS.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, CONFIG);
        NeoForge.EVENT_BUS.addListener(WarpUnlock::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(WarpUnlock::registerCommand);
    }

    private static void registerCommand(RegisterCommandsEvent event) {
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

    private static int setAccess(CommandSourceStack source, ServerPlayer target, String action)
            throws CommandSyntaxException {
        boolean unlocked = switch (action) {
            case "lock" -> false;
            case "unlock" -> true;
            default -> throw BAD_ACTION.create();
        };
        target.setData(UNLOCKED, unlocked);
        target.sendSystemMessage(Component.translatable(unlocked
            ? "weaverlib.warp_unlocked" : "weaverlib.warp_manual_lock"));
        if (source.getEntity() != target) {
            source.sendSuccess(() -> Component.translatable(unlocked
                ? "weaverlib.warp_command_unlocked" : "weaverlib.warp_command_locked", target.getDisplayName()), false);
        }
        return 1;
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.isAlive()
                && !player.isSpectator() && player.tickCount % 20 == 0) {
            check(player, false);
        }
    }

    static boolean check(ServerPlayer player, boolean showProgress) {
        int required = REQUIRED_WARP.get();
        // An absent attachment means natural progression. Stored false means an explicit operator lock.
        if (player.hasData(UNLOCKED)) {
            boolean unlocked = player.getData(UNLOCKED);
            if (!unlocked && showProgress) {
                player.displayClientMessage(Component.translatable("weaverlib.warp_manual_lock"), true);
            }
            return unlocked;
        }
        if (required == 0) {
            return true;
        }

        var warp = WarpHelper.getWarp(player);
        long current = (long) warp.get(WarpType.NORMAL) + warp.get(WarpType.PERMANENT);
        if (current >= required) {
            player.setData(UNLOCKED, true);
            player.sendSystemMessage(Component.translatable("weaverlib.warp_unlocked"));
            return true;
        }
        if (showProgress) {
            player.displayClientMessage(Component.translatable("weaverlib.warp_locked", current, required), true);
        }
        return false;
    }
}
