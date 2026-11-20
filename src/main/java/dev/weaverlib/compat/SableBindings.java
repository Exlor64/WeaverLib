package dev.weaverlib.compat;

import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

/** Optional Sable 2.0.6 API bridge. No Sable classes are linked when the mod is absent. */
public final class SableBindings {
    public record Target(Object body, Object pipeline, UUID id, Vec3 local, Vec3 world, AABB bounds, double mass) {}
    public interface Lock extends AutoCloseable { boolean valid(); @Override void close(); }
    private static boolean warned;
    private SableBindings() {}
    private static void warn(Throwable error){
        if(!warned){warned=true;System.getLogger("WeaverLib/Sable").log(System.Logger.Level.WARNING,
            "Optional Sable binding API unavailable; construct binding is disabled for this attempt. Expected Sable 2.0.6 API.",error);}
    }
    /** Sable block raycasts retain plot coordinates; convert them before any world-space entity query. */
    public static Vec3 global(net.minecraft.world.level.Level level, Vec3 point) {
        if(!ModList.get().isLoaded("sable"))return point;
        try {
            Object helper=Class.forName("dev.ryanhcode.sable.Sable").getField("HELPER").get(null);
            Object body=call(helper,"getContaining",level,point);
            if(body==null)return point;
            Vector3d p=new Vector3d(point.x,point.y,point.z);call(call(body,"logicalPose"),"transformPosition",p);
            return new Vec3(p.x,p.y,p.z);
        } catch(ReflectiveOperationException | LinkageError | RuntimeException unavailable){return point;}
    }
    public static Target aim(ServerPlayer player, double range) {
        if (!ModList.get().isLoaded("sable")) return null;
        try {
            var hit = player.serverLevel().clip(new ClipContext(player.getEyePosition(),
                player.getEyePosition().add(player.getLookAngle().scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.BLOCK) return null;
            Object helper = Class.forName("dev.ryanhcode.sable.Sable").getField("HELPER").get(null);
            Object body = call(helper, "getContaining", player.serverLevel(), hit.getBlockPos());
            if (body == null || (boolean) call(body, "isRemoved")) return null;
            Object system = Class.forName("dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem")
                .getMethod("get", net.minecraft.world.level.Level.class).invoke(null, player.serverLevel());
            if (system == null) return null;
            Object pose = call(body, "logicalPose"), box = call(body, "boundingBox");
            Vec3 local = Vec3.atCenterOf(hit.getBlockPos());
            Vector3d world = new Vector3d(local.x, local.y, local.z);
            call(pose, "transformPosition", world);
            AABB bounds = new AABB(number(box,"minX"),number(box,"minY"),number(box,"minZ"),
                number(box,"maxX"),number(box,"maxY"),number(box,"maxZ"));
            if(!Double.isFinite(bounds.minX) || !Double.isFinite(bounds.minY) || !Double.isFinite(bounds.minZ)
                    || !Double.isFinite(bounds.maxX) || !Double.isFinite(bounds.maxY) || !Double.isFinite(bounds.maxZ)
                    || !Double.isFinite(world.x) || !Double.isFinite(world.y) || !Double.isFinite(world.z))return null;
            return new Target(body, call(system,"getPipeline"), (UUID) call(body,"getUniqueId"), local,
                new Vec3(world.x,world.y,world.z), bounds, number(call(body,"getMassTracker"),"getMass"));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) { warn(unavailable);return null; }
    }
    public static Lock lock(Target target) {
        try {
            Object pose = call(target.body,"logicalPose");
            Quaterniond orientation = new Quaterniond((Quaterniondc) call(pose,"orientation")).conjugate();
            Class<?> config = Class.forName("dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration");
            Object fixed = config.getConstructor(org.joml.Vector3dc.class, org.joml.Vector3dc.class, Quaterniondc.class)
                .newInstance(new Vector3d(target.local.x,target.local.y,target.local.z),
                    new Vector3d(target.world.x,target.world.y,target.world.z), orientation);
            Object joint = call(target.pipeline,"addConstraint",target.body,null,fixed);
            if (joint == null) return null;
            return new Lock() {
                private boolean closed;
                public boolean valid() {
                    try { return !closed && !(boolean)call(target.body,"isRemoved") && (boolean)call(joint,"isValid"); }
                    catch (ReflectiveOperationException | RuntimeException e) { return false; }
                }
                public void close() {
                    if (closed) return;
                    closed = true;
                    try { if ((boolean)call(joint,"isValid")) call(joint,"remove"); }
                    catch (ReflectiveOperationException | RuntimeException ignored) { /* World teardown may already remove the joint. */ }
                }
            };
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) { warn(unavailable);return null; }
    }
    private static double number(Object object, String name) throws ReflectiveOperationException { return ((Number)call(object,name)).doubleValue(); }
    private static Object call(Object object, String name, Object... args) throws ReflectiveOperationException {
        outer: for (Method m : object.getClass().getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
            Class<?>[] types = m.getParameterTypes();
            for (int i=0;i<args.length;i++) if (args[i]!=null && !types[i].isInstance(args[i])) continue outer;
            return m.invoke(object,args);
        }
        throw new NoSuchMethodException(object.getClass().getName()+"."+name);
    }
}
