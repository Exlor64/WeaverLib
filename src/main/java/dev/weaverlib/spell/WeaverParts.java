package dev.weaverlib.spell;

import com.leclowndu93150.thaumaturge.api.spell.behavior.AbstractEffectBehavior;
import com.leclowndu93150.thaumaturge.api.spell.behavior.SpellBehavior;
import com.leclowndu93150.thaumaturge.api.spell.behavior.SpellBehaviorType;
import com.leclowndu93150.thaumaturge.api.spell.cast.CastContext;
import com.leclowndu93150.thaumaturge.api.spell.cast.SpellTarget;
import com.leclowndu93150.thaumaturge.api.spell.part.SpellPartKind;
import com.mojang.serialization.MapCodec;
import dev.weaverlib.WeaverLib;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class WeaverParts {
    private static final DeferredRegister<SpellBehaviorType<?>> BEHAVIORS =
        DeferredRegister.create(SpellBehaviorType.REGISTRY_KEY, WeaverLib.ID);

    private static final DeferredHolder<SpellBehaviorType<?>, SpellBehaviorType<Leap>>
        LEAP = BEHAVIORS.register("leap", () -> new SpellBehaviorType<>(SpellPartKind.EFFECT, MapCodec.unit(new Leap())));
    private static final DeferredHolder<SpellBehaviorType<?>, SpellBehaviorType<Eternal>>
        ETERNAL = BEHAVIORS.register("eternal", () -> new SpellBehaviorType<>(SpellPartKind.MODIFIER, MapCodec.unit(new Eternal())));
    private static final DeferredHolder<SpellBehaviorType<?>, SpellBehaviorType<Halt>>
        HALT = BEHAVIORS.register("halt", () -> new SpellBehaviorType<>(SpellPartKind.EFFECT, MapCodec.unit(new Halt())));

    private WeaverParts() {}

    public static void register(IEventBus modBus) {
        BEHAVIORS.register(modBus);
        EternalSpells.register(modBus);
    }

    private static final class Leap extends AbstractEffectBehavior {
        @Override
        public SpellBehaviorType<?> type() {
            return LEAP.get();
        }

        @Override
        protected void apply(CastContext ctx, SpellTarget target, float power, int index) {
            double impulse = Math.clamp(power * (.55 + .25 * ctx.setting("strength")), 0, 3);
            if (impulse > 0 && Double.isFinite(impulse)
                    && target.entity().orElse(null) instanceof LivingEntity living
                    && living.isAlive() && !living.isSpectator()) {
                living.push(living.getLookAngle().scale(impulse));
                living.hurtMarked = true;
                ctx.fx().impact(ctx.part().fx(), living.getBoundingBox().getCenter(), ctx.color());
            }
        }
    }

    private record Eternal() implements SpellBehavior {
        @Override
        public SpellBehaviorType<?> type() {
            return ETERNAL.get();
        }

        @Override
        public void execute(CastContext ctx, List<SpellTarget> targets) {
            EternalSpells.activate(ctx, targets);
        }

        @Override
        public boolean standalone() {
            return false;
        }
    }

    private static final class Halt extends AbstractEffectBehavior {
        @Override
        public SpellBehaviorType<?> type() {
            return HALT.get();
        }

        @Override
        protected void apply(CastContext ctx, SpellTarget target, float power, int index) {
            if (target.entity().orElse(null) instanceof ServerPlayer player && EternalSpells.halt(player)) {
                ctx.fx().impact(ctx.part().fx(), player.getBoundingBox().getCenter(), ctx.color());
            }
        }
    }
}
