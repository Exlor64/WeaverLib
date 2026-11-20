package dev.weaverlib.angel;

import dev.weaverlib.WeaverLib;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class RetributionCombat {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, WeaverLib.ID);
    public static final DeferredHolder<EntityType<?>, EntityType<ChainBinding>> BINDING = ENTITIES.register("angel_chain",
        () -> EntityType.Builder.<ChainBinding>of(ChainBinding::new, MobCategory.MISC).sized(.9F, 2.1F)
            .noSave().noSummon().fireImmune().clientTrackingRange(12).updateInterval(1).build("weaverlib:angel_chain"));
    private static final DeferredRegister<AttachmentType<?>> DATA = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, WeaverLib.ID);
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> SWING = DATA.register("angel_chain_swing",
        () -> AttachmentType.builder(() -> -100L).sync(ByteBufCodecs.VAR_LONG).build());
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<State>> STATE = DATA.register("angel_chains",
        () -> AttachmentType.builder(State::new).build());
    private static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath(WeaverLib.ID, "angel_chain"));
    private static final class State {
        long nextAttack, meleeAt = -1, heldUntil = -1;
        boolean aerial;
    }
    private RetributionCombat() {}
    static void register(IEventBus bus) { ENTITIES.register(bus); DATA.register(bus); }
    public static long swing(Player p) { return p.getExistingData(SWING).orElse(-100L); }
    static boolean usable(ServerPlayer p) {
        return p.isAlive() && !p.isSpectator() && Angels.view(p).active() && Angels.retribution(p)
            && p.getMainHandItem().isEmpty() && !p.isUsingItem() && !p.isPassenger() && !p.isSleeping() && !Angels.view(p).fast();
    }
    static DamageSource damage(ServerPlayer p) {
        return new DamageSource(p.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE), p);
    }
    static List<ChainBinding> owned(ServerPlayer p) {
        return p.serverLevel().getEntitiesOfClass(ChainBinding.class, p.getBoundingBox().inflate(40), c -> c.owner() == p && !c.isRemoved());
    }
    static ChainBinding binding(LivingEntity target) {
        return target.level().getEntitiesOfClass(ChainBinding.class, target.getBoundingBox().inflate(1),
            c -> c.target() == target && !c.isRemoved()).stream().findFirst().orElse(null);
    }
    static boolean attack(ServerPlayer p, long now) {
        State s = p.getData(STATE);
        if (!usable(p) || now < s.nextAttack) return false;
        if (p.getAbilities().flying) {
            if (!cast(p, true)) return false;
        } else {
            if (!Angels.pay(p, ChainCombatRules.MELEE_COST)) return false;
            s.meleeAt = now + ChainCombatRules.HIT_DELAY;
            p.setData(SWING, now);
            p.swing(InteractionHand.MAIN_HAND, true);
            p.serverLevel().playSound(null, p, SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, .85F, .8F);
        }
        s.nextAttack = now + ChainCombatRules.ATTACK_COOLDOWN;
        return true;
    }
    static void hold(ServerPlayer p, long now) {
        if (!usable(p)) return;
        State s = p.getData(STATE);
        boolean aerial = p.getAbilities().flying;
        boolean fresh = s.heldUntil < now || s.aerial != aerial;
        s.aerial = aerial;
        s.heldUntil = now + 8;
        if (!aerial && fresh && now >= s.nextAttack && cast(p, false)) s.nextAttack = now + 20;
    }
    static void release(ServerPlayer p) { p.getData(STATE).heldUntil = -1; }
    static boolean tightening(ServerPlayer p, long now) {
        State s = p.getData(STATE);
        return usable(p) && p.getAbilities().flying && s.aerial && s.heldUntil >= now;
    }
    static void clear(ServerPlayer p) {
        for (var bind : owned(p)) bind.discard();
        p.removeData(STATE);
        p.setData(SWING, -100L);
    }
    static void tick(ServerPlayer p, long now) {
        State s = p.getData(STATE);
        if (!usable(p)) { s.meleeAt = -1; s.heldUntil = -1; return; }
        if (s.meleeAt >= 0 && now >= s.meleeAt) {
            s.meleeAt = -1;
            if (p.getAbilities().flying) return;
            Vec3 forward = p.getLookAngle().multiply(1, 0, 1).normalize();
            for (LivingEntity target : p.serverLevel().getEntitiesOfClass(LivingEntity.class,
                    p.getBoundingBox().inflate(ChainCombatRules.MELEE_RANGE, .7, ChainCombatRules.MELEE_RANGE), e -> Angels.canHit(p, e))) {
                if (!usable(p)) break;
                Vec3 delta = target.getBoundingBox().getCenter().subtract(p.position().add(0, .9, 0));
                if (delta.lengthSqr() <= 16 && forward.dot(delta.normalize()) >= .2 && p.hasLineOfSight(target))
                    target.hurt(damage(p), ChainCombatRules.MELEE_DAMAGE);
            }
        }
    }
    private static boolean cast(ServerPlayer p, boolean ground) {
        if (owned(p).size() >= ChainCombatRules.MAX_BINDS) return false;
        Vec3 start = p.getEyePosition(), desired = start.add(p.getLookAngle().scale(ChainCombatRules.TARGET_RANGE));
        Vec3 end = p.serverLevel().clip(new ClipContext(start, desired, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getLocation();
        var hit = ProjectileUtil.getEntityHitResult(p, start, end, p.getBoundingBox().expandTowards(end.subtract(start)).inflate(1),
            e -> e instanceof LivingEntity living && Angels.canHit(p, living) && binding(living) == null, start.distanceToSqr(end));
        if (hit == null || !(hit.getEntity() instanceof LivingEntity target) || !p.hasLineOfSight(target)) return false;
        Vec3 anchor = p.position();
        if (ground) {
            var floor = p.serverLevel().clip(new ClipContext(target.position().add(0, .1, 0), target.position().add(0, -24, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target));
            if (floor.getType() == HitResult.Type.MISS || !p.serverLevel().getWorldBorder().isWithinBounds(floor.getBlockPos())) return false;
            anchor = new Vec3(target.getX(), floor.getLocation().y + .03, target.getZ());
        }
        double cost = ground ? ChainCombatRules.BIND_COST : ChainCombatRules.GRAPPLE_COST;
        if (!AngelFuel.pay(p, cost, false)) return false;
        var chain = new ChainBinding(BINDING.get(), p.serverLevel());
        chain.bind(p, target, ground, anchor);
        if (!p.serverLevel().addFreshEntity(chain)) return false;
        if (!Angels.pay(p, cost)) { chain.discard(); return false; }
        p.serverLevel().playSound(null, p, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, .9F, .65F);
        return true;
    }
}
