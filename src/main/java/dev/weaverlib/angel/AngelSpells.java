package dev.weaverlib.angel;

import com.leclowndu93150.thaumaturge.api.warp.WarpHelper;
import com.leclowndu93150.thaumaturge.api.warp.WarpType;
import dev.weaverlib.WeaverLib;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** The six D/P spells, paid and resolved entirely on the server. */
final class AngelSpells {
    private static final ResourceKey<DamageType> CIRCLE_FIRE = ResourceKey.create(Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath(WeaverLib.ID, "angel_circle"));
    static final class State {
        long healing = -1;
        final List<Wave> waves = new ArrayList<>();
        void clear() { healing = -1; waves.clear(); }
    }
    private static final class Wave {
        final int spell;
        final long started;
        final Vec3 center;
        final Set<UUID> pushed = new HashSet<>();
        Wave(int spell, long started, Vec3 center) { this.spell = spell; this.started = started; this.center = center; }
    }
    private AngelSpells() {}

    static void cast(ServerPlayer player, State state, int spell, long now) {
        if (spell == AngelSpellRules.FORGE) { forge(player); return; }
        Vec3 target = spell == AngelSpellRules.SMITE ? aim(player) : null;
        if (spell == AngelSpellRules.SMITE && target == null) {
            player.displayClientMessage(Component.translatable("weaverlib.angel.no_target"), true);
            return;
        }
        if (!Angels.pay(player, AngelSpellRules.cost(spell))) return;
        switch (spell) {
            case AngelSpellRules.HEALING -> {
                state.healing = now;
                effect(player, AngelBurst.ANGEL_HEALING, player.position(), player.position(), 3, player.getId());
            }
            case AngelSpellRules.SMITE -> smite(player, target);
            case AngelSpellRules.REPEL -> {
                state.waves.add(new Wave(spell, now, player.position()));
                effect(player, AngelBurst.ANGEL_REPEL, player.position(), player.position(), 6, 0);
            }
            case AngelSpellRules.CLEANSE -> cleanse(player);
            case AngelSpellRules.CIRCLE -> {
                state.waves.add(new Wave(spell, now, player.position()));
                effect(player, AngelBurst.ANGEL_CIRCLE, player.position(), player.position(), 6, 0);
            }
            default -> { return; }
        }
        player.serverLevel().playSound(null, player, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, .7F, 1.1F);
    }

    static void tick(ServerPlayer player, State state, long now) {
        if (state.healing >= 0) {
            long age = now - state.healing;
            if (AngelSpellRules.healingPulse(age)) {
                for (LivingEntity living : nearby(player, AngelSpellRules.HEAL_RADIUS)) living.heal(AngelSpellRules.HEAL_AMOUNT);
            }
            if (age >= AngelSpellRules.HEAL_TICKS || age < 0) state.healing = -1;
        }
        // Damage callbacks may revert the caster and clear their state, so iterate a snapshot.
        var waves = List.copyOf(state.waves);
        state.waves.clear();
        for (Wave wave : waves) {
            if (!Angels.view(player).active()) break;
            long age = now - wave.started;
            if (age < 0) continue;
            if (wave.spell == AngelSpellRules.CIRCLE) {
                if (!AngelSpellRules.circleActive(age)) continue;
                circle(player, wave, age);
            } else {
                if (age >= AngelSpellRules.WAVE_TICKS) continue;
                repel(player, wave, age);
            }
            if (Angels.view(player).active()) state.waves.add(wave);
        }
    }

    private static List<LivingEntity> nearby(ServerPlayer player, double radius) {
        return player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
            living -> living.isAlive() && !living.isSpectator() && living.distanceToSqr(player) <= radius * radius);
    }

    private static void cleanse(ServerPlayer player) {
        for (LivingEntity living : nearby(player, AngelSpellRules.CLEANSE_RADIUS)) {
            for (var effect : List.copyOf(living.getActiveEffects())) {
                if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL)
                    living.removeEffect(effect.getEffect());
            }
            if (living instanceof ServerPlayer other) {
                for (var type : new WarpType[]{WarpType.TEMPORARY, WarpType.NORMAL}) {
                    int before = WarpHelper.getWarp(other).get(type);
                    int after = AngelSpellRules.reducedWarp(before, type == WarpType.TEMPORARY ? 3 : 1);
                    if (before != after) WarpHelper.addWarp(other, after - before, type);
                }
            }
            effect(player, AngelBurst.ANGEL_CLEANSE, living.position(), living.position(), 1, living.getId());
        }
    }

    private static Vec3 aim(ServerPlayer player) {
        Vec3 start = player.getEyePosition(), end = start.add(player.getLookAngle().scale(AngelSpellRules.SMITE_RANGE));
        var block = player.serverLevel().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        end = block.getLocation();
        var entity = ProjectileUtil.getEntityHitResult(player.serverLevel(), player, start, end,
            new AABB(start, end).inflate(1), e -> e instanceof LivingEntity living && Angels.canHit(player, living), .35F);
        if (entity != null) return entity.getEntity().getBoundingBox().getCenter();
        return block.getType() == HitResult.Type.BLOCK ? end.subtract(player.getLookAngle().scale(.08)) : null;
    }

    private static void smite(ServerPlayer player, Vec3 at) {
        var level = player.serverLevel();
        Vec3 top = level.clip(new ClipContext(at.add(0, .1, 0), at.add(0, 24, 0),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, top).inflate(.9),
                e -> Angels.canHit(player, e))) {
            var box = target.getBoundingBox().inflate(.65);
            if (!box.contains(at) && box.clip(at, top).isEmpty()) continue;
            if (level.clip(new ClipContext(at, target.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS) continue;
            if (target.hurt(Angels.pyraDamage(player), AngelSpellRules.smiteDamage(Angels.rank(player))))
                target.igniteForSeconds(4);
        }
        effect(player, AngelBurst.ANGEL_SMITE, top, at, Angels.rank(player), 0);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.5F, 1.1F);
    }

    private static void repel(ServerPlayer player, Wave wave, long age) {
        double radius = AngelSpellRules.waveRadius(age + 1);
        for (Entity entity : player.serverLevel().getEntities(player, new AABB(wave.center, wave.center).inflate(radius + 1),
                e -> e.isAlive() && !e.isSpectator() && !wave.pushed.contains(e.getUUID()))) {
            if (entity.position().distanceToSqr(wave.center) > (radius + .5) * (radius + .5)) continue;
            wave.pushed.add(entity.getUUID());
            push(entity, wave.center, 1.5);
        }
    }

    private static void circle(ServerPlayer player, Wave wave, long age) {
        double radius = AngelSpellRules.circleRadius(age);
        var damage = new DamageSource(player.serverLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
            .getHolderOrThrow(CIRCLE_FIRE), player);
        for (Entity entity : player.serverLevel().getEntities(player,
                new AABB(wave.center.x - radius, wave.center.y - .5, wave.center.z - radius,
                    wave.center.x + radius, wave.center.y + 2, wave.center.z + radius),
                e -> e.isAlive() && !e.isSpectator())) {
            Vec3 delta = entity.position().subtract(wave.center);
            if (delta.x * delta.x + delta.z * delta.z > radius * radius) continue;
            if (player.serverLevel().clip(new ClipContext(wave.center.add(0, .6, 0), entity.getBoundingBox().getCenter(),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS) continue;
            push(entity, wave.center, .5);
            if (entity instanceof LivingEntity living && Angels.canHit(player, living)) {
                if (living.hurt(damage, AngelSpellRules.CIRCLE_DAMAGE)) living.igniteForSeconds(4);
            }
        }
    }

    private static void push(Entity entity, Vec3 origin, double force) {
        Vec3 direction = entity.getBoundingBox().getCenter().subtract(origin.add(0, .5, 0)).normalize();
        if (direction.lengthSqr() < .001) direction = new Vec3(0, 1, 0);
        if (entity instanceof Projectile) entity.setDeltaMovement(direction.scale(Math.max(2.2, entity.getDeltaMovement().length() * 1.15)));
        else entity.setDeltaMovement(entity.getDeltaMovement().scale(.3).add(direction.scale(force)).add(0, .15, 0));
        entity.hasImpulse = true;
        entity.hurtMarked = true;
        if (entity instanceof ServerPlayer other) other.connection.send(new ClientboundSetEntityMotionPacket(other));
    }

    private static void forge(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        var input = new SingleRecipeInput(held.copyWithCount(1));
        var recipe = player.serverLevel().getRecipeManager().getRecipeFor(RecipeType.SMELTING, input, player.serverLevel());
        if (held.isEmpty() || recipe.isEmpty()) {
            player.displayClientMessage(Component.translatable("weaverlib.angel.no_smelting"), true);
            return;
        }
        ItemStack output = recipe.get().value().assemble(input, player.registryAccess());
        if (output.isEmpty()) return;
        int count = AngelSpellRules.forgeCount(held.getCount(), cost -> AngelFuel.pay(player, cost, false));
        if (count == 0 || !Angels.pay(player, count * AngelSpellRules.cost(AngelSpellRules.FORGE))) return;
        var remainder = recipe.get().value().getRemainingItems(input);
        player.stopUsingItem();
        // Paying from a carried Ember store can replace the selected stack with updated components.
        player.getMainHandItem().shrink(count);
        for (int i = 0; i < count; i++) {
            give(player, output.copy());
            for (var stack : remainder) if (!stack.isEmpty()) give(player, stack.copy());
        }
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(.7)).add(0, -.3, 0);
        effect(player, AngelBurst.ANGEL_FORGE, at, at, count, player.getId());
        player.serverLevel().playSound(null, player, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, .7F, 1.4F);
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }
    private static void effect(ServerPlayer player, int kind, Vec3 from, Vec3 to, double power, int id) {
        PacketDistributor.sendToPlayersNear(player.serverLevel(), null, from.x, from.y, from.z, 112,
            new AngelBurst(kind | AngelBurst.FIRE, from, to, (float)power, id));
    }
}
