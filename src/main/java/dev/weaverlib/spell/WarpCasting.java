package dev.weaverlib.spell;

import com.leclowndu93150.thaumaturge.api.spell.Spell;
import com.leclowndu93150.thaumaturge.api.spell.cast.SpellState;
import com.leclowndu93150.thaumaturge.api.spell.cast.SpellTarget;
import com.leclowndu93150.thaumaturge.content.spell.engine.SpellEngine;
import dev.weaverlib.WeaverLib;
import java.util.HashMap;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class WarpCasting {
    public static final int UNFUELED_MULTIPLIER = 3;
    private static final ResourceLocation VIS_MULTIPLIER =
        ResourceLocation.fromNamespaceAndPath(WeaverLib.ID, "warp_vis_multiplier");
    private static final ThreadLocal<Cast> UNFUELED_CAST = new ThreadLocal<>();

    private WarpCasting() {}

    public static void execute(ServerPlayer player, ItemStack source, Spell spell, float power, boolean unfueled) {
        if (!unfueled) {
            SpellEngine.execute(player, source, spell, List.of(SpellTarget.origin(player)), power);
            return;
        }
        Cast previous = UNFUELED_CAST.get();
        UNFUELED_CAST.set(new Cast(player, spell));
        try {
            SpellEngine.execute(player, source, spell, List.of(SpellTarget.origin(player)), power);
        } finally {
            if (previous == null) {
                UNFUELED_CAST.remove();
            } else {
                UNFUELED_CAST.set(previous);
            }
        }
    }

    public static SpellState tagCost(SpellState state, LivingEntity caster, ItemStack source, Spell spell) {
        Cast cast = UNFUELED_CAST.get();
        if (cast == null || cast.caster() != caster || cast.spell() != spell || !source.isEmpty()) {
            return state;
        }
        // Native carriers and continuations preserve SpellState after the original cast ends.
        var stats = new HashMap<>(state.stats());
        stats.put(VIS_MULTIPLIER, (float) UNFUELED_MULTIPLIER);
        return new SpellState(stats);
    }

    static float continuationCost(SpellState state, float normalVis) {
        return state.stats().containsKey(VIS_MULTIPLIER) ? normalVis * UNFUELED_MULTIPLIER : normalVis;
    }

    private record Cast(ServerPlayer caster, Spell spell) {}
}
