package dev.weaverlib;

import com.leclowndu93150.thaumaturge.api.aura.AuraHelper;
import com.leclowndu93150.thaumaturge.api.transaction.Transaction;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.weaverlib.rune.Weave;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(WeaverLib.ID)
public final class WeaverLib {
    public static final String ID = "weaverlib";
    private final Map<UUID, Integer> nextAttempt = new HashMap<>();

    public WeaverLib(IEventBus modBus) {
        modBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::onLogout);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("incant")
            .requires(source -> source.getEntity() instanceof ServerPlayer)
            .then(Commands.argument("words", StringArgumentType.greedyString())
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    if (!beginAttempt(player)) {
                        return 0;
                    }
                    Weave spell = Weave.fromWords(StringArgumentType.getString(context, "words"));
                    return cast(player, spell, 1.0) ? 1 : 0;
                })));
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(RuneCast.TYPE, RuneCast.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player && beginAttempt(player)) {
                var match = Weave.recognize(payload.strokes());
                if (match.template() == null) {
                    message(player, "unknown_rune");
                    return;
                }
                cast(player, Weave.fromWords(match.template().id()), match.potency(.5));
            }
        });
    }

    private void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        nextAttempt.remove(event.getEntity().getUUID());
    }

    private boolean beginAttempt(ServerPlayer player) {
        int now = player.serverLevel().getServer().getTickCount();
        Integer until = nextAttempt.get(player.getUUID());
        if (!player.isAlive() || player.isSpectator() || (until != null && now - until < 0)) {
            return false;
        }
        nextAttempt.put(player.getUUID(), now + 20);
        return true;
    }

    private boolean cast(ServerPlayer player, Weave spell, double potency) {
        if (spell == null) {
            message(player, "unknown_words");
            return false;
        }

        int duration = (int) (1200 * potency);
        MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
        if ((spell == Weave.SANA && player.getHealth() >= player.getMaxHealth())
                || (spell == Weave.LUX && current != null
                    && (current.isInfiniteDuration() || current.getDuration() >= duration
                        || current.getAmplifier() > 0))) {
            message(player, "no_effect");
            return false;
        }

        try (Transaction payment = Transaction.openRoot()) {
            float drained = AuraHelper.drainVis(player.serverLevel(), player.blockPosition(), spell.cost(), payment);
            if (!(drained >= spell.cost())) {
                message(player, "insufficient_vis", spell.cost());
                return false;
            }
            payment.commit();
        }

        switch (spell) {
            case LUX -> player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, duration, 0, false, false));
            case SANA -> player.heal((float) (4 * potency));
        }
        message(player, "cast", spell.word(), spell.cost());
        return true;
    }

    private static void message(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("weaverlib." + key, args), true);
    }
}
