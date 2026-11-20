package dev.weaverlib.angel;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Attackable, non-persistent restraint. The entity's hitbox surrounds the visible chains, not the caster. */
public final class ChainBinding extends Entity {
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(ChainBinding.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(ChainBinding.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> GROUND = SynchedEntityData.defineId(ChainBinding.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> TIGHT = SynchedEntityData.defineId(ChainBinding.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> HEALTH = SynchedEntityData.defineId(ChainBinding.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<BlockPos> ANCHOR = SynchedEntityData.defineId(ChainBinding.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Float> TOP = SynchedEntityData.defineId(ChainBinding.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Long> START = SynchedEntityData.defineId(ChainBinding.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> TRAVEL = SynchedEntityData.defineId(ChainBinding.class, EntityDataSerializers.INT);
    private long heldTicks;
    private boolean latched;

    public ChainBinding(EntityType<? extends ChainBinding> type, Level level) { super(type, level); noPhysics = true; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(OWNER, -1); b.define(TARGET, -1); b.define(GROUND, false); b.define(TIGHT, false);
        b.define(HEALTH, ChainCombatRules.HEALTH); b.define(ANCHOR, BlockPos.ZERO); b.define(TOP, 0F);
        b.define(START, 0L); b.define(TRAVEL, 6);
    }
    public Player owner() { return level().getEntity(entityData.get(OWNER)) instanceof Player p ? p : null; }
    public LivingEntity target() { return level().getEntity(entityData.get(TARGET)) instanceof LivingEntity e ? e : null; }
    public boolean ground() { return entityData.get(GROUND); }
    public boolean tight() { return entityData.get(TIGHT); }
    public float health() { return entityData.get(HEALTH); }
    public double age(float partial) { return level().getGameTime() + partial - entityData.get(START); }
    public double extension(float partial) { return Math.clamp(age(partial) / entityData.get(TRAVEL), 0, 1); }
    public boolean latched() { return age(0) >= entityData.get(TRAVEL); }
    public Vec3 anchor() {
        BlockPos p = entityData.get(ANCHOR);
        return new Vec3(p.getX() + .5, p.getY() + entityData.get(TOP), p.getZ() + .5);
    }
    void bind(ServerPlayer caster, LivingEntity target, boolean ground, Vec3 anchor) {
        entityData.set(OWNER, caster.getId()); entityData.set(TARGET, target.getId());
        entityData.set(GROUND, ground); entityData.set(ANCHOR, BlockPos.containing(anchor));
        entityData.set(TOP, (float) (anchor.y - Math.floor(anchor.y)));
        entityData.set(START, level().getGameTime());
        entityData.set(TRAVEL, ChainCombatRules.travelTicks((ground ? anchor : caster.getEyePosition()).distanceTo(target.position())));
        setPos(target.position()); refreshDimensions();
    }
    @Override public EntityDimensions getDimensions(Pose pose) {
        // Entity's constructor requests dimensions before its data tracker exists.
        LivingEntity victim = entityData == null ? null : target();
        return victim == null ? super.getDimensions(pose)
            : EntityDimensions.scalable(Math.max(.65F, victim.getBbWidth() + .32F), Math.max(.6F, victim.getBbHeight() + .15F));
    }
    @Override public boolean isPickable() { return !isRemoved() && latched(); }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved() || !latched() || !(source.getEntity() instanceof LivingEntity)
                || !Float.isFinite(amount) || amount <= 0) return false;
        entityData.set(HEALTH, ChainCombatRules.damaged(health(), amount));
        level().playSound(null, this, SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, .75F, health() <= 0 ? .6F : 1.2F);
        if (health() <= 0) {
            level().playSound(null, this, SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1F, .85F);
            discard();
        }
        return true;
    }
    @Override public void tick() {
        super.tick();
        LivingEntity victim = target();
        if (level().isClientSide) {
            if (victim != null) { setPos(victim.position()); refreshDimensions(); }
            return;
        }
        if (!(owner() instanceof ServerPlayer caster) || victim == null || !caster.isAlive() || !Angels.view(caster).active()
                || !Angels.retribution(caster) || !Angels.canHit(caster, victim) || caster.distanceToSqr(victim) > 32 * 32
                || ChainCombatRules.expired((long) age(0)) || victim.isPassenger()) { discard(); return; }
        setPos(victim.position()); refreshDimensions();
        if (!latched()) return;
        if (!latched) {
            latched = true;
            level().playSound(null, this, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1F, .7F);
        }
        Vec3 destination;
        if (ground()) {
            destination = anchor();
            // A removed floor, teleport or obstruction releases the restraint rather than dragging through blocks.
            var support = level().clip(new ClipContext(destination.add(0, .05, 0), destination.add(0, -.3, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, victim));
            if (support.getType() == HitResult.Type.MISS || victim.position().distanceToSqr(destination) > 26 * 26) { discard(); return; }
            var obstruction = level().clip(new ClipContext(destination.add(0, .12, 0), victim.getBoundingBox().getCenter(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, victim));
            if (obstruction.getType() != HitResult.Type.MISS) { discard(); return; }
        } else {
            if (!caster.hasLineOfSight(victim)) { discard(); return; }
            Vec3 forward = caster.getLookAngle().multiply(1, 0, 1).normalize();
            destination = caster.position().add(forward.scale(1.4));
            if (victim.distanceToSqr(destination) < 1.5 * 1.5) { discard(); return; }
        }
        if (victim instanceof Player p) {
            if (p.getAbilities().flying) {
                p.getAbilities().flying = false;
                if (p instanceof ServerPlayer sp) sp.onUpdateAbilities();
            }
            p.stopFallFlying();
        }
        Vec3 delta = destination.subtract(victim.position());
        var pull = ChainCombatRules.pull(delta.x, delta.y, delta.z, ground());
        victim.setDeltaMovement(pull.x(), pull.y(), pull.z());
        victim.hurtMarked = true;
        victim.resetFallDistance();
        if (victim instanceof ServerPlayer p) p.connection.send(new ClientboundSetEntityMotionPacket(p));
        boolean tighten = ground() && RetributionCombat.tightening(caster, level().getGameTime())
            && Angels.pay(caster, ChainCombatRules.TIGHTEN_PER_TICK);
        entityData.set(TIGHT, tighten);
        if (tighten) {
            heldTicks++;
            if (ChainCombatRules.pulse(heldTicks)) victim.hurt(RetributionCombat.damage(caster), ChainCombatRules.TIGHTEN_DAMAGE);
        } else heldTicks = 0;
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { discard(); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}
