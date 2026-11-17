package dev.weaverlib.spell;

import com.leclowndu93150.thaumaturge.api.spell.Spell;
import com.leclowndu93150.thaumaturge.api.spell.cast.SpellState;
import com.leclowndu93150.thaumaturge.api.spell.cast.SpellTarget;
import com.leclowndu93150.thaumaturge.api.warp.WarpHelper;
import com.leclowndu93150.thaumaturge.api.warp.WarpType;
import com.leclowndu93150.thaumaturge.content.spell.engine.SpellEngine;
import dev.weaverlib.WeaverLib;
import java.util.HashMap;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class WarpCasting {
    private static final int RUNE_VIS_MULTIPLIER = 3;
    private static final int TEMPORARY_WARP_LIMIT = 100;
    private static final int CAST_WARP = 1;
    private static final int INSCRIPTION_WARP = 20;
    private static final ResourceLocation VIS_MULTIPLIER =
        ResourceLocation.fromNamespaceAndPath(WeaverLib.ID, "warp_vis_multiplier");
    private static final ThreadLocal<Cast> RUNE_CAST = new ThreadLocal<>();

    private WarpCasting() {}

    public static float runeVisCost(float normalVis) {
        return normalVis * RUNE_VIS_MULTIPLIER;
    }

    public static void addCastingWarp(ServerPlayer player) {
        addTemporaryWarp(player, CAST_WARP);
    }

    public static void addInscriptionWarp(ServerPlayer player) {
        addTemporaryWarp(player, INSCRIPTION_WARP);
    }

    public static void addTemporaryWarp(ServerPlayer player, int gain) {
        int current = WarpHelper.getWarp(player).get(WarpType.TEMPORARY);
        int total = temporaryWarpAfterGain(current, gain);
        WarpHelper.addWarp(player, total - current, WarpType.TEMPORARY);
        if (total < current) {
            WarpHelper.addWarp(player, 1, WarpType.NORMAL);
            player.displayClientMessage(Component.translatable("weaverlib.warp_condensed"), true);
        }
    }

    static int temporaryWarpAfterGain(int current, int gain) {
        return (long) current + gain > TEMPORARY_WARP_LIMIT ? 1 : current + gain;
    }

    public static void execute(ServerPlayer player, ItemStack source, Spell spell, float power) {
        if (!source.isEmpty()) {
            SpellEngine.execute(player, source, spell, List.of(SpellTarget.origin(player)), power);
            return;
        }
        Cast previous = RUNE_CAST.get();
        RUNE_CAST.set(new Cast(player, spell));
        try {
            SpellEngine.execute(player, source, spell, List.of(SpellTarget.origin(player)), power);
        } finally {
            if (previous == null) {
                RUNE_CAST.remove();
            } else {
                RUNE_CAST.set(previous);
            }
        }
    }

    public static SpellState tagCost(SpellState state, LivingEntity caster, ItemStack source, Spell spell) {
        Cast cast = RUNE_CAST.get();
        if (cast == null || cast.caster() != caster || cast.spell() != spell || !source.isEmpty()) {
            return state;
        }
        // Native carriers and continuations preserve SpellState after the original cast ends.
        var stats = new HashMap<>(state.stats());
        stats.put(VIS_MULTIPLIER, (float) RUNE_VIS_MULTIPLIER);
        return new SpellState(stats);
    }

    static float continuationCost(SpellState state, float normalVis) {
        return state.stats().containsKey(VIS_MULTIPLIER) ? runeVisCost(normalVis) : normalVis;
    }

    private record Cast(ServerPlayer caster, Spell spell) {}
}
