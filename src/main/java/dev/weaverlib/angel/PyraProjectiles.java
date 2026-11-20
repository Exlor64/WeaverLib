package dev.weaverlib.angel;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-owned paid shots. Fireball blasts never invoke block explosion code. */
final class PyraProjectiles {
    private static final AtomicInteger IDS = new AtomicInteger();
    static final class Shot {
        final int id = IDS.incrementAndGet(), kind;
        final double invested;
        final Vec3 velocity;
        final Set<UUID> hit = new HashSet<>();
        Vec3 position;
        int life;
        Shot(int kind, Vec3 position, Vec3 velocity, double invested, int life) {
            this.kind = kind; this.position = position; this.velocity = velocity;
            this.invested = invested; this.life = life;
        }
    }
    private PyraProjectiles() {}

    static Shot arc(ServerPlayer player, int wing) {
        return new Shot(AngelBurst.PYRA_ARC, muzzle(player, player.getEyePosition()
            .add(player.getLookAngle().scale(.65))), player.getLookAngle().scale(1.35), wing, 28);
    }
    static Shot fireball(ServerPlayer player, double invested) {
        return new Shot(AngelBurst.PYRA_ORB, muzzle(player, Angels.pyraOrb(player, 1, invested)),
            player.getLookAngle().scale(.85), invested, 80);
    }
    private static Vec3 muzzle(ServerPlayer player, Vec3 desired) {
        return player.serverLevel().clip(new ClipContext(player.getEyePosition(), desired,
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation();
    }
    private static Vec3 right(Vec3 direction) {
        Vec3 right = direction.cross(new Vec3(0, 1, 0));
        return (right.lengthSqr() < .001 ? direction.cross(new Vec3(1, 0, 0)) : right).normalize();
    }

    static boolean advance(ServerPlayer player, Shot shot) {
        Vec3 next = shot.position.add(shot.velocity);
        var level = player.serverLevel();
        if (!level.getChunkSource().hasChunk((int)Math.floor(next.x) >> 4, (int)Math.floor(next.z) >> 4)) return false;
        var block = level.clip(new ClipContext(shot.position, next, ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, player));
        Vec3 end = block.getLocation();
        boolean impact = block.getType() == HitResult.Type.BLOCK;
        if (shot.kind == AngelBurst.PYRA_ORB) {
            double width = PyraCombat.fireballOrb(shot.invested) * .8;
            // Sweep the entire orb rather than just its center so large charges cannot pass through walls.
            Vec3 r = right(shot.velocity), up = shot.velocity.normalize().cross(r).normalize();
            for (Vec3 offset : new Vec3[]{r.scale(width), r.scale(-width), up.scale(width), up.scale(-width)}) {
                var edge = level.clip(new ClipContext(shot.position.add(offset), next.add(offset),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                if (edge.getType() == HitResult.Type.BLOCK) {
                    Vec3 center = edge.getLocation().subtract(offset);
                    if (!impact || center.distanceToSqr(shot.position) < end.distanceToSqr(shot.position)) end = center;
                    impact = true;
                }
            }
            double nearest = shot.position.distanceToSqr(end);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(shot.position, end).inflate(width + 1), e -> Angels.canHit(player, e))) {
                var box = target.getBoundingBox().inflate(width);
                var hit = box.clip(shot.position, end);
                Vec3 at = box.contains(shot.position) ? shot.position : hit.orElse(null);
                if (at != null && shot.position.distanceToSqr(at) <= nearest) {
                    nearest = shot.position.distanceToSqr(at); end = at; impact = true;
                }
            }
        } else {
            Vec3 r = right(shot.velocity), up = shot.velocity.normalize().cross(r).normalize();
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(shot.position, end).inflate(2.7), e -> Angels.canHit(player, e) && !shot.hit.contains(e.getUUID()))) {
                boolean touched = false;
                // Sample the same crescent used by the renderer; sweep each part between server ticks.
                for (int i = 0; i <= 16 && !touched; i++) {
                    double t = i / 8.0 - 1, lateral = 2.4 * t;
                    double bow = .75 * (1 - t * t) * (((int)shot.invested & 1) == 0 ? 1 : -1);
                    Vec3 offset = r.scale(lateral).add(up.scale(bow));
                    var box = target.getBoundingBox().inflate(.35);
                    touched = box.contains(shot.position.add(offset))
                        || box.clip(shot.position.add(offset), end.add(offset)).isPresent();
                }
                if (touched && level.clip(new ClipContext(shot.position, target.getBoundingBox().getCenter(),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS) {
                    shot.hit.add(target.getUUID());
                    if (target.hurt(Angels.pyraDamage(player), PyraCombat.ARC_DAMAGE)) {
                        target.igniteForSeconds(7);
                        target.knockback(.65, -shot.velocity.x, -shot.velocity.z);
                    }
                }
            }
        }
        effect(player, shot.kind, shot.position, end, shot.invested, shot.id);
        if (impact && shot.kind == AngelBurst.PYRA_ORB) fireballBlast(player, shot, end);
        shot.position = end;
        return !impact && --shot.life > 0;
    }

    private static void fireballBlast(ServerPlayer player, Shot shot, Vec3 center) {
        double radius = PyraCombat.fireballRadius(shot.invested);
        for (LivingEntity target : player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                new AABB(center, center).inflate(radius), e -> Angels.canHit(player, e))) {
            Vec3 nearest = new Vec3(Math.clamp(center.x, target.getBoundingBox().minX, target.getBoundingBox().maxX),
                Math.clamp(center.y, target.getBoundingBox().minY, target.getBoundingBox().maxY),
                Math.clamp(center.z, target.getBoundingBox().minZ, target.getBoundingBox().maxZ));
            double distance = nearest.distanceTo(center);
            if (distance > radius) continue;
            // A small retreat from the collision face keeps the wall from hiding everything in front of it.
            Vec3 origin = center.subtract(shot.velocity.normalize().scale(.08));
            double exposure = Explosion.getSeenPercent(origin, target);
            if (exposure <= 0) continue;
            float damage = (float)(PyraCombat.fireballDamage(shot.invested) * (.25 + .75 * (1 - distance / radius)) * exposure);
            if (target.hurt(Angels.pyraBlastDamage(player), damage)) {
                target.igniteForSeconds((float)(6 + 4 * Math.sqrt(PyraCombat.fireballPower(shot.invested))));
                Vec3 away = target.position().subtract(center);
                target.knockback(1.2 * (1 - distance / radius), -away.x, -away.z);
                if (target instanceof ServerPlayer other) other.connection.send(new ClientboundSetEntityMotionPacket(other));
            }
        }
        effect(player, AngelBurst.PYRA_BLAST, center, center, shot.invested, shot.id);
        player.serverLevel().playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(),
            SoundSource.PLAYERS, 3, .7F);
    }

    static boolean wallAhead(ServerPlayer player, Vec3 previous, Vec3 current) {
        Vec3 ahead = player.getDeltaMovement().normalize().scale(.7);
        if (ahead.lengthSqr() < .01) return false;
        for (double height : new double[]{-.25, 0, .25}) {
            Vec3 offset = new Vec3(0, height, 0);
            if (player.serverLevel().clip(new ClipContext(previous.add(offset), current.add(offset).add(ahead),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.BLOCK) return true;
        }
        return false;
    }

    static void dashBlast(ServerPlayer player) {
        Vec3 at = player.getBoundingBox().getCenter().add(player.getLookAngle().scale(.65));
        var calculator = new ExplosionDamageCalculator() {
            @Override public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
                return entity instanceof LivingEntity living && Angels.canHit(player, living);
            }
            @Override public float getKnockbackMultiplier(Entity entity) {
                return entity == player || player.isAlliedTo(entity)
                    || entity instanceof LivingEntity living && !Angels.canHit(player, living) ? 0 : 1;
            }
        };
        var explosion = new Explosion(player.serverLevel(), player, player.damageSources().explosion(player, player),
            calculator, at.x, at.y, at.z, 3F, false, Explosion.BlockInteraction.DESTROY_WITH_DECAY,
            ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER, SoundEvents.GENERIC_EXPLODE);
        if (net.neoforged.neoforge.event.EventHooks.onExplosionStart(player.serverLevel(), explosion)) return;
        explosion.explode();
        explosion.finalizeExplosion(false);
        for (var target : explosion.getHitPlayers().keySet()) {
            if (target instanceof ServerPlayer other) other.connection.send(new ClientboundSetEntityMotionPacket(other));
        }
        effect(player, AngelBurst.PYRA_DASH_BLAST, at, at, 3, 0);
        player.serverLevel().playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(),
            SoundSource.PLAYERS, 3, .65F);
    }

    private static void effect(ServerPlayer player, int kind, Vec3 from, Vec3 to, double energy, int id) {
        PacketDistributor.sendToPlayersNear(player.serverLevel(), null, from.x, from.y, from.z, 112,
            new AngelBurst(kind | AngelBurst.FIRE, from, to, (float)energy, id));
    }
}
