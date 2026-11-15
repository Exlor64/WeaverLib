package dev.weaverlib;

import com.leclowndu93150.thaumaturge.api.aura.AuraHelper;
import com.leclowndu93150.thaumaturge.api.transaction.Transaction;
import com.leclowndu93150.thaumaturge.api.casters.FocusCaster;
import com.leclowndu93150.thaumaturge.api.spell.FocusTier;
import com.leclowndu93150.thaumaturge.api.spell.Spell;
import com.leclowndu93150.thaumaturge.api.spell.SpellProblem;
import com.leclowndu93150.thaumaturge.api.spell.Spells;
import com.leclowndu93150.thaumaturge.api.spell.cast.SpellTarget;
import com.leclowndu93150.thaumaturge.content.casters.CasterManager;
import com.leclowndu93150.thaumaturge.content.spell.engine.SpellEngine;
import dev.weaverlib.rune.FocusComponent;
import dev.weaverlib.rune.FocusSpell;
import dev.weaverlib.rune.RuneSpell;
import dev.weaverlib.rune.Weave;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(WeaverLib.ID)
public final class WeaverLib {
    public static final String ID = "weaverlib";
    private static final FocusTier RUNE_FOCUS_LIMITS = new FocusTier(75, 14, 4, 4);
    private static final double GAZE_RANGE = 8;
    private static final double PULSE_RADIUS = 3;
    private static final int MAX_PULSE_TARGETS = 8;
    private static final int INCANT_CHAT_RANGE_SQUARED = 100;
    private static final Map<UUID, Integer> NEXT_ATTEMPT = new HashMap<>();

    public WeaverLib(IEventBus modBus) {
        modBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.addListener(this::onLogout);
    }

    public static boolean handleIncantation(ServerPlayer caster, PlayerChatMessage chat, ChatType.Bound type) {
        String[] words = chat.signedContent().strip().split("(?U)\\s+", 2);
        if (!words[0].equalsIgnoreCase("incant")) {
            return false;
        }

        OutgoingChatMessage outgoing = OutgoingChatMessage.create(chat);
        for (ServerPlayer listener : caster.serverLevel().players()) {
            if (listener.distanceToSqr(caster) <= INCANT_CHAT_RANGE_SQUARED) {
                listener.sendChatMessage(outgoing, caster.shouldFilterMessageTo(listener), type);
            }
        }

        if (beginAttempt(caster)) {
            Weave spell = words.length == 2 ? Weave.fromWords(words[1]) : null;
            if (spell != null) {
                cast(caster, RuneSpell.basic(spell, 1.0));
            } else if (words.length == 2) {
                Spell focus;
                try {
                    focus = FocusSpell.fromWords(words[1], caster.registryAccess());
                } catch (IllegalArgumentException invalidSpell) {
                    message(caster, invalidSpell.getMessage());
                    return true;
                }
                castFocus(caster, focus);
            } else {
                message(caster, "unknown_words");
            }
        }
        return true;
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("4").playToServer(RuneCast.TYPE, RuneCast.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player && beginAttempt(player)) {
                if (FocusComponent.recognize(payload.glyphs().getFirst()) != null) {
                    Spell focus;
                    try {
                        focus = FocusSpell.fromRunes(payload.glyphs(), payload.arguments(), player.registryAccess());
                    } catch (IllegalArgumentException invalidRune) {
                        message(player, invalidRune.getMessage());
                        return;
                    }
                    castFocus(player, focus);
                } else {
                    RuneSpell spell;
                    try {
                        if (payload.arguments().stream().anyMatch(argument -> !argument.isEmpty())) {
                            throw new IllegalArgumentException("focus_arguments");
                        }
                        spell = RuneSpell.compile(payload.glyphs());
                    } catch (IllegalArgumentException invalidRune) {
                        message(player, invalidRune.getMessage());
                        return;
                    }
                    cast(player, spell);
                }
            }
        });
    }

    private void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        NEXT_ATTEMPT.remove(event.getEntity().getUUID());
    }

    private static boolean beginAttempt(ServerPlayer player) {
        int now = player.serverLevel().getServer().getTickCount();
        Integer until = NEXT_ATTEMPT.get(player.getUUID());
        if (!player.isAlive() || player.isSpectator() || (until != null && now - until < 0)) {
            return false;
        }
        NEXT_ATTEMPT.put(player.getUUID(), now + 20);
        return true;
    }

    private static void castFocus(ServerPlayer player, Spell spell) {
        if (CasterManager.isOnCooldown(player)) {
            message(player, "focus_cooldown");
            return;
        }
        var summary = Spells.analyze(spell, RUNE_FOCUS_LIMITS, player.registryAccess(), player);
        if (!summary.valid()) {
            summary.problems().stream().filter(SpellProblem::fatal).findFirst()
                .ifPresent(problem -> player.displayClientMessage(problem.message(), true));
            return;
        }

        ItemStack held = player.getMainHandItem().getItem() instanceof FocusCaster
            ? player.getMainHandItem() : player.getOffhandItem();
        boolean hasCaster = held.getItem() instanceof FocusCaster;
        if (spell.nodes().stream().anyMatch(node -> node.part().equals(FocusComponent.named("plan").key())) && !hasCaster) {
            message(player, "focus_plan_wand");
            return;
        }
        if (spell.nodes().stream().anyMatch(node -> node.part().equals(FocusComponent.named("exchange").key()))
                && (!(held.getItem() instanceof FocusCaster caster) || caster.rememberedBlock(held) == null)) {
            message(player, "focus_exchange_wand");
            return;
        }

        var pre = SpellEngine.firePre(player, ItemStack.EMPTY, spell, 1, summary.vis());
        if (pre.isCanceled() || !Float.isFinite(pre.visCost()) || !Float.isFinite(pre.power())
                || pre.visCost() < 0 || pre.power() <= 0) {
            return;
        }
        float cost = pre.visCost();
        if (cost > 0) {
            try (Transaction payment = Transaction.openRoot()) {
                float drained = AuraHelper.drainVis(player.serverLevel(), player.blockPosition(), cost, payment);
                if (!(drained >= cost)) {
                    message(player, "insufficient_vis", cost);
                    return;
                }
                payment.commit();
            }
        }

        int now = player.serverLevel().getServer().getTickCount();
        NEXT_ATTEMPT.put(player.getUUID(), now + Math.max(20, summary.cooldown()));
        CasterManager.setCooldown(player, summary.cooldown());
        SpellEngine.execute(player, ItemStack.EMPTY, spell, List.of(SpellTarget.origin(player)), pre.power());
    }

    private static boolean cast(ServerPlayer player, RuneSpell spell) {
        LivingEntity anchor = spell.gaze() ? gazeTarget(player) : player;
        if (anchor == null) {
            message(player, "no_target");
            return false;
        }

        double strength = spell.potency() * (spell.surge() ? 1.5 : 1);
        int duration = (int) (1200 * strength);
        List<LivingEntity> targets;
        if (spell.pulse()) {
            targets = player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                    new AABB(anchor.position(), anchor.position()).inflate(PULSE_RADIUS),
                    entity -> entity.isAlive() && !entity.isSpectator()
                        && entity.distanceToSqr(anchor) <= PULSE_RADIUS * PULSE_RADIUS
                        && (entity == player || player.hasLineOfSight(entity))
                        && canApply(entity, spell.effect(), duration))
                .stream().sorted(Comparator.comparingDouble(anchor::distanceToSqr))
                .limit(MAX_PULSE_TARGETS).toList();
        } else {
            targets = canApply(anchor, spell.effect(), duration) ? List.of(anchor) : List.of();
        }
        if (targets.isEmpty()) {
            message(player, "no_effect");
            return false;
        }

        int cost = (int) Math.ceil(spell.effect().cost() * (spell.surge() ? 1.5 : 1))
            * targets.size() + (spell.pulse() ? 2 : 0);
        try (Transaction payment = Transaction.openRoot()) {
            float drained = AuraHelper.drainVis(player.serverLevel(), player.blockPosition(), cost, payment);
            if (!(drained >= cost)) {
                message(player, "insufficient_vis", cost);
                return false;
            }
            payment.commit();
        }

        for (LivingEntity target : targets) {
            switch (spell.effect()) {
                case LUX -> target.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, duration, 0, false, false));
                case SANA -> target.heal((float) (4 * strength));
            }
        }
        message(player, "cast", spell.effect().word(), cost);
        return true;
    }

    private static boolean canApply(LivingEntity target, Weave effect, int duration) {
        if (effect == Weave.SANA) {
            return target.getHealth() < target.getMaxHealth();
        }
        if (!(target instanceof Player)) {
            return false;
        }
        MobEffectInstance current = target.getEffect(MobEffects.NIGHT_VISION);
        return current == null || (!current.isInfiniteDuration() && current.getDuration() < duration
            && current.getAmplifier() == 0);
    }

    private static LivingEntity gazeTarget(ServerPlayer player) {
        var start = player.getEyePosition();
        var aim = player.getViewVector(1).scale(GAZE_RANGE);
        var end = player.serverLevel().clip(new ClipContext(start, start.add(aim),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation();
        var hit = ProjectileUtil.getEntityHitResult(player, start, end,
            player.getBoundingBox().expandTowards(end.subtract(start)).inflate(1),
            entity -> entity instanceof LivingEntity living && living.isAlive()
                && !living.isSpectator() && entity.isPickable() && player.hasLineOfSight(living), start.distanceToSqr(end));
        Entity entity = hit == null ? null : hit.getEntity();
        return entity instanceof LivingEntity living ? living : null;
    }

    private static void message(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("weaverlib." + key, args), true);
    }
}
