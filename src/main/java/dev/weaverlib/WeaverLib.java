package dev.weaverlib;

import com.leclowndu93150.thaumaturge.api.aura.AuraHelper;
import com.leclowndu93150.thaumaturge.api.transaction.Transaction;
import com.leclowndu93150.thaumaturge.api.casters.FocusCaster;
import com.leclowndu93150.thaumaturge.api.spell.FocusTier;
import com.leclowndu93150.thaumaturge.api.spell.Spell;
import com.leclowndu93150.thaumaturge.api.spell.SpellNode;
import com.leclowndu93150.thaumaturge.api.spell.SpellProblem;
import com.leclowndu93150.thaumaturge.api.spell.Spells;
import com.leclowndu93150.thaumaturge.api.spell.cast.SpellTarget;
import com.leclowndu93150.thaumaturge.content.casters.CasterManager;
import com.leclowndu93150.thaumaturge.content.spell.engine.SpellEngine;
import dev.weaverlib.compat.GoetyCasting;
import dev.weaverlib.compat.SoulIntegrity;
import dev.weaverlib.rune.FocusComponent;
import dev.weaverlib.rune.FocusSpell;
import dev.weaverlib.rune.RuneSpell;
import dev.weaverlib.rune.Weave;
import com.leclowndu93150.thaumaturge.api.items.RechargeAccess;
import com.leclowndu93150.thaumaturge.api.spell.SpellSummary;
import com.mojang.datafixers.util.Either;
import dev.weaverlib.spell.EternalSpells;
import dev.weaverlib.spell.InscribedSpell;
import dev.weaverlib.spell.Inscriptions;
import dev.weaverlib.spell.WeaverParts;
import dev.weaverlib.spell.WarpCasting;
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
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
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

    public WeaverLib(IEventBus modBus, ModContainer container) {
        WarpUnlock.register(modBus, container);
        WeaverParts.register(modBus);
        Inscriptions.register(modBus);
        if (hasGoety()) {
            SoulIntegrity.register(modBus);
            GoetyCasting.register(modBus);
        }
        modBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.addListener(this::onLogout);
    }

    public static boolean hasGoety() {
        return ModList.get().isLoaded("goety");
    }

    private static void castGoety(ServerPlayer caster, boolean standalone) {
        if (!hasGoety()) {
            message(caster, "goety_missing");
        } else if (!standalone) {
            message(caster, "goety_standalone");
        } else {
            GoetyCasting.start(caster);
        }
    }

    public static boolean handleIncantation(ServerPlayer caster, PlayerChatMessage chat, ChatType.Bound type) {
        String[] words = chat.signedContent().strip().split("(?U)\\s+", 2);
        if (!words[0].equalsIgnoreCase("incant")) {
            return false;
        }

        if (!WarpUnlock.check(caster, false)) {
            if (words.length != 2 || !isRealIncantation(caster, words[1])) {
                return false;
            }
            caster.sendSystemMessage(Component.translatable("weaverlib.incant_locked"));
            return true;
        }

        OutgoingChatMessage outgoing = OutgoingChatMessage.create(chat);
        for (ServerPlayer listener : caster.serverLevel().players()) {
            if (listener.distanceToSqr(caster) <= INCANT_CHAT_RANGE_SQUARED) {
                listener.sendChatMessage(outgoing, caster.shouldFilterMessageTo(listener), type);
            }
        }

        if (words.length == 2 && FocusComponent.spoken(words[1].strip()) == FocusComponent.named("halt")) {
            castHalt(caster);
            return true;
        }
        if (beginAttempt(caster, false)) {
            if (words.length == 2) {
                String[] phrase = words[1].strip().split("(?U)\\s+", 2);
                if (phrase[0].equalsIgnoreCase("goety")) {
                    castGoety(caster, phrase.length == 2 && phrase[1].equalsIgnoreCase("osseous"));
                    return true;
                }
                FocusComponent prefix = FocusComponent.spoken(phrase[0]);
                if (prefix == FocusComponent.named("erase")) {
                    erase(caster, phrase.length == 1);
                    return true;
                }
                if (prefix == FocusComponent.named("inscribe")) {
                    inscribeWords(caster, phrase.length == 2 ? phrase[1] : "");
                    return true;
                }
            }
            Weave spell = words.length == 2 ? Weave.fromWords(words[1]) : null;
            if (spell != null) {
                cast(caster, RuneSpell.basic(spell, 1.0), ItemStack.EMPTY);
            } else if (words.length == 2) {
                Spell focus;
                try {
                    focus = FocusSpell.fromWords(words[1], caster.registryAccess());
                } catch (IllegalArgumentException invalidSpell) {
                    message(caster, invalidSpell.getMessage());
                    return true;
                }
                castFocus(caster, focus, ItemStack.EMPTY);
            } else {
                message(caster, "unknown_words");
            }
        }
        return true;
    }

    private static boolean isRealIncantation(ServerPlayer caster, String words) {
        String[] phrase = words.strip().split("(?U)\\s+", 2);
        if (phrase[0].equalsIgnoreCase("goety")) {
            return hasGoety() && phrase.length == 2 && phrase[1].equalsIgnoreCase("osseous");
        }
        FocusComponent prefix = FocusComponent.spoken(phrase[0]);
        if (prefix == FocusComponent.named("erase")) {
            return phrase.length == 1;
        }
        if (prefix == FocusComponent.named("halt") && phrase.length == 1) {
            return true;
        }
        if (prefix == FocusComponent.named("inscribe")) {
            if (phrase.length != 2) {
                return false;
            }
            words = phrase[1];
        }
        if (Weave.fromWords(words) != null) {
            return true;
        }
        try {
            FocusSpell.fromWords(words, caster.registryAccess());
            return true;
        } catch (IllegalArgumentException invalidSpell) {
            return false;
        }
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("7");
        registrar.playToServer(CastInscription.TYPE, CastInscription.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                castInscription(player, payload.inscriptionId());
            }
        });
        registrar.playToServer(RuneCast.TYPE, RuneCast.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                if (EternalSpells.isSustaining(player) && isStandaloneHalt(payload)) {
                    castHalt(player);
                    return;
                }
                if (!beginAttempt(player, true)) {
                    return;
                }
                FocusComponent first = FocusComponent.recognize(payload.glyphs().getFirst());
                if (first != null) {
                    if (first == FocusComponent.named("osseous")) {
                        castGoety(player, payload.glyphs().size() == 1 && payload.arguments().getFirst().isEmpty());
                        return;
                    }
                    if (first == FocusComponent.named("erase")) {
                        erase(player, payload.glyphs().size() == 1 && payload.arguments().getFirst().isEmpty());
                        return;
                    }
                    if (first.word().equals("inscribe")) {
                        inscribeRunes(player, payload);
                        return;
                    }
                    if (first.word().equals("halt") && payload.glyphs().size() == 1
                            && payload.arguments().getFirst().isEmpty()) {
                        castHalt(player);
                        return;
                    }
                    Spell focus;
                    try {
                        focus = FocusSpell.fromRunes(payload.glyphs(), payload.arguments(), player.registryAccess());
                    } catch (IllegalArgumentException invalidRune) {
                        message(player, invalidRune.getMessage());
                        return;
                    }
                    castFocus(player, focus, ItemStack.EMPTY);
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
                    cast(player, spell, ItemStack.EMPTY);
                }
            }
        });
    }

    private static boolean isStandaloneHalt(RuneCast payload) {
        if (payload.glyphs().size() != 1 || !payload.arguments().getFirst().isEmpty()) {
            return false;
        }
        FocusComponent part = FocusComponent.recognize(payload.glyphs().getFirst());
        return part != null && part.word().equals("halt");
    }

    private static void castHalt(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || !EternalSpells.isSustaining(player)
                || !WarpUnlock.check(player, false)) {
            return;
        }
        var halt = FocusComponent.named("halt").key();
        if (Spells.part(player.registryAccess(), halt).filter(part -> !part.hidden()).isEmpty()) {
            return;
        }
        Spell spell = Spell.empty().withRoot(SpellNode.of(Spell.ORIGIN).then(SpellNode.of(halt)));
        var pre = SpellEngine.firePre(player, ItemStack.EMPTY, spell, 1, 0);
        if (!pre.isCanceled() && payCast(player, ItemStack.EMPTY, 0) >= 0) {
            SpellEngine.execute(player, ItemStack.EMPTY, spell,
                List.of(SpellTarget.entity(player, player.getLookAngle())), 1);
        }
    }

    private void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        NEXT_ATTEMPT.remove(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer player) {
            EternalSpells.clear(player);
        }
    }

    private static void erase(ServerPlayer player, boolean standalone) {
        if (!standalone) {
            message(player, "erase_layout");
            return;
        }
        if (Inscriptions.erase(player.getMainHandItem())) {
            message(player, "erase_done");
        } else {
            message(player, "erase_empty");
        }
    }

    private static void inscribeWords(ServerPlayer player, String phrase) {
        if (phrase.isEmpty()) {
            message(player, "inscribe_layout");
            return;
        }
        Weave effect = Weave.fromWords(phrase);
        if (effect != null) {
            inscribeLegacy(player, RuneSpell.basic(effect, 1.0));
            return;
        }
        try {
            inscribeFocus(player, FocusSpell.readWords(phrase, player.registryAccess()));
        } catch (IllegalArgumentException invalidSpell) {
            message(player, invalidSpell.getMessage());
        }
    }

    private static void inscribeRunes(ServerPlayer player, RuneCast payload) {
        int size = payload.glyphs().size();
        if (size < 2 || !payload.arguments().getFirst().isEmpty()) {
            message(player, "inscribe_layout");
            return;
        }
        var glyphs = payload.glyphs().subList(1, size);
        var arguments = payload.arguments().subList(1, size);
        try {
            if (FocusComponent.recognize(glyphs.getFirst()) != null) {
                inscribeFocus(player, FocusSpell.readRunes(glyphs, arguments, player.registryAccess()));
            } else {
                if (arguments.stream().anyMatch(argument -> !argument.isEmpty())) {
                    throw new IllegalArgumentException("focus_arguments");
                }
                inscribeLegacy(player, RuneSpell.compile(glyphs));
            }
        } catch (IllegalArgumentException invalidSpell) {
            message(player, invalidSpell.getMessage());
        }
    }

    private static void inscribeFocus(ServerPlayer player, FocusSpell.Compiled compiled) {
        if (analyze(player, compiled.spell()) != null) {
            inscribe(player, Either.left(compiled.spell()), compiled.incantation());
        }
    }

    private static void inscribeLegacy(ServerPlayer player, RuneSpell spell) {
        inscribe(player, Either.right(spell), spell.inscriptionName());
    }

    private static void inscribe(ServerPlayer player, Either<Spell, RuneSpell> spell, String words) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty() || stack.getCount() != 1) {
            message(player, "inscribe_single_item");
            return;
        }
        if (Inscriptions.get(stack) != null) {
            message(player, "inscribe_already_bound");
            return;
        }
        var inscription = new InscribedSpell(UUID.randomUUID(), spell, words, Inscriptions.efficientRecipe(player, stack));
        if (payAmbient(player, Inscriptions.FEE)) {
            Inscriptions.bind(stack, inscription);
            message(player, "inscribe_bound", words);
            WarpCasting.addInscriptionWarp(player);
        }
    }

    private static void castInscription(ServerPlayer player, UUID id) {
        ItemStack stack = player.getMainHandItem();
        var inscription = Inscriptions.get(stack);
        if (player.isShiftKeyDown() || inscription == null || !id.equals(inscription.id()) || stack.getCount() != 1
                || !beginAttempt(player, false)) {
            return;
        }
        inscription.spell().ifLeft(spell -> castFocus(player, spell, stack))
            .ifRight(spell -> cast(player, spell, stack));
    }

    private static boolean beginAttempt(ServerPlayer player, boolean checkWarp) {
        int now = player.serverLevel().getServer().getTickCount();
        Integer until = NEXT_ATTEMPT.get(player.getUUID());
        if (!player.isAlive() || player.isSpectator() || (until != null && now - until < 0)) {
            return false;
        }
        NEXT_ATTEMPT.put(player.getUUID(), now + 20);
        return !checkWarp || WarpUnlock.check(player, true);
    }

    private static SpellSummary analyze(ServerPlayer player, Spell spell) {
        var summary = Spells.analyze(spell, RUNE_FOCUS_LIMITS, player.registryAccess(), player);
        if (summary.valid()) {
            return summary;
        }
        summary.problems().stream().filter(SpellProblem::fatal).findFirst()
            .ifPresent(problem -> player.displayClientMessage(problem.message(), true));
        return null;
    }

    private static void castFocus(ServerPlayer player, Spell spell, ItemStack source) {
        if (CasterManager.isOnCooldown(player)) {
            message(player, "focus_cooldown");
            return;
        }
        var summary = analyze(player, spell);
        if (summary == null) {
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

        var pre = SpellEngine.firePre(player, source, spell, 1, summary.vis());
        if (pre.isCanceled() || !Float.isFinite(pre.visCost()) || !Float.isFinite(pre.power())
                || pre.visCost() < 0 || pre.power() <= 0) {
            return;
        }
        if (payCast(player, source, pre.visCost()) < 0) {
            return;
        }

        int now = player.serverLevel().getServer().getTickCount();
        NEXT_ATTEMPT.put(player.getUUID(), now + Math.max(20, summary.cooldown()));
        CasterManager.setCooldown(player, summary.cooldown());
        WarpCasting.execute(player, source, spell, pre.power());
    }

    // Returns the Vis paid, or a negative value if the payment failed.
    private static float payCast(ServerPlayer player, ItemStack source, float normalCost) {
        var inscription = Inscriptions.get(source);
        if (inscription == null) {
            float cost = WarpCasting.runeVisCost(normalCost);
            if (!payAmbient(player, cost)) {
                return -1;
            }
            WarpCasting.addCastingWarp(player);
            return cost;
        }
        int cost = inscription.cost(normalCost);
        if (!RechargeAccess.spendVis(source, cost)) {
            message(player, "inscription_no_vis", cost, RechargeAccess.storedVis(source));
            return -1;
        }
        return cost;
    }

    private static boolean payAmbient(ServerPlayer player, float cost) {
        if (!Float.isFinite(cost)) {
            message(player, "insufficient_vis", cost);
            return false;
        }
        if (cost > 0) {
            try (Transaction payment = Transaction.openRoot()) {
                float drained = AuraHelper.drainVis(player.serverLevel(), player.blockPosition(), cost, payment);
                if (!(drained >= cost)) {
                    message(player, "insufficient_vis", cost);
                    return false;
                }
                payment.commit();
            }
        }
        return true;
    }

    private static boolean cast(ServerPlayer player, RuneSpell spell, ItemStack source) {
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
        float paid = payCast(player, source, cost);
        if (paid < 0) {
            return false;
        }

        for (LivingEntity target : targets) {
            switch (spell.effect()) {
                case LUX -> target.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, duration, 0, false, false));
                case SANA -> target.heal((float) (4 * strength));
            }
        }
        message(player, "cast", spell.effect().word(), (int) paid);
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
