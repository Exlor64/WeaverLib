package dev.weaverlib.angel;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Explicit, revocable player oaths. Recall gates require the same accepted lord UUID at entry. */
final class PaladinOaths {
    private static final String LORD="weaverlib_paladin_lord";
    private static final TagKey<EntityType<?>> PALADINS=TagKey.create(Registries.ENTITY_TYPE,ResourceLocation.fromNamespaceAndPath("weaverlib","paladins"));
    private static final OathOfferBook OFFERS=new OathOfferBook();
    private PaladinOaths() {}
    static boolean paladin(Entity e) {return e.getTags().contains("paladin") || e.getType().is(PALADINS);}
    static UUID lord(Entity e) {return e.getPersistentData().hasUUID(LORD)?e.getPersistentData().getUUID(LORD):null;}
    static void cast(ServerPlayer caster) {
        Entity aimed=CelestialSpells.aim(caster,32,e->e instanceof ServerPlayer && paladin(e));
        if(aimed instanceof ServerPlayer paladin && lord(paladin)==null) {
            if(!AngelFuel.pay(caster,40,false))return;
            UUID nonce=OFFERS.offer(caster.getUUID(),paladin.getUUID(),caster.server.getTickCount());
            var accept=Component.literal("[Accept oath]").withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/weaveroath accept "+nonce)));
            var decline=Component.literal("[Decline]").withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/weaveroath decline "+nonce)));
            paladin.sendSystemMessage(Component.literal(caster.getName().getString()+" offers you a paladin's oath of service. Accepting allows them to open recall gates for you. ")
                .append(accept).append(" ").append(decline));
            caster.sendSystemMessage(Component.literal("The oath awaits their choice. Grace is paid only if they accept."));
            return;
        }
        var bound=caster.server.getPlayerList().getPlayers().stream().filter(p->p.isAlive() && paladin(p) && caster.getUUID().equals(lord(p)) && !p.isPassenger()).toList();
        if(bound.isEmpty()) {CelestialSpells.message(caster,"You have no online paladins bound to your service.");return;}
        if(!AngelFuel.pay(caster,40,false))return;
        var gates=new java.util.ArrayList<CelestialEntity>();
        for(ServerPlayer p:bound) {
            Vec3 forward=Vec3.directionFromRotation(0,p.getYRot());
            Vec3 wanted=p.position().add(forward.scale(3));
            Vec3 at=p.serverLevel().clip(new ClipContext(p.position().add(0,.3,0),wanted.add(0,.3,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getLocation().subtract(forward.scale(.25));
            at=new Vec3(at.x,p.getY(),at.z);
            if(at.distanceToSqr(p.position())<1.5*1.5){p.sendSystemMessage(Component.literal("There is no room in front of you for the recall gate."));continue;}
            var gate=CelestialSpells.makeIn(p.serverLevel(),CelestialEntity.GATE,caster,p,at,1,200);
            gate.setYRot(p.getYRot());gates.add(gate);
        }
        if(gates.isEmpty())return;
        for(var gate:gates)if(!((net.minecraft.server.level.ServerLevel)gate.level()).addFreshEntity(gate)){
            for(var added:gates)added.discard();return;
        }
        if(!Angels.pay(caster,40)){for(var gate:gates)gate.discard();return;}
        for(var gate:gates){
            for(var old:CelestialSpells.ACTIVE.stream().filter(e->e.role()==CelestialEntity.GATE && e.target()==gate.target()).toList())old.discard();
            CelestialSpells.ACTIVE.add(gate);
            if(gate.target() instanceof ServerPlayer p)p.sendSystemMessage(Component.literal("Your angel calls. Enter the gate to travel to their side."));
        }
    }
    static void tickGate(CelestialEntity gate,ServerPlayer lord) {
        if(!(gate.target() instanceof ServerPlayer p) || !p.isAlive() || !lord.getUUID().equals(lord(p)) || !paladin(p)) {gate.discard();return;}
        Vec3 delta=p.position().subtract(gate.position());double yaw=Math.toRadians(gate.getYRot());
        double across=delta.x*Math.cos(yaw)+delta.z*Math.sin(yaw),depth=-delta.x*Math.sin(yaw)+delta.z*Math.cos(yaw);
        if(Math.abs(across)>.86+p.getBbWidth()*.4 || Math.abs(depth)>.32+p.getBbWidth()*.5
                || delta.y+p.getBbHeight()<0 || delta.y>2.7 || p.isPassenger())return;
        Vec3 safe=safeSide(lord,p);
        if(safe==null) {if(p.tickCount%40==0)CelestialSpells.message(p,"The gate cannot find safe footing beside your angel.");return;}
        p.teleportTo(lord.serverLevel(),safe.x,safe.y,safe.z,Set.of(),lord.getYRot(),p.getXRot());
        p.setDeltaMovement(Vec3.ZERO);p.resetFallDistance();gate.discard();
    }
    private static Vec3 safeSide(ServerPlayer lord,ServerPlayer paladin) {
        for(int radius=2;radius<=4;radius++)for(int side=0;side<8;side++)for(int dy=2;dy>=-5;dy--) {
            double angle=side*Math.TAU/8;
            BlockPos pos=BlockPos.containing(lord.getX()+Math.cos(angle)*radius,lord.getY()+dy,lord.getZ()+Math.sin(angle)*radius);
            if(!lord.serverLevel().hasChunkAt(pos) || !lord.serverLevel().getWorldBorder().isWithinBounds(pos)
                    || !lord.serverLevel().getBlockState(pos.below()).isFaceSturdy(lord.serverLevel(),pos.below(),net.minecraft.core.Direction.UP))continue;
            var support=lord.serverLevel().getBlockState(pos.below());
            if(support.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK) || support.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                    || support.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE) || !lord.serverLevel().getBlockState(pos).isAir()
                    || !lord.serverLevel().getBlockState(pos.above()).isAir())continue;
            Vec3 at=Vec3.atBottomCenterOf(pos);
            var box=paladin.getBoundingBox().move(at.subtract(paladin.position()));
            if(lord.serverLevel().noCollision(paladin,box) && !lord.serverLevel().containsAnyLiquid(box))return at;
        }
        return null;
    }
    static void commands(RegisterCommandsEvent e) {
        var root=Commands.literal("weaveroath");
        for(String action:new String[]{"accept","decline"})root.then(Commands.literal(action)
            .then(Commands.argument("offer",StringArgumentType.word()).executes(c->answer(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"offer"),action.equals("accept")))));
        root.then(Commands.literal("renounce").executes(c->{var p=c.getSource().getPlayerOrException();p.getPersistentData().remove(LORD);p.sendSystemMessage(Component.literal("Your paladin oath has ended."));return 1;}));
        e.getDispatcher().register(root);
    }
    private static int answer(ServerPlayer paladin,String token,boolean accept) {
        UUID nonce;
        try{nonce=UUID.fromString(token);}catch(IllegalArgumentException invalid){return 0;}
        var offer=OFFERS.claim(nonce,paladin.getUUID(),paladin.server.getTickCount());
        if(offer==null){paladin.sendSystemMessage(Component.literal("That oath offer is no longer available."));return 0;}
        if(!accept){paladin.sendSystemMessage(Component.literal("You decline the oath."));return 1;}
        ServerPlayer lord=paladin.server.getPlayerList().getPlayer(offer.lord());
        if(lord==null || !lord.isAlive() || !paladin.isAlive() || !Angels.view(lord).active() || Angels.view(lord).rank()<3 || !paladin(paladin)
                || lord(paladin)!=null || lord.level()!=paladin.level() || lord.distanceToSqr(paladin)>32*32 || paladin.server.getTickCount()>offer.until())return 0;
        if(!Angels.pay(lord,40)){paladin.sendSystemMessage(Component.literal("Your angel cannot fulfill the oath right now."));return 0;}
        paladin.getPersistentData().putUUID(LORD,lord.getUUID());
        paladin.sendSystemMessage(Component.literal("You swear yourself to "+lord.getName().getString()+". Use /weaveroath renounce to leave their service."));
        lord.sendSystemMessage(Component.literal(paladin.getName().getString()+" has accepted your oath."));
        return 1;
    }
    static void clone(PlayerEvent.Clone e) {if(e.getOriginal().getPersistentData().hasUUID(LORD))e.getEntity().getPersistentData().putUUID(LORD,e.getOriginal().getPersistentData().getUUID(LORD));}
    static void prune(long now) {OFFERS.prune(now);}
    static void clear() {OFFERS.clear();}
}
