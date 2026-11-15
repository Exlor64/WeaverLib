package dev.weaverlib.spell;

import com.leclowndu93150.thaumaturge.api.aura.AuraHelper;
import com.leclowndu93150.thaumaturge.api.spell.cast.CastContext;
import com.leclowndu93150.thaumaturge.api.transaction.Transaction;
import dev.weaverlib.WeaverLib;
import java.lang.ref.WeakReference;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

final class LeapVis {
    private static final float VIS_PER_PREVIOUS_LEAP = 3.0F;
    private static final int TAKEOFF_GRACE_TICKS = 4;
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, WeaverLib.ID);
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Streak>> STREAK =
        ATTACHMENTS.register("leap_streak", () -> AttachmentType.builder(Streak::new).build());

    private LeapVis() {}

    static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        NeoForge.EVENT_BUS.addListener(LeapVis::onTick);
        NeoForge.EVENT_BUS.addListener(LeapVis::onDimensionChange);
    }

    static boolean payForActivation(CastContext context) {
        if (!(context.caster() instanceof ServerPlayer caster)) {
            return true;
        }
        if (!caster.isAlive() || caster.isSpectator()) {
            return false;
        }
        Streak streak = caster.getData(STREAK);
        if (streak.lastActivation.get() == context) {
            return streak.paid;
        }

        boolean grounded = caster.onGround();
        float surcharge = streak.surcharge(grounded);
        streak.lastActivation = new WeakReference<>(context);
        streak.paid = false;
        if (surcharge > 0) {
            try (Transaction payment = Transaction.openRoot()) {
                float drained = AuraHelper.drainVis(caster.serverLevel(), caster.blockPosition(), surcharge, payment);
                if (!(drained >= surcharge)) {
                    if (!EternalSpells.stopUnpaidLeap(caster, context.castId())) {
                        caster.displayClientMessage(Component.translatable("weaverlib.leap_airborne_vis", surcharge), true);
                    }
                    return false;
                }
                payment.commit();
            }
        }
        streak.paid = true;
        streak.recordUse(grounded, caster.tickCount);
        return true;
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.hasData(STREAK)) {
            return;
        }
        Streak streak = player.getData(STREAK);
        if (!player.isAlive() || streak.shouldReset(player.onGround(), player.tickCount)) {
            player.removeData(STREAK);
        } else if (!player.onGround()) {
            streak.sawAirborne = true;
        }
    }

    private static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.removeData(STREAK);
        }
    }

    private static final class Streak {
        private WeakReference<CastContext> lastActivation = new WeakReference<>(null);
        private boolean paid;
        private int uses;
        private boolean sawAirborne;
        private int lastUseTick;

        private float surcharge(boolean grounded) {
            return grounded ? 0 : uses * VIS_PER_PREVIOUS_LEAP;
        }

        private void recordUse(boolean grounded, int tick) {
            if (grounded) {
                uses = 1;
            } else if (uses < Integer.MAX_VALUE) {
                uses++;
            }
            sawAirborne = !grounded;
            lastUseTick = tick;
        }

        private boolean shouldReset(boolean grounded, int tick) {
            return grounded && (sawAirborne || tick - lastUseTick >= TAKEOFF_GRACE_TICKS);
        }
    }
}
