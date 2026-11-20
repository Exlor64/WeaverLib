package dev.weaverlib.angel;

import dev.weaverlib.compat.SableBindings;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Tracked geometry and lifetime for anchored spells. Only world chains have a damageable hitbox. */
public final class CelestialEntity extends Entity {
    public static final int GATE = 0, CHAINS = 1, SEAL = 2, SKY = 3, GROUNDS = 4, SWORD = 5;
    private static final EntityDataAccessor<Integer> ROLE = SynchedEntityData.defineId(CelestialEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(CelestialEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(CelestialEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(CelestialEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START = SynchedEntityData.defineId(CelestialEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(CelestialEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEALTH = SynchedEntityData.defineId(CelestialEntity.class, EntityDataSerializers.FLOAT);
    private SableBindings.Lock joint;
    private Vec3 fixed;
    private java.util.UUID ownerId,constructId;
    private float fixedYaw, fixedPitch;
    public CelestialEntity(EntityType<? extends CelestialEntity> type, Level level) { super(type,level); noPhysics = true; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(ROLE,GATE);b.define(OWNER,-1);b.define(TARGET,-1);b.define(LIFE,200);b.define(START,0L);b.define(SIZE,1F);b.define(HEALTH,25F);
    }
    void init(int role, ServerPlayer owner, Entity target, Vec3 at, double size, int life) {
        entityData.set(ROLE,role);entityData.set(OWNER,owner.getId());entityData.set(TARGET,target==null?-1:target.getId());
        entityData.set(SIZE,(float)size);entityData.set(LIFE,life);entityData.set(START,level().getGameTime());
        ownerId=owner.getUUID();fixedYaw=target==null?0:target.getYRot();fixedPitch=target==null?0:target.getXRot();
        setPos(at);fixed=target==null?at:target.position();setYRot(owner.getYRot());refreshDimensions();
    }
    void construct(java.util.UUID id){constructId=id;}
    java.util.UUID construct(){return constructId;}
    void joint(SableBindings.Lock lock) { joint = lock; }
    public int role() { return entityData.get(ROLE); }
    public Entity owner() {
        Entity local=level().getEntity(entityData.get(OWNER));
        return local!=null?local:!level().isClientSide && ownerId!=null && level().getServer()!=null
            ?level().getServer().getPlayerList().getPlayer(ownerId):null;
    }
    public Entity target() { return level().getEntity(entityData.get(TARGET)); }
    public double size() { return entityData.get(SIZE); }
    public float health() { return entityData.get(HEALTH); }
    public double age(float partial) { return level().getGameTime()+partial-entityData.get(START); }
    public int life() { return entityData.get(LIFE); }
    public boolean hasJoint() { return joint != null; }
    @Override public EntityDimensions getDimensions(Pose pose) {
        Entity t=entityData==null?null:target();
        if(t==null && entityData!=null && role()==CHAINS){float radius=(float)Math.min(4,.6+Math.log1p(size()));return EntityDimensions.scalable(radius*2+.15F,2.2F);}
        return t==null?super.getDimensions(pose):EntityDimensions.scalable(Math.max(.8F,t.getBbWidth()+.3F),Math.max(1,t.getBbHeight()+.2F));
    }
    @Override public boolean isPickable() { return !isRemoved() && role()==CHAINS; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean hurt(DamageSource source,float amount) {
        if(level().isClientSide || role()!=CHAINS || isRemoved() || !(source.getEntity() instanceof LivingEntity) || !Float.isFinite(amount) || amount<=0)return false;
        entityData.set(HEALTH,Math.max(0,health()-amount));
        level().playSound(null,this,health()<=0?SoundEvents.CHAIN_BREAK:SoundEvents.CHAIN_HIT,SoundSource.PLAYERS,.9F,.75F);
        if(health()<=0)discard();
        return true;
    }
    @Override public void tick() {
        super.tick();
        if(level().isClientSide){
            if((role()==CHAINS || role()==SEAL || role()==SWORD) && target()!=null)setPos(target().position());
            refreshDimensions();return;
        }
        if(!(owner() instanceof ServerPlayer p) || !p.isAlive() || age(0)>=life() || age(0)<0
                || role()!=SWORD && (!Angels.view(p).active() || role()!=GATE && p.level()!=level())) {discard();return;}
        if(joint!=null && !joint.valid()) {discard();return;}
        if(role()==CHAINS || role()==SEAL) {
            Entity t=target();
            if(t==null && joint==null || t!=null && (!t.isAlive() || t.isSpectator() || t.isPassenger() || t.position().distanceToSqr(fixed)>16*16)) {discard();return;}
            if(role()==SEAL && !Angels.pay(p,CelestialRules.sealUpkeep(size()))) {discard();return;}
            if(t!=null)reassert();
        } else if(role()==GATE) PaladinOaths.tickGate(this,p);
        else if(role()==SKY) {
            if(!CelestialSpells.day(p)) {discard();return;}
            if(((long)age(0))%20==0) {
                Vec3 tear=position().add(0,18,0);
                for(LivingEntity t:level().getEntitiesOfClass(LivingEntity.class,new net.minecraft.world.phys.AABB(tear,tear).inflate(4),
                        e->e.isAlive() && !e.isSpectator() && e.distanceToSqr(tear)<=16
                            && (!(e instanceof net.minecraft.world.entity.player.Player other) || e==p || p.canHarmPlayer(other)))) {
                    if(!p.isAlive() || !Angels.view(p).active())break;
                    if(t.hurt(CelestialSpells.damage(p,"celestial_fire"),10))t.igniteForSeconds(4);
                }
            }
        } else if(role()==GROUNDS) {
            if(!Angels.pay(p,15.0/20)) {discard();return;}
            if(((long)age(0))%20==0) for(LivingEntity t:level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(16),
                    e->e.isAlive() && e.distanceToSqr(position())<=256 && !CelestialSpells.aligned(p,e))) {
                if(!p.isAlive() || !Angels.view(p).active())break;
                if(Angels.canHit(p,t))t.hurt(CelestialSpells.damage(p,"celestial_fire"),4);
            }
        } else if(role()==SWORD) {
            if(!(target() instanceof LivingEntity t) || !t.isAlive() || !t.hasEffect(CelestialSpells.DAMOCLES)) {discard();return;}
            setPos(t.position());
        }
    }
    void reassert() {
        Entity t=target();if(t==null || fixed==null || t.isRemoved() || !t.isAlive() || t.position().distanceToSqr(fixed)>16*16)return;
        t.setDeltaMovement(Vec3.ZERO);t.resetFallDistance();
        if(t instanceof ServerPlayer player) {
            if(player.getAbilities().flying){player.getAbilities().flying=false;player.onUpdateAbilities();}
            player.stopFallFlying();
            if(t.position().distanceToSqr(fixed)>.0025)player.connection.teleport(fixed.x,fixed.y,fixed.z,player.getYRot(),player.getXRot());
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }else {t.setPos(fixed);if(!(t instanceof LivingEntity)){t.setYRot(fixedYaw);t.setXRot(fixedPitch);}}
        setPos(fixed);refreshDimensions();
    }
    @Override public void remove(RemovalReason reason) {
        if(joint!=null){joint.close();joint=null;}
        super.remove(reason);
    }
    @Override public void onRemovedFromLevel() {
        if(joint!=null){joint.close();joint=null;}
        super.onRemovedFromLevel();
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {discard();}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}
