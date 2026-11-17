package dev.weaverlib.compat;

import com.mojang.serialization.Codec;
import dev.weaverlib.WeaverLib;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class SoulIntegrity {
    private static final int FULL = 100;
    private static final int RECOVERY_TICKS = 600;
    private static final ResourceLocation PENALTY = ResourceLocation.fromNamespaceAndPath(WeaverLib.ID, "soul_loss");
    private static final List<Holder<Attribute>> SCALED =
        List.of(Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE, Attributes.MOVEMENT_SPEED, Attributes.ATTACK_SPEED);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, WeaverLib.ID);
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> SOUL =
        ATTACHMENTS.register("personal_soul", () -> AttachmentType.builder(() -> FULL)
            .serialize(Codec.intRange(1, FULL)).copyOnDeath()
            .sync((holder, player) -> holder == player, ByteBufCodecs.VAR_INT).build());
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> RECOVERY =
        ATTACHMENTS.register("personal_soul_recovery", () -> AttachmentType.builder(() -> RECOVERY_TICKS).build());

    private SoulIntegrity() {}

    public static void register(IEventBus bus) {
        ATTACHMENTS.register(bus);
        NeoForge.EVENT_BUS.addListener(SoulIntegrity::tick);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> reapply(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> reapply(event.getEntity()));
    }

    public static int percent(Player player) {
        return player.getExistingData(SOUL).orElse(FULL);
    }

    static void spend(ServerPlayer player, int amount) {
        int current = percent(player);
        if (current == FULL) {
            player.setData(RECOVERY, RECOVERY_TICKS);
        }
        update(player, current - amount);
    }

    static int restore(ServerPlayer player, int amount) {
        int current = percent(player);
        int recovered = Math.min(amount, FULL - current);
        if (recovered > 0) {
            update(player, current + recovered);
        }
        return recovered;
    }

    private static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator()
                || percent(player) == FULL) {
            return;
        }
        int ticks = player.getData(RECOVERY) - 1;
        if (ticks == 0) {
            restore(player, 1);
            ticks = RECOVERY_TICKS;
        }
        player.setData(RECOVERY, ticks);
    }

    private static void update(ServerPlayer player, int value) {
        player.setData(SOUL, value);
        reapply(player);
        player.displayClientMessage(Component.translatable("weaverlib.soul_percent", value), true);
    }

    private static void reapply(Player entity) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        int current = percent(player);
        for (Holder<Attribute> attribute : SCALED) {
            var instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            instance.removeModifier(PENALTY);
            if (current < FULL) {
                instance.addTransientModifier(new AttributeModifier(PENALTY, (current - FULL) / 100.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }
}
