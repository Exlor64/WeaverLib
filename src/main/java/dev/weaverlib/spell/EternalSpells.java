package dev.weaverlib.spell;

import com.leclowndu93150.thaumaturge.api.aura.AuraHelper;
import com.leclowndu93150.thaumaturge.api.spell.Spell;
import com.leclowndu93150.thaumaturge.api.spell.SpellNode;
import com.leclowndu93150.thaumaturge.api.spell.Spells;
import com.leclowndu93150.thaumaturge.api.spell.cast.CastContext;
import com.leclowndu93150.thaumaturge.api.spell.cast.SpellContinuation;
import com.leclowndu93150.thaumaturge.api.spell.cast.SpellTarget;
import com.leclowndu93150.thaumaturge.api.spell.event.SpellNodeEvent;
import com.leclowndu93150.thaumaturge.api.transaction.Transaction;
import dev.weaverlib.WeaverLib;
import dev.weaverlib.rune.FocusComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class EternalSpells {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, WeaverLib.ID);
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Session>> SESSION =
        ATTACHMENTS.register("eternal_spell", () -> AttachmentType.builder(Session::new).build());

    private EternalSpells() {}

    static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EternalSpells::onNode);
        NeoForge.EVENT_BUS.addListener(EternalSpells::onTick);
        NeoForge.EVENT_BUS.addListener(EternalSpells::onDamage);
        NeoForge.EVENT_BUS.addListener(EternalSpells::onDimensionChange);
    }

    private static void onNode(SpellNodeEvent event) {
        CastContext ctx = event.context();
        if (!ctx.partKey().equals(Spell.ORIGIN) || !(ctx.caster() instanceof ServerPlayer player)) {
            return;
        }
        var markers = new ArrayList<SpellNode>();
        ctx.node().forEach(node -> {
            if (node.part().equals(FocusComponent.named("eternal").key())) {
                markers.add(node);
            }
        });
        if (markers.size() == 1) {
            // A later hit from a carrier can start this loop only while its original cast is still authorized.
            player.setData(SESSION, new Session(ctx.castId(), ctx.level()));
        }
    }

    static void activate(CastContext ctx, List<SpellTarget> targets) {
        if (!(ctx.caster() instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator()
                || targets.isEmpty() || !ctx.hasNext()) {
            return;
        }
        Session session = player.getExistingData(SESSION).orElse(null);
        if (session == null || session.level != ctx.level() || !ctx.castId().equals(session.castId)) {
            return;
        }
        if (session.pulse == null) {
            SpellContinuation continuation = ctx.continuation();
            var subtree = Spell.empty().withRoot(SpellNode.of(Spell.ORIGIN).withChildren(continuation.nodes()));
            var summary = Spells.analyze(subtree, null, ctx.registries(), player);
            float vis = summary.vis();
            if (!summary.valid() || !Float.isFinite(vis) || vis < 0) {
                player.removeData(SESSION);
                return;
            }
            int interval = Math.clamp(ctx.setting("interval"), 10, 100);
            session.pulse = new Pulse(continuation, List.copyOf(targets), interval, Math.max(1, vis));
            session.nextTick = ctx.level().getGameTime() + interval;
            notify(player, "eternal_started");
        }
        ctx.proceed(targets);
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Session session = player.getExistingData(SESSION).orElse(null);
        if (session == null) {
            return;
        }
        if (!player.isAlive() || player.isSpectator() || player.serverLevel() != session.level) {
            player.removeData(SESSION);
            return;
        }
        Pulse pulse = session.pulse;
        if (pulse == null || session.level.getGameTime() < session.nextTick) {
            return;
        }
        var targets = pulse.targets().stream().filter(target -> validTarget(session.level, target)).toList();
        if (targets.isEmpty()) {
            player.removeData(SESSION);
            notify(player, "eternal_target_gone");
            return;
        }

        try (Transaction payment = Transaction.openRoot()) {
            float drained = AuraHelper.drainVis(session.level, player.blockPosition(), pulse.cost(), payment);
            if (!(drained >= pulse.cost())) {
                player.removeData(SESSION);
                notify(player, "eternal_no_vis");
                return;
            }
            payment.commit();
        }

        session.nextTick = session.level.getGameTime() + pulse.interval();
        var continuation = pulse.continuation();
        // A paid pulse gets its own native touch budget.
        Spells.resume(session.level, new SpellContinuation(continuation.nodes(), continuation.state(),
            continuation.caster(), UUID.randomUUID()), targets);
    }

    private static boolean validTarget(ServerLevel level, SpellTarget target) {
        Entity entity = target.entity().orElse(null);
        if (entity != null) {
            return entity.level() == level && entity.isAlive() && !entity.isSpectator();
        }
        var block = target.blockPos();
        return level.getChunkSource().hasChunk(block.getX() >> 4, block.getZ() >> 4);
    }

    private static void onDamage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getNewDamage() > 0
                && player.removeData(SESSION) != null) {
            notify(player, "eternal_interrupted");
        }
    }

    private static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.removeData(SESSION);
        }
    }

    public static boolean isSustaining(ServerPlayer player) {
        return player.hasData(SESSION);
    }

    public static void clear(ServerPlayer player) {
        player.removeData(SESSION);
    }

    public static boolean halt(ServerPlayer player) {
        if (player.removeData(SESSION) == null) {
            return false;
        }
        notify(player, "eternal_halted");
        return true;
    }

    private static void notify(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("weaverlib." + key), true);
    }

    private static final class Session {
        private final UUID castId;
        private final ServerLevel level;
        private Pulse pulse;
        private long nextTick;

        private Session() {
            this(null, null);
        }

        private Session(UUID castId, ServerLevel level) {
            this.castId = castId;
            this.level = level;
        }
    }

    private record Pulse(SpellContinuation continuation, List<SpellTarget> targets, int interval, float cost) {}
}
