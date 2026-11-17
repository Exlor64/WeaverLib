package dev.weaverlib.sol;

import dev.weaverlib.WeaverLib;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class SolCombat {
    public enum Mode {
        OFF, RANGED, MELEE
    }

    public record View(int reserve, Mode mode, boolean handsTogether) {
        public static final View LOCKED = new View(-1, Mode.OFF, false);

        public boolean unlocked() {
            return reserve >= 0;
        }
    }

    private static final int SKY_COST = 32;
    private static final int HAND_COST = 24;
    private static final int LANCE_COST = 12;
    private static final int WINDUP = 14;
    private static final int HAND_RECOVERY = 9;
    private static final double LANCE_SPEED = 2.6;
    private static final int LANCE_TICKS = 16;
    private static final ResourceKey<DamageType> SOLAR = ResourceKey.create(Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath(WeaverLib.ID, "solar"));
    private static final StreamCodec<ByteBuf, View> VIEW_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, View::reserve,
        ByteBufCodecs.VAR_INT, view -> view.mode().ordinal(),
        ByteBufCodecs.BOOL, View::handsTogether,
        (reserve, mode, hands) -> new View(reserve, Mode.values()[mode], hands));

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, WeaverLib.ID);
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<View>> VIEW =
        ATTACHMENTS.register("sol_view", () -> AttachmentType.builder(() -> View.LOCKED).sync(VIEW_CODEC).build());
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Session>> SESSION =
        ATTACHMENTS.register("sol_combat", () -> AttachmentType.builder(Session::new).build());

    private SolCombat() {}

    static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }

    public static View view(Player player) {
        return player.getExistingData(VIEW).orElse(View.LOCKED);
    }

    static void refresh(ServerPlayer player) {
        Session session = player.getExistingData(SESSION).orElse(null);
        View view = Sol.unlocked(player)
            ? new View(Sol.reserve(player), session == null ? Mode.OFF : session.mode,
                session != null && session.poseUntil > player.serverLevel().getGameTime())
            : View.LOCKED;
        if (!view.equals(view(player))) {
            player.setData(VIEW, view);
        }
    }

    static void reset(ServerPlayer player) {
        player.removeData(SESSION);
        refresh(player);
    }

    public static void handle(ServerPlayer player, SolAction action) {
        if (!Sol.unlocked(player) || !player.isAlive() || player.isSpectator()) {
            return;
        }
        Session session = player.getData(SESSION);
        if (session.level != player.serverLevel()) {
            session = new Session();
            session.level = player.serverLevel();
            player.setData(SESSION, session);
        }

        long now = player.serverLevel().getGameTime();
        if (action == SolAction.CYCLE_MODE) {
            if (now < session.nextModeChange) {
                return;
            }
            session.nextModeChange = now + 5;
            session.mode = switch (session.mode) {
                case OFF -> Mode.RANGED;
                case RANGED -> Mode.MELEE;
                case MELEE -> Mode.OFF;
            };
            session.fireAt = 0;
            session.poseUntil = 0;
            refresh(player);
            return;
        }
        if (session.mode != Mode.RANGED || now < session.nextAttack || session.fireAt != 0
                || player.isUsingItem() || player.isSleeping()) {
            return;
        }

        switch (action) {
            case SKY_BEAM -> skyBeam(player, session, now);
            case HAND_BEAM -> {
                if (Sol.reserve(player) >= HAND_COST) {
                    session.fireAt = now + WINDUP;
                    session.poseUntil = session.fireAt + HAND_RECOVERY;
                    session.nextAttack = now + WINDUP + 26;
                    refresh(player);
                    session.level.playSound(null, player, SoundEvents.BEACON_ACTIVATE,
                        SoundSource.PLAYERS, 0.55F, 1.6F);
                } else {
                    lowReserve(player, HAND_COST);
                }
            }
            case LANCE -> {
                if (pay(player, LANCE_COST)) {
                    session.nextAttack = now + 18;
                    session.lance = new Lance(player.getEyePosition(), player.getLookAngle().scale(LANCE_SPEED),
                        LANCE_TICKS);
                    session.level.playSound(null, player, SoundEvents.TRIDENT_THROW.value(),
                        SoundSource.PLAYERS, 0.7F, 1.5F);
                }
            }
            case CYCLE_MODE -> throw new IllegalStateException();
        }
    }

    static void tick(ServerPlayer player) {
        Session session = player.getExistingData(SESSION).orElse(null);
        if (session == null) {
            refresh(player);
            return;
        }
        if (session.level != player.serverLevel() || !player.isAlive() || player.isSpectator()) {
            reset(player);
            return;
        }

        long now = player.serverLevel().getGameTime();
        if (session.fireAt != 0) {
            if (player.isUsingItem() || player.isSleeping()) {
                session.fireAt = 0;
                session.poseUntil = 0;
            } else if (now >= session.fireAt) {
                session.fireAt = 0;
                if (pay(player, HAND_COST)) {
                    handBeam(player);
                }
            } else if (now % 2 == 0) {
                Vec3 hands = handOrigin(player);
                session.level.sendParticles(ParticleTypes.WAX_ON, hands.x, hands.y, hands.z,
                    4, 0.15, 0.15, 0.15, 0.005);
            }
        }
        if (session.lance != null) {
            session.lance = advanceLance(player, session.lance);
        }
        refresh(player);
    }

    private static void skyBeam(ServerPlayer player, Session session, long now) {
        ServerLevel level = player.serverLevel();
        Vec3 from = player.getEyePosition();
        Vec3 to = blockEnd(player, from, from.add(player.getLookAngle().scale(42)));
        var hit = ProjectileUtil.getEntityHitResult(level, player, from, to,
            new AABB(from, to).inflate(1), entity -> entity instanceof LivingEntity living && canHit(player, living));
        Vec3 target = hit == null ? to : hit.getEntity().position().add(0, 0.1, 0);
        if ((hit == null && to.distanceToSqr(from) >= 42 * 42 - 0.01)
                || !level.dimensionType().hasSkyLight() || !level.canSeeSky(BlockPos.containing(target.add(0, 0.35, 0)))) {
            player.displayClientMessage(Component.translatable("weaverlib.sol.no_sky_target"), true);
            return;
        }
        if (!pay(player, SKY_COST)) {
            return;
        }
        session.nextAttack = now + 38;
        flash(player, SolFlash.Kind.SKY, target.add(0, 54, 0), target);
        level.playSound(null, BlockPos.containing(target), SoundEvents.LIGHTNING_BOLT_IMPACT,
            SoundSource.PLAYERS, 0.85F, 1.35F);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(target, target).inflate(2.6, 5, 2.6), entity -> canHit(player, entity))) {
            double dx = victim.getX() - target.x;
            double dz = victim.getZ() - target.z;
            Vec3 chest = victim.getBoundingBox().getCenter();
            if (dx * dx + dz * dz <= 2.6 * 2.6 && level.canSeeSky(BlockPos.containing(chest))
                    && clear(player, target.add(0, 2.2, 0), chest)) {
                damage(player, victim, 14);
            }
        }
    }

    private static void handBeam(ServerPlayer player) {
        Vec3 from = handOrigin(player);
        Vec3 to = blockEnd(player, from, from.add(player.getLookAngle().scale(28)));
        flash(player, SolFlash.Kind.HAND, from, to);
        player.serverLevel().playSound(null, player, SoundEvents.WARDEN_SONIC_BOOM,
            SoundSource.PLAYERS, 0.7F, 1.4F);
        for (LivingEntity victim : player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                new AABB(from, to).inflate(0.8), entity -> canHit(player, entity))) {
            if ((victim.getBoundingBox().inflate(0.72).contains(from)
                    || victim.getBoundingBox().inflate(0.72).clip(from, to).isPresent())
                    && clear(player, from, victim.getBoundingBox().getCenter())) {
                damage(player, victim, 12);
            }
        }
    }

    private static Lance advanceLance(ServerPlayer player, Lance lance) {
        Vec3 from = lance.position();
        Vec3 next = from.add(lance.velocity());
        BlockPos nextBlock = BlockPos.containing(next);
        if (!player.serverLevel().hasChunk(SectionPos.blockToSectionCoord(nextBlock.getX()),
                SectionPos.blockToSectionCoord(nextBlock.getZ()))) {
            return null;
        }
        Vec3 to = blockEnd(player, from, next);
        var hit = ProjectileUtil.getEntityHitResult(player.serverLevel(), player, from, to,
            new AABB(from, to).inflate(0.8), entity -> entity instanceof LivingEntity living && canHit(player, living),
            0.24F);
        if (hit != null) {
            to = hit.getLocation();
            damage(player, (LivingEntity) hit.getEntity(), 8);
        }
        flash(player, SolFlash.Kind.LANCE, from, to);
        if (hit != null || to.distanceToSqr(next) > 1.0E-6) {
            flash(player, SolFlash.Kind.IMPACT, to, to);
            player.serverLevel().playSound(null, BlockPos.containing(to), SoundEvents.AMETHYST_BLOCK_BREAK,
                SoundSource.PLAYERS, 0.75F, 1.3F);
            return null;
        }
        return lance.ticks() > 1 ? new Lance(to, lance.velocity(), lance.ticks() - 1) : null;
    }

    private static Vec3 handOrigin(ServerPlayer player) {
        Vec3 chest = player.getEyePosition().add(0, -0.30, 0);
        return blockEnd(player, chest, chest.add(player.getLookAngle().scale(0.6)));
    }

    private static Vec3 blockEnd(ServerPlayer player, Vec3 from, Vec3 to) {
        return player.serverLevel().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, player)).getLocation();
    }

    private static boolean clear(ServerPlayer player, Vec3 from, Vec3 to) {
        return player.serverLevel().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }

    private static boolean canHit(ServerPlayer caster, LivingEntity target) {
        return target != caster && target.isAlive() && !target.isSpectator() && !caster.isAlliedTo(target)
            && (!(target instanceof Player player) || caster.canHarmPlayer(player));
    }

    private static void damage(ServerPlayer player, LivingEntity target, float amount) {
        target.hurt(player.damageSources().source(SOLAR, player), amount);
    }

    private static void flash(ServerPlayer player, SolFlash.Kind kind, Vec3 from, Vec3 to) {
        Vec3 center = from.lerp(to, 0.5);
        PacketDistributor.sendToPlayersNear(player.serverLevel(), null, center.x, center.y, center.z,
            88, new SolFlash(kind, from, to));
    }

    private static boolean pay(ServerPlayer player, int cost) {
        if (Sol.spend(player, cost)) {
            return true;
        }
        lowReserve(player, cost);
        return false;
    }

    private static void lowReserve(ServerPlayer player, int cost) {
        player.displayClientMessage(Component.translatable("weaverlib.sol.need_reserve", cost), true);
    }

    private static final class Session {
        ServerLevel level;
        Mode mode = Mode.OFF;
        long nextModeChange;
        long nextAttack;
        long fireAt;
        long poseUntil;
        Lance lance;
    }

    private record Lance(Vec3 position, Vec3 velocity, int ticks) {}
}
