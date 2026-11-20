package dev.weaverlib.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.weaverlib.angel.Angels;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Connected world-space flame volumes emitted from both faces of the actual moving feather geometry. */
final class PyraWingFire {
    private record Key(UUID player,int pair,int side,int source,boolean hand) {}
    private static final Map<Key,PyraFlamePhysics.Plume> PLUMES=new HashMap<>();
    private static final Map<BlockPos,List<AABB>> COLLIDERS=new HashMap<>();
    private static ClientLevel world;
    private static long collisionTick=Long.MIN_VALUE;
    private PyraWingFire() {}

    static void tick(ClientLevel level) {
        if(world!=level) { PLUMES.clear();COLLIDERS.clear();world=level; }
        if(level==null)return;
        long now=level.getGameTime();
        if(collisionTick!=now) { COLLIDERS.clear();collisionTick=now; }
        PLUMES.entrySet().removeIf(entry -> {
            var player=level.getPlayerByUUID(entry.getKey().player());
            return now-entry.getValue().lastSeen()>10 || player==null || !player.isAlive() || player.isInvisible()
                || !Angels.view(player).active() || !Angels.inferno(player) || entry.getKey().pair()>=Angels.view(player).rank();
        });
    }

    static void capture(AbstractClientPlayer player,PoseStack pose,AngelWingMotion.Joints joints,
            long pattern,int pair,int side,boolean hand) {
        var client=Minecraft.getInstance();
        if(world!=client.level)tick(client.level);
        if(world==null || player.distanceToSqr(client.gameRenderer.getMainCamera().getPosition())>64*64)return;
        if(collisionTick!=world.getGameTime()) { COLLIDERS.clear();collisionTick=world.getGameTime(); }
        if(PLUMES.size()>8192)PLUMES.clear();
        Vec3 camera=client.gameRenderer.getMainCamera().getPosition();
        for(var source:PyraWings.sources(joints,pattern)) {
            var point=pose.last().pose().transformPosition((float)source.position().x,(float)source.position().y,
                (float)source.position().z,new org.joml.Vector3f());
            var normal=pose.last().pose().transformDirection((float)source.normal().x,(float)source.normal().y,
                (float)source.normal().z,new org.joml.Vector3f()).normalize();
            Vec3 position=new Vec3(point.x,point.y,point.z).add(camera);
            if(boxes(BlockPos.containing(position)).stream().anyMatch(box -> box.contains(position)))continue;
            var anchor=new PyraFlamePhysics.Anchor(vector(position),new PyraFlamePhysics.Vector(normal.x,normal.y,normal.z),
                vector(player.getDeltaMovement()),player.getScale(),source.width()*player.getScale(),source.heat(),
                source.gold(),pattern+source.id()*7919L);
            var plume=PLUMES.computeIfAbsent(new Key(player.getUUID(),pair,side,source.id(),hand),key -> new PyraFlamePhysics.Plume());
            plume.capture(anchor,world.getGameTime(),PyraWingFire::collide);
        }
    }

    static void render(VertexConsumer buffer,PoseStack.Pose pose,Vec3 camera,float partial) {
        var client=Minecraft.getInstance();
        if(world!=client.level || world==null)return;
        boolean first=client.options.getCameraType().isFirstPerson();
        for(var entry:PLUMES.entrySet()) {
            if(world.getGameTime()-entry.getValue().lastSeen()>2)continue;
            boolean own=client.player!=null && entry.getKey().player().equals(client.player.getUUID());
            if(entry.getKey().hand() && (!own || !first) || own && first && !entry.getKey().hand())continue;
            mesh(buffer,pose,camera,entry.getValue(),partial);
        }
    }

    static void mesh(VertexConsumer buffer,PoseStack.Pose pose,Vec3 camera,PyraFlamePhysics.Plume plume,float partial) {
        var points=plume.samples(partial);
        if(points.size()<2)return;
        Vec3 normal=vec(plume.anchor().normal());
        int material=plume.anchor().gold()?PyraFire.GILDED_WING:PyraFire.TRAIL;
        double phase=(plume.anchor().seed()>>>16&65535)/65536.0;
        for(int i=1;i<points.size();i++) {
            var before=points.get(Math.max(0,i-2));var start=points.get(i-1);
            var end=points.get(i);var after=points.get(Math.min(points.size()-1,i+1));
            for(int part=0;part<2;part++) {
                var a=PyraFlamePhysics.interpolate(before,start,end,after,part/2.0);
                var b=PyraFlamePhysics.interpolate(before,start,end,after,(part+1)/2.0);
                Vec3 p=vec(a.position()).subtract(camera),q=vec(b.position()).subtract(camera);
                Vec3 tangent=q.subtract(p);
                if(tangent.lengthSqr()<.000001)continue;
                tangent=tangent.normalize();
                Vec3 across=normal.cross(tangent);
                if(across.lengthSqr()<.0001)across=new Vec3(1,0,0).cross(tangent);
                if(across.lengthSqr()<.0001)across=new Vec3(0,0,1).cross(tangent);
                across=across.normalize();
                for(int plane=0;plane<2;plane++) {
                    Vec3 width=plane==0?across:tangent.cross(across).normalize();
                    PyraFire.plumeQuad(buffer,pose,material,phase,
                        p.subtract(width.scale(a.width())),p.add(width.scale(a.width())),
                        q.add(width.scale(b.width())),q.subtract(width.scale(b.width())),
                        a.along(),b.along(),a.alpha()*(plane==0?1:.55),b.alpha()*(plane==0?1:.55));
                }
            }
        }
    }

    private static List<AABB> boxes(BlockPos at) {
        return COLLIDERS.computeIfAbsent(at.immutable(),pos -> world.getBlockState(pos)
            .getCollisionShape(world,pos).toAabbs().stream().map(box -> box.move(pos.getX(),pos.getY(),pos.getZ())).toList());
    }

    private static PyraFlamePhysics.Movement collide(PyraFlamePhysics.Vector from,
            PyraFlamePhysics.Vector to,PyraFlamePhysics.Vector velocity) {
        Vec3 start=vec(from),end=vec(to),hit=null;AABB obstacle=null;
        for(var pos:BlockPos.betweenClosed(BlockPos.containing(Math.min(start.x,end.x),Math.min(start.y,end.y),Math.min(start.z,end.z)),
                BlockPos.containing(Math.max(start.x,end.x),Math.max(start.y,end.y),Math.max(start.z,end.z)))) {
            for(var box:boxes(pos)) {
                if(box.contains(start))return null;
                var at=box.clip(start,end);
                if(at.isPresent() && (hit==null || at.get().distanceToSqr(start)<hit.distanceToSqr(start))) {
                    hit=at.get();obstacle=box;
                }
            }
        }
        if(hit==null)return new PyraFlamePhysics.Movement(to,velocity);
        Vec3 n=face(obstacle,hit),motion=vec(velocity);
        motion=motion.subtract(n.scale(Math.min(0,motion.dot(n)))).scale(.85);
        return new PyraFlamePhysics.Movement(vector(hit.add(n.scale(.003))),vector(motion));
    }

    private static Vec3 face(AABB box,Vec3 point) {
        double[] d={Math.abs(point.x-box.minX),Math.abs(point.x-box.maxX),Math.abs(point.y-box.minY),
            Math.abs(point.y-box.maxY),Math.abs(point.z-box.minZ),Math.abs(point.z-box.maxZ)};
        Vec3[] n={new Vec3(-1,0,0),new Vec3(1,0,0),new Vec3(0,-1,0),new Vec3(0,1,0),new Vec3(0,0,-1),new Vec3(0,0,1)};
        int best=0;for(int i=1;i<6;i++)if(d[i]<d[best])best=i;
        return n[best];
    }
    private static PyraFlamePhysics.Vector vector(Vec3 v) { return new PyraFlamePhysics.Vector(v.x,v.y,v.z); }
    private static Vec3 vec(PyraFlamePhysics.Vector v) { return new Vec3(v.x(),v.y(),v.z()); }
}
