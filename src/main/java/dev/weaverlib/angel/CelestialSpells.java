package dev.weaverlib.angel;

import dev.weaverlib.WeaverLib;
import dev.weaverlib.compat.SableBindings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Eight rank-three invocations, with optional construct constraints and server-owned status contracts. */
public final class CelestialSpells {
    private static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(Registries.ENTITY_TYPE,WeaverLib.ID);
    public static final DeferredHolder<EntityType<?>,EntityType<CelestialEntity>> ANCHOR=ENTITIES.register("celestial_anchor",
        ()->EntityType.Builder.<CelestialEntity>of(CelestialEntity::new,MobCategory.MISC).sized(1.2F,2F).noSave().noSummon().fireImmune().clientTrackingRange(16).updateInterval(1).build("weaverlib:celestial_anchor"));
    private static final DeferredRegister<MobEffect> EFFECTS=DeferredRegister.create(Registries.MOB_EFFECT,WeaverLib.ID);
    private static final class Status extends MobEffect {Status(MobEffectCategory kind,int color){super(kind,color);}}
    public static final DeferredHolder<MobEffect,MobEffect> DAMOCLES=EFFECTS.register("damocles",()->new Status(MobEffectCategory.HARMFUL,0xB69B61));
    public static final DeferredHolder<MobEffect,MobEffect> RETALIATION=EFFECTS.register("retaliation",()->new Status(MobEffectCategory.BENEFICIAL,0xEB6D27));
    private static final DeferredRegister<AttachmentType<?>> DATA=DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES,WeaverLib.ID);
    private static final DeferredHolder<AttachmentType<?>,AttachmentType<Float>> STRAIN=DATA.register("holy_seal_strain",()->AttachmentType.builder(()->0F).sync(ByteBufCodecs.FLOAT).build());
    private static final ResourceLocation SLOW=ResourceLocation.fromNamespaceAndPath(WeaverLib.ID,"holy_seal_strain");
    private static final TagKey<EntityType<?>> ALIGNED=TagKey.create(Registries.ENTITY_TYPE,ResourceLocation.fromNamespaceAndPath(WeaverLib.ID,"angel_aligned"));
    static final Set<CelestialEntity> ACTIVE=new HashSet<>();
    private record Pact(LivingEntity a,LivingEntity b,DamoclesContract contract) {}
    private static final Map<UUID,Pact> PACTS=new HashMap<>();
    private static final Map<UUID,RetaliationLedger> REVENGE=new HashMap<>();
    private CelestialSpells() {}
    static void register(IEventBus bus) {
        ENTITIES.register(bus);EFFECTS.register(bus);DATA.register(bus);
        NeoForge.EVENT_BUS.addListener(PaladinOaths::commands);
        NeoForge.EVENT_BUS.addListener(PaladinOaths::clone);
        NeoForge.EVENT_BUS.addListener(CelestialSpells::tick);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH,CelestialSpells::incoming);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,CelestialSpells::damaged);
        NeoForge.EVENT_BUS.addListener(CelestialSpells::stopping);
        NeoForge.EVENT_BUS.addListener(CelestialSpells::logout);
        NeoForge.EVENT_BUS.addListener(CelestialSpells::login);
    }
    public static double flightScale(Player p) {return 1-Math.clamp(p.getExistingData(STRAIN).orElse(0F),0,.9);}
    public static boolean day(ServerPlayer p) {return p.level().dimensionType().hasSkyLight() && !p.level().dimensionType().hasFixedTime() && CelestialRules.daylight(p.level().getDayTime());}
    public static boolean recharging(ServerPlayer p) {
        return day(p) && ACTIVE.stream().anyMatch(e->!e.isRemoved() && e.role()==CelestialEntity.SKY && e.owner()==p && e.level()==p.level()
            && e.position().distanceToSqr(p.position())<=8*8);
    }
    static void message(ServerPlayer p,String text) {p.displayClientMessage(Component.literal(text),true);}
    static DamageSource damage(ServerPlayer owner,String path) {
        return new DamageSource(owner.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
            .getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE,ResourceLocation.fromNamespaceAndPath(WeaverLib.ID,path))),owner);
    }
    static boolean aligned(ServerPlayer caster,LivingEntity target) {
        if(target==caster || caster.isAlliedTo(target) || target.getTags().contains("angel_aligned") || target.getTags().contains("angel") || target.getType().is(ALIGNED))return true;
        if(target instanceof Player p && Angels.view(p).rank()>0)return true;
        if(PaladinOaths.paladin(target) && PaladinOaths.lord(target)!=null)return true;
        return target instanceof net.minecraft.world.entity.TamableAnimal pet && pet.getOwner() instanceof Player p && Angels.view(p).rank()>0;
    }
    static Entity aim(ServerPlayer p,double range,Predicate<Entity> predicate) {
        Vec3 start=p.getEyePosition(),end=start.add(p.getLookAngle().scale(range));
        end=SableBindings.global(p.level(),p.serverLevel().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getLocation());
        start=SableBindings.global(p.level(),start);
        var hit=ProjectileUtil.getEntityHitResult(p,start,end,new AABB(start,end).inflate(1),e->e!=p && e.isAlive() && !e.isSpectator() && predicate.test(e),start.distanceToSqr(end));
        return hit==null?null:hit.getEntity();
    }
    static CelestialEntity makeIn(ServerLevel level,int role,ServerPlayer p,Entity target,Vec3 at,double size,int life) {
        var e=new CelestialEntity(ANCHOR.get(),level);e.init(role,p,target,at,size,life);return e;
    }
    private static CelestialEntity make(ServerPlayer p,int role,Entity target,Vec3 at,double size,int life) {return makeIn(p.serverLevel(),role,p,target,at,size,life);}
    private static boolean add(CelestialEntity e) {
        if(!((ServerLevel)e.level()).addFreshEntity(e)){e.discard();return false;}
        ACTIVE.add(e);
        e.level().playSound(null,e,e.role()==CelestialEntity.CHAINS?net.minecraft.sounds.SoundEvents.CHAIN_PLACE:
            net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT,net.minecraft.sounds.SoundSource.PLAYERS,.75F,e.role()==CelestialEntity.SKY?.55F:.9F);
        return true;
    }
    private static boolean paidAnchor(ServerPlayer p,CelestialEntity e,double cost) {
        if(!AngelFuel.pay(p,cost,false)){e.discard();return false;}
        if(!add(e))return false;
        if(!Angels.pay(p,cost)){e.discard();return false;}
        return true;
    }
    static void cast(ServerPlayer p,int spell,long now) {
        switch(spell) {
            case AngelSpellRules.OATH -> PaladinOaths.cast(p);
            case AngelSpellRules.WORLD_BIND -> worldBind(p);
            case AngelSpellRules.SEAL -> seal(p);
            case AngelSpellRules.DAMOCLES -> damocles(p);
            case AngelSpellRules.RETALIATION -> {
                if(p.hasEffect(RETALIATION) || !Angels.pay(p,60))return;
                REVENGE.put(p.getUUID(),new RetaliationLedger());p.addEffect(new MobEffectInstance(RETALIATION,CelestialRules.RETALIATION_TICKS,0,false,false));
            }
            case AngelSpellRules.SKY -> {
                if(!day(p)){message(p,"The sky will only answer during the day.");return;}
                if(ACTIVE.stream().anyMatch(e->!e.isRemoved() && e.owner()==p && e.role()==CelestialEntity.SKY))return;
                paidAnchor(p,make(p,CelestialEntity.SKY,null,p.position(),1,CelestialRules.SKY_TICKS),200);
            }
            case AngelSpellRules.HOLY_GROUNDS -> {
                if(ACTIVE.stream().anyMatch(e->!e.isRemoved() && e.owner()==p && e.role()==CelestialEntity.GROUNDS))return;
                paidAnchor(p,make(p,CelestialEntity.GROUNDS,null,p.position(),16,CelestialRules.GROUNDS_TICKS),60);
            }
            case AngelSpellRules.HOLY_BLAST -> blast(p);
            default -> { }
        }
    }
    public static boolean pinned(Player p) {
        if(p.level().isClientSide)return !p.level().getEntitiesOfClass(CelestialEntity.class,p.getBoundingBox().inflate(2),
            e->e.target()==p && (e.role()==CelestialEntity.CHAINS || e.role()==CelestialEntity.SEAL)).isEmpty();
        return alreadyPinned(p);
    }
    private static boolean alreadyPinned(Entity target) {return ACTIVE.stream().anyMatch(e->!e.isRemoved() && e.target()==target && (e.role()==CelestialEntity.CHAINS || e.role()==CelestialEntity.SEAL));}
    private static boolean shipLocked(UUID id){return ACTIVE.stream().anyMatch(e->!e.isRemoved() && id.equals(e.construct()));}
    private static void worldBind(ServerPlayer p) {
        SableBindings.Target ship=SableBindings.aim(p,32);
        if(ship!=null && shipLocked(ship.id()))ship=null;
        Entity aimed=aim(p,32,e->e instanceof LivingEntity living && Angels.canHit(p,living));
        Vec3 at=aimed!=null?SableBindings.global(p.level(),aimed.position()):ship!=null?ship.world():SableBindings.global(p.level(),p.serverLevel().clip(new ClipContext(p.getEyePosition(),p.getEyePosition().add(p.getLookAngle().scale(32)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getLocation());
        var targets=p.serverLevel().getEntitiesOfClass(LivingEntity.class,new AABB(at,at).inflate(6),
            e->Angels.canHit(p,e) && e.distanceToSqr(at)<=36 && p.hasLineOfSight(e) && !alreadyPinned(e) && !e.isPassenger()).stream()
            .sorted(java.util.Comparator.comparingDouble(e->e.distanceToSqr(at))).limit(8).toList();
        if(targets.isEmpty() && ship==null){message(p,"No creatures or moving constructs are within reach.");return;}
        double cost=(targets.isEmpty()?0:100+Math.max(0,targets.size()-1)*40)+(ship!=null?100*CelestialRules.scale(ship.bounds().getXsize()*ship.bounds().getYsize()*ship.bounds().getZsize()):0);
        if(!AngelFuel.pay(p,cost,false))return;
        var created=new ArrayList<CelestialEntity>();
        for(Entity t:targets)created.add(make(p,CelestialEntity.CHAINS,t,t.position(),1,CelestialRules.CHAIN_TICKS));
        if(ship!=null) {
            var lock=SableBindings.lock(ship);
            if(lock==null){message(p,"These chains cannot take hold of that construct.");return;}
            var proxy=make(p,CelestialEntity.CHAINS,null,ship.world(),Math.max(1,Math.cbrt(ship.bounds().getXsize()*ship.bounds().getYsize()*ship.bounds().getZsize())),CelestialRules.CHAIN_TICKS);
            proxy.joint(lock);proxy.construct(ship.id());created.add(proxy);
        }
        for(var e:created)if(!add(e)){for(var old:created)old.discard();return;}
        if(!Angels.pay(p,cost))for(var e:created)e.discard();
    }
    private static void seal(ServerPlayer p) {
        Entity target=aim(p,32,e->!(e instanceof ChainBinding) && !(e instanceof CelestialEntity));
        if(target instanceof Player other && !p.canHarmPlayer(other) || target!=null && p.isAlliedTo(target))return;
        SableBindings.Target ship=target==null?SableBindings.aim(p,32):null;
        if(target==null && ship==null){message(p,"Aim at a creature, vehicle, or moving construct.");return;}
        if(ship!=null && shipLocked(ship.id()))return;
        if(target!=null && (alreadyPinned(target) || target.isPassenger()))return;
        AABB box=target!=null?target.getBoundingBox():ship.bounds();
        double scale=CelestialRules.scale(box.getXsize()*box.getYsize()*box.getZsize());
        double cost=CelestialRules.sealCost(scale);
        if(!AngelFuel.pay(p,cost,false))return;
        var e=make(p,CelestialEntity.SEAL,target,target!=null?target.position():new Vec3(ship.bounds().getCenter().x,ship.bounds().minY,ship.bounds().getCenter().z),scale,CelestialRules.SEAL_TICKS);
        if(ship!=null){var lock=SableBindings.lock(ship);if(lock==null){message(p,"That ship resists the seal.");return;}e.joint(lock);e.construct(ship.id());}
        paidAnchor(p,e,cost);
    }
    private static void damocles(ServerPlayer p) {
        Entity hit=aim(p,32,e->e instanceof LivingEntity t && Angels.canHit(p,t));
        if(!(hit instanceof LivingEntity t) || PACTS.containsKey(p.getUUID()) || PACTS.containsKey(t.getUUID()))return;
        if(!Angels.pay(p,75))return;
        Pact pact=new Pact(p,t,new DamoclesContract(p.getUUID(),t.getUUID()));PACTS.put(p.getUUID(),pact);PACTS.put(t.getUUID(),pact);
        for(LivingEntity who:new LivingEntity[]{p,t}) {
            who.addEffect(new MobEffectInstance(DAMOCLES,CelestialRules.DAMOCLES_TICKS,0,false,false));
            add(make(p,CelestialEntity.SWORD,who,who.position(),1,CelestialRules.DAMOCLES_TICKS));
        }
    }
    private static void end(Pact pact) {
        PACTS.remove(pact.a.getUUID());PACTS.remove(pact.b.getUUID());pact.a.removeEffect(DAMOCLES);pact.b.removeEffect(DAMOCLES);
    }
    private static void incoming(LivingIncomingDamageEvent e) {
        if(e.getEntity().level().isClientSide || !(e.getSource().getEntity() instanceof LivingEntity attacker) || attacker==e.getEntity() || !attacker.hasEffect(RETALIATION))return;
        var ledger=REVENGE.get(attacker.getUUID());double bonus=ledger==null?0:ledger.bonus(e.getEntity().getUUID());
        if(bonus>0)e.setAmount((float)Math.min(Float.MAX_VALUE/4,e.getAmount()+bonus));
    }
    private static void damaged(LivingDamageEvent.Post e) {
        if(e.getEntity().level().isClientSide || e.getNewDamage()<=0 || !(e.getSource().getEntity() instanceof LivingEntity attacker) || attacker==e.getEntity())return;
        if(attacker.hasEffect(RETALIATION)){
            var ledger=REVENGE.remove(attacker.getUUID());if(ledger!=null)ledger.consume(e.getEntity().getUUID());attacker.removeEffect(RETALIATION);
        }
        if(e.getEntity().hasEffect(RETALIATION)) {
            var stored=REVENGE.computeIfAbsent(e.getEntity().getUUID(),u->new RetaliationLedger());
            stored.record(attacker.getUUID(),e.getNewDamage());
            var current=e.getEntity().getEffect(RETALIATION);
            int visual=(int)Math.min(5,stored.total()/5);
            if(current!=null && visual>current.getAmplifier())e.getEntity().addEffect(new MobEffectInstance(RETALIATION,current.getDuration(),visual,false,false));
        }
        Pact pact=PACTS.get(attacker.getUUID());
        if(pact!=null && attacker.hasEffect(DAMOCLES) && pact.contract.violate(attacker.getUUID())) {
            end(pact); // Remove both sides before delivering the verdict, preventing recursion or a second winner.
            var holder=attacker.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE,ResourceLocation.fromNamespaceAndPath(WeaverLib.ID,"damocles")));
            attacker.hurt(new DamageSource(holder),50);
        }
    }
    private static void blast(ServerPlayer p) {
        if(!Angels.pay(p,150))return;
        Vec3 start=p.getEyePosition(),forward=p.getLookAngle(),end=start.add(forward.scale(32));
        Vec3 right=forward.cross(new Vec3(0,1,0));if(right.lengthSqr()<.01)right=new Vec3(1,0,0);right=right.normalize();
        Vec3 up=right.cross(forward).normalize();
        for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++) {
            Vec3 from=start.add(right.scale(x*.65)).add(up.scale(y*.65)),to=end.add(right.scale(x*.65)).add(up.scale(y*.65));
            for(int blocks=0;blocks<64;blocks++) {
                var hit=p.serverLevel().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
                if(hit.getType()!=HitResult.Type.BLOCK)break;
                var pos=hit.getBlockPos();if(!p.serverLevel().hasChunkAt(pos))break;
                var state=p.serverLevel().getBlockState(pos);
                if(state.getDestroySpeed(p.level(),pos)<0 || !p.mayInteract(p.serverLevel(),pos))break;
                var event=new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(p.serverLevel(),pos,state,p);
                if(NeoForge.EVENT_BUS.post(event).isCanceled() || !p.serverLevel().destroyBlock(pos,true,p))break;
            }
        }
        for(Entity t:p.serverLevel().getEntities(p,new AABB(start,end).inflate(2),e->e.isAlive() && !e.isSpectator() && (!(e instanceof CelestialEntity a) || a.role()==CelestialEntity.CHAINS))) {
            if(!p.isAlive() || !Angels.view(p).active())break;
            if(t instanceof LivingEntity living && !Angels.canHit(p,living))continue;
            double along=Math.clamp(t.getBoundingBox().getCenter().subtract(start).dot(forward),0,32);
            if(!p.hasLineOfSight(t) || t.getBoundingBox().getCenter().distanceToSqr(start.add(forward.scale(along)))>Math.pow(1.25+t.getBbWidth()*.5,2))continue;
            boolean hit=t.hurt(damage(p,"celestial_fire"),32);
            if(hit)t.igniteForSeconds(6);
            if(!t.isRemoved()) {
                t.setDeltaMovement(t.getDeltaMovement().add(forward.scale(2)).add(0,.4,0));t.hurtMarked=true;
                if(t instanceof ServerPlayer target)target.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(target));
            }
        }
        p.serverLevel().playSound(null,p,net.minecraft.sounds.SoundEvents.BLAZE_SHOOT,net.minecraft.sounds.SoundSource.PLAYERS,1.5F,.6F);
        PacketDistributor.sendToPlayersNear(p.serverLevel(),null,start.x,start.y,start.z,96,new AngelBurst(AngelBurst.HOLY_BLAST|AngelBurst.FIRE,start,end,1));
    }
    private static void tick(ServerTickEvent.Post e) {
        ACTIVE.removeIf(Entity::isRemoved);
        for(var anchor:List.copyOf(ACTIVE))if(anchor.role()==CelestialEntity.CHAINS || anchor.role()==CelestialEntity.SEAL)anchor.reassert();
        PaladinOaths.prune(e.getServer().getTickCount());
        for(Pact pact:Set.copyOf(PACTS.values()))if(!pact.a.isAlive() || !pact.b.isAlive() || pact.a.isRemoved() || pact.b.isRemoved() || pact.a.level()!=pact.b.level()
                || !pact.a.hasEffect(DAMOCLES) || !pact.b.hasEffect(DAMOCLES))end(pact);
        REVENGE.keySet().removeIf(id->{var p=e.getServer().getPlayerList().getPlayer(id);return p==null || !p.hasEffect(RETALIATION);});
        for(ServerPlayer p:e.getServer().getPlayerList().getPlayers()) {
            float slow=(float)ACTIVE.stream().filter(a->a.owner()==p && a.role()==CelestialEntity.SEAL).mapToDouble(a->CelestialRules.sealSlow(a.size())).max().orElse(0);
            if(!Angels.view(p).active())slow=0;
            if(p.getData(STRAIN)!=slow) {
                p.setData(STRAIN,slow);var speed=p.getAttribute(Attributes.MOVEMENT_SPEED);speed.removeModifier(SLOW);
                if(slow>0)speed.addTransientModifier(new AttributeModifier(SLOW,-slow,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
    }
    static void endForm(ServerPlayer p) {
        for(var a:List.copyOf(ACTIVE))if(a.owner()==p && a.role()!=CelestialEntity.SWORD)a.discard();
        p.setData(STRAIN,0F);var speed=p.getAttribute(Attributes.MOVEMENT_SPEED);if(speed!=null)speed.removeModifier(SLOW);
    }
    private static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {
        Pact pact=PACTS.get(e.getEntity().getUUID());if(pact!=null)end(pact);
        REVENGE.remove(e.getEntity().getUUID());
    }
    private static void login(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e) {
        e.getEntity().removeEffect(DAMOCLES);e.getEntity().removeEffect(RETALIATION);
        var speed=e.getEntity().getAttribute(Attributes.MOVEMENT_SPEED);if(speed!=null)speed.removeModifier(SLOW);
        e.getEntity().setData(STRAIN,0F);
    }
    private static void stopping(ServerStoppingEvent e) {
        for(var anchor:Set.copyOf(ACTIVE))anchor.discard();ACTIVE.clear();
        for(Pact pact:Set.copyOf(PACTS.values()))end(pact);PACTS.clear();REVENGE.clear();PaladinOaths.clear();
    }
}
