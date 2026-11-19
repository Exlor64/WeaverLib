package dev.weaverlib.angel;

import dev.weaverlib.WeaverLib;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class Angels {
    private static final String KEY = "weaverlib:angel";
    private static final ResourceLocation FLIGHT_ATTRIBUTE =
        ResourceLocation.fromNamespaceAndPath(WeaverLib.ID, "angel_flight");
    public static final double SLOW_SPEED = .03;
    // Retain the old item ID so worlds containing a Sun Stone still load cleanly.
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(WeaverLib.ID);
    static {
        ITEMS.registerSimpleItem("sun_stone");
    }
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, WeaverLib.ID);

    public record View(int rank, int energy, boolean active, boolean fast, boolean defending, int wing, long swing,
                       long spin) {
        public static final View HUMAN = new View(0, 0, false, false, false, 0, 0, 0);
    }

    private static final StreamCodec<io.netty.buffer.ByteBuf, View> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, View::rank, ByteBufCodecs.VAR_INT, View::energy,
        ByteBufCodecs.VAR_INT, v -> (v.active() ? 1 : 0) | (v.fast() ? 2 : 0) | (v.defending() ? 4 : 0),
        ByteBufCodecs.VAR_INT, View::wing, ByteBufCodecs.VAR_LONG, View::swing, ByteBufCodecs.VAR_LONG, View::spin,
        (rank, energy, flags, wing, swing, spin) ->
            new View(rank, energy, (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0, wing, swing, spin));
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<View>> VIEW =
        ATTACHMENTS.register("angel_view", () -> AttachmentType.builder(() -> View.HUMAN).sync(CODEC).build());
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Session>> SESSION =
        ATTACHMENTS.register("angel_session", () -> AttachmentType.builder(Session::new).build());

    private Angels() {}

    public static void register(IEventBus bus) {
        ATTACHMENTS.register(bus);
        ITEMS.register(bus);
        NeoForge.EVENT_BUS.addListener(Angels::tick);
        NeoForge.EVENT_BUS.addListener(Angels::login);
        NeoForge.EVENT_BUS.addListener(Angels::logout);
        NeoForge.EVENT_BUS.addListener(Angels::respawn);
        NeoForge.EVENT_BUS.addListener(Angels::dimension);
        NeoForge.EVENT_BUS.addListener(Angels::died);
        NeoForge.EVENT_BUS.addListener(Angels::gameMode);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, Angels::shield);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, Angels::mantle);
        NeoForge.EVENT_BUS.addListener(Angels::commands);
    }

    public static View view(Player player) {
        return player.getExistingData(VIEW).orElse(View.HUMAN);
    }

    private static CompoundTag data(ServerPlayer player) {
        var root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, 10)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        var persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(KEY, 10)) {
            persisted.put(KEY, new CompoundTag());
            var legacy = persisted.getCompound("weaverlib:sol");
            if (legacy.getBoolean("Unlocked")) {
                var angel = persisted.getCompound(KEY);
                angel.putInt("Rank", 1);
                angel.putDouble("Energy", Math.clamp(legacy.getInt("Reserve"), 0, 100));
                persisted.remove("weaverlib:sol");
            }
        }
        return persisted.getCompound(KEY);
    }

    public static int rank(ServerPlayer player) {
        return Math.clamp(data(player).getInt("Rank"), 0, 3);
    }

    private static double energy(ServerPlayer player) {
        double value = data(player).getDouble("Energy");
        return Double.isFinite(value) ? Math.clamp(value, 0, AngelEnergy.capacity(rank(player))) : 0;
    }

    private static void setEnergy(ServerPlayer player, double energy) {
        data(player).putDouble("Energy", Math.clamp(energy, 0, AngelEnergy.capacity(rank(player))));
    }

    public static void grant(ServerPlayer player, int rank) {
        revert(player);
        data(player).putInt("Rank", rank);
        setEnergy(player, AngelEnergy.capacity(rank));
        sync(player);
    }

    private static Session session(ServerPlayer player) {
        return player.getData(SESSION);
    }

    public static void handle(ServerPlayer player, AngelAction action) {
        if (rank(player) == 0 || !player.isAlive() || player.isSpectator()) {
            return;
        }
        var session = session(player);
        long now = player.serverLevel().getGameTime();
        switch (action.action()) {
            case AngelAction.INVOKE -> {
                if (now < session.nextInvoke || action.sequence() < 0 || action.sequence() > 26) return;
                session.nextInvoke = now + 10;
                int code = action.sequence();
                for (int i = 0; i < 3; i++, code /= 3) {
                    if (code % 3 >= rank(player)) return;
                }
                if (action.sequence() != 0) {
                    player.displayClientMessage(Component.translatable("weaverlib.angel.unwritten"), true);
                    return;
                }
                if (session.active) revert(player);
                else transform(player);
            }
            case AngelAction.ATTACK -> attack(player, now);
            case AngelAction.DEFEND -> {
                if (canUseWings(player)) {
                    if (session.defendUntil < now) session.defendStarted = now;
                    session.defendUntil = now + 8;
                }
            }
            case AngelAction.RELEASE -> session.defendUntil = 0;
            case AngelAction.FLIGHT -> {
                if (session.active && now >= session.nextFlight && !player.onGround()
                        && !player.isInWater() && !player.isPassenger()) {
                    session.nextFlight = now + 8;
                    session.fast = !session.fast;
                    player.getAbilities().flying = !session.fast;
                    if (session.fast) {
                        player.startFallFlying();
                        player.setDeltaMovement(player.getLookAngle().scale(.7));
                    } else {
                        player.stopFallFlying();
                    }
                    player.onUpdateAbilities();
                }
            }
            default -> { return; }
        }
        sync(player);
    }

    private static boolean sun(ServerPlayer player) {
        var level = player.serverLevel();
        return level.dimensionType().hasSkyLight() && !level.dimensionType().hasFixedTime()
            && level.isDay() && !level.isRaining() && level.canSeeSky(BlockPos.containing(player.getEyePosition()));
    }

    private static void transform(ServerPlayer player) {
        boolean sunlight = sun(player);
        double current = energy(player);
        if (!AngelEnergy.canPay(current, 5) && !sunlight) {
            burst(player, AngelBurst.DIM, player.position(), player.position());
            return;
        }
        var s = session(player);
        var saved = data(player);
        saved.putBoolean("FlightGranted", true);
        saved.putBoolean("PreviousFlying", player.getAbilities().flying);
        saved.putFloat("PreviousFlySpeed", player.getAbilities().getFlyingSpeed());
        s.active = true;
        s.fast = false;
        s.level = player.serverLevel();
        // Sunlight also rescues a reserve below the activation fee, avoiding a stranded human form.
        if (AngelEnergy.canPay(current, 5)) setEnergy(player, current - 5);
        var flight = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        flight.removeModifier(FLIGHT_ATTRIBUTE);
        flight.addTransientModifier(new AttributeModifier(FLIGHT_ATTRIBUTE, 1, AttributeModifier.Operation.ADD_VALUE));
        player.getAbilities().setFlyingSpeed((float) SLOW_SPEED);
        player.onUpdateAbilities();
        burst(player, sunlight ? AngelBurst.AWAKEN : AngelBurst.DIM, player.position(), player.position());
        player.serverLevel().playSound(null, player, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS,
            sunlight ? .8F : .25F, sunlight ? 1.3F : .8F);
        sync(player);
    }

    private static void revert(ServerPlayer player) {
        var s = player.getExistingData(SESSION).orElse(null);
        boolean wasActive = s != null && s.active;
        if (s != null) {
            s.active = false;
            s.fast = false;
            s.defendUntil = 0;
            s.spinUntil = 0;
            s.meleeAt = 0;
            s.feathers.clear();
        }
        player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT).removeModifier(FLIGHT_ATTRIBUTE);
        var saved = data(player);
        if (saved.getBoolean("FlightGranted")) {
            if (!player.isCreative() && !player.isSpectator()) {
                player.getAbilities().flying = player.mayFly() && saved.getBoolean("PreviousFlying");
            }
            float speed = saved.getFloat("PreviousFlySpeed");
            player.getAbilities().setFlyingSpeed(speed > 0 && Float.isFinite(speed) ? speed : .05F);
            saved.remove("FlightGranted");
            player.stopFallFlying();
            player.onUpdateAbilities();
        }
        if (wasActive && player.isAlive()) {
            burst(player, AngelBurst.DIM, player.position(), player.position());
        }
        sync(player);
    }

    private static boolean canUseWings(ServerPlayer player) {
        return session(player).active && player.getMainHandItem().isEmpty()
            && !player.isUsingItem() && !player.isSleeping() && !player.isPassenger();
    }

    public static boolean flying(Player player) {
        return view(player).fast() || player.getAbilities().flying;
    }

    private static boolean pay(ServerPlayer player, double cost) {
        double energy = energy(player);
        if (!AngelEnergy.canPay(energy, cost)) return false;
        setEnergy(player, AngelEnergy.afterPayment(energy, cost));
        return true;
    }

    private static void attack(ServerPlayer player, long now) {
        var s = session(player);
        if (!canUseWings(player) || now < s.nextAttack || s.defendUntil >= now) return;
        if (s.fast) {
            if (!pay(player, 10)) return;
            s.nextAttack = now + 24;
            s.spinUntil = now + 18;
            s.spinHits.clear();
            s.spinPosition = player.getBoundingBox().getCenter();
            player.setDeltaMovement(player.getLookAngle().scale(1.8));
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        } else if (player.getAbilities().flying) {
            if (!pay(player, 3)) return;
            s.nextAttack = now + 10;
            s.feathers.add(new Feather(player.getEyePosition().add(player.getLookAngle().scale(.45)),
                player.getLookAngle().scale(2), 24));
            player.serverLevel().playSound(null, player, SoundEvents.TRIDENT_THROW.value(),
                SoundSource.PLAYERS, .45F, 1.9F);
        } else {
            if (!pay(player, 2)) return;
            s.nextAttack = now + 12;
            s.swing = now;
            s.wing = player.getRandom().nextInt(rank(player) * 2);
            s.meleeAt = now + 4;
            player.serverLevel().playSound(null, player, SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, .65F, 1.25F);
        }
    }

    private static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var s = player.getExistingData(SESSION).orElse(null);
        if (s == null || !s.active) {
            if (player.tickCount % 20 == 0) sync(player);
            return;
        }
        if (!player.isAlive() || player.isSpectator() || player.isSleeping() || s.level != player.serverLevel()) {
            revert(player);
            return;
        }
        long now = player.serverLevel().getGameTime();
        if (s.fast && (player.onGround() || player.isInWater() || player.isPassenger())) {
            s.fast = false;
            s.spinUntil = 0;
            player.stopFallFlying();
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
        boolean sunlight = sun(player);
        if (sunlight) setEnergy(player, energy(player) + AngelEnergy.sunlightPerSecond(rank(player)) / 20);
        if (!pay(player, AngelEnergy.upkeep(player.getAbilities().flying, s.fast))) {
            revert(player);
            return;
        }
        if (!canUseWings(player)) s.defendUntil = 0;
        if (s.defendUntil >= now && player.getAbilities().flying && !s.fast) repel(player);
        if (s.meleeAt != 0 && now >= s.meleeAt) {
            s.meleeAt = 0;
            if (canUseWings(player) && !s.fast && !player.getAbilities().flying) melee(player);
        }
        // Damage callbacks can end the form and clear its projectiles during an impact.
        var pending = List.copyOf(s.feathers);
        s.feathers.clear();
        for (Feather feather : pending) {
            if (!s.active) break;
            if (advanceFeather(player, feather) && s.active) s.feathers.add(feather);
        }
        if (!s.active) return;
        if (s.fast && s.spinUntil > now) spinCollision(player, s);
        if (s.fast) {
            accelerate(player, s.spinUntil > now);
            if (player.tickCount % 2 == 0) {
                var at = player.position().add(0, .8, 0);
                player.serverLevel().sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 2, .2, .2, .2, .005);
            }
        }
        if (energy(player) <= 0) revert(player);
        else sync(player);
    }

    public static void accelerate(Player player, boolean spinning) {
        var direction = player.getLookAngle();
        var current = player.getDeltaMovement();
        double target = spinning ? 2.15 : 1.10;
        player.setDeltaMovement(current.scale(.90).add(direction.scale(target * .10)).add(0, .025, 0));
        player.resetFallDistance();
    }

    private static void melee(ServerPlayer player) {
        Vec3 origin = player.getEyePosition();
        Vec3 forward = player.getLookAngle().multiply(1, 0, 1).normalize();
        for (LivingEntity target : player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(3.0, .5, 3.0), e -> canHit(player, e))) {
            Vec3 delta = target.getBoundingBox().getCenter().subtract(origin);
            if (delta.lengthSqr() <= 3.2 * 3.2 && forward.dot(delta.normalize()) >= .2
                    && player.hasLineOfSight(target)) {
                target.hurt(player.damageSources().playerAttack(player), 5);
                target.knockback(.5, -delta.x, -delta.z);
            }
        }
    }

    private static boolean advanceFeather(ServerPlayer player, Feather feather) {
        Vec3 next = feather.position.add(feather.velocity);
        if (!player.serverLevel().getChunkSource().hasChunk((int) Math.floor(next.x) >> 4, (int) Math.floor(next.z) >> 4)) return false;
        Vec3 end = player.serverLevel().clip(new ClipContext(feather.position, next, ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, player)).getLocation();
        var hit = ProjectileUtil.getEntityHitResult(player.serverLevel(), player, feather.position, end,
            new AABB(feather.position, end).inflate(.5),
            e -> e instanceof LivingEntity living && canHit(player, living), .18F);
        if (hit != null) {
            end = hit.getLocation();
            hit.getEntity().hurt(player.damageSources().playerAttack(player), 5);
        }
        burst(player, AngelBurst.FEATHER, feather.position, end);
        if (hit != null || end.distanceToSqr(next) > .00001) {
            burst(player, AngelBurst.IMPACT, end, end);
            return false;
        }
        feather.position = end;
        return --feather.remaining > 0;
    }

    private static void repel(ServerPlayer player) {
        var entities = player.serverLevel().getEntities(player, player.getBoundingBox().inflate(4.5),
            e -> e.isAlive() && e.distanceToSqr(player) <= 4.5 * 4.5);
        if (entities.isEmpty() || !pay(player, entities.size() / 20.0)) return;
        Vec3 center = player.getBoundingBox().getCenter();
        for (Entity entity : entities) {
            Vec3 direction = entity.getBoundingBox().getCenter().subtract(center).normalize();
            if (direction.lengthSqr() < .01) direction = new Vec3(0, 1, 0);
            Vec3 old = entity.getDeltaMovement();
            Vec3 motion = entity instanceof Projectile
                ? direction.scale(Math.max(1.3, old.length() * 1.05))
                : old.scale(.45).add(direction.scale(.48));
            entity.setDeltaMovement(motion);
            entity.hurtMarked = true;
            if (entity instanceof ServerPlayer other) other.connection.send(new ClientboundSetEntityMotionPacket(other));
        }
    }

    private static void spinCollision(ServerPlayer player, Session s) {
        Vec3 motion = player.getDeltaMovement();
        Vec3 current = player.getBoundingBox().getCenter();
        Vec3 previous = s.spinPosition == null ? current : s.spinPosition;
        s.spinPosition = current;
        for (LivingEntity target : player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                new AABB(previous, current).inflate(.8),
                e -> canHit(player, e) && !s.spinHits.contains(e.getUUID()))) {
            var hitbox = target.getBoundingBox().inflate(.65);
            if ((!hitbox.contains(previous) && !hitbox.contains(current) && hitbox.clip(previous, current).isEmpty())
                    || !player.hasLineOfSight(target)) continue;
            s.spinHits.add(target.getUUID());
            target.hurt(player.damageSources().playerAttack(player), 9);
            target.knockback(.9, -motion.x, -motion.z);
            burst(player, AngelBurst.IMPACT, target.position().add(0, .7, 0), target.position());
        }
    }

    private static boolean canHit(ServerPlayer player, LivingEntity target) {
        return target != player && target.isAlive() && !target.isSpectator() && !player.isAlliedTo(target)
            && (!(target instanceof Player other) || player.canHarmPlayer(other));
    }

    private static void shield(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !canDefend(player)
                || flying(player) || event.getSource().is(DamageTypeTags.BYPASSES_SHIELD)) return;
        var s = session(player);
        long now = player.serverLevel().getGameTime();
        if (now - s.defendStarted < 5 || now < s.shieldDisabledUntil) return;
        Vec3 source = event.getSource().getSourcePosition();
        if (source == null || player.getLookAngle().multiply(1, 0, 1).normalize()
                .dot(source.subtract(player.position()).multiply(1, 0, 1).normalize()) <= 0) return;
        if (event.getSource().getDirectEntity() instanceof AbstractArrow arrow && arrow.getPierceLevel() > 0) return;
        if (!pay(player, event.getAmount())) return;
        event.setCanceled(true);
        if (event.getSource().getDirectEntity() instanceof LivingEntity attacker) {
            if (attacker.getMainHandItem().is(ItemTags.AXES)) s.shieldDisabledUntil = now + 100;
            attacker.knockback(.35, player.getX() - attacker.getX(), player.getZ() - attacker.getZ());
        }
        player.serverLevel().playSound(null, player, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, .6F, 1.3F);
        sync(player);
    }

    private static void mantle(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !canDefend(player)
                || !session(player).fast || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        double prevented = event.getNewDamage() * .75;
        if (!pay(player, prevented)) return;
        event.setNewDamage(event.getNewDamage() * .25F);
        sync(player);
    }

    private static boolean canDefend(ServerPlayer player) {
        var s = player.getExistingData(SESSION).orElse(null);
        return s != null && s.active && s.defendUntil >= player.serverLevel().getGameTime() && canUseWings(player);
    }

    private static void sync(ServerPlayer player) {
        var s = player.getExistingData(SESSION).orElse(null);
        boolean active = s != null && s.active;
        long now = player.serverLevel().getGameTime();
        View next = new View(rank(player), (int) Math.round(energy(player) * 100), active,
            active && s.fast, active && s.defendUntil >= now && now >= s.shieldDisabledUntil,
            s == null ? 0 : s.wing, s == null ? 0 : s.swing, active ? s.spinUntil : 0);
        if (!next.equals(view(player))) player.setData(VIEW, next);
    }

    private static void burst(ServerPlayer player, int kind, Vec3 from, Vec3 to) {
        PacketDistributor.sendToPlayersNear(player.serverLevel(), null, from.x, from.y, from.z, 72,
            new AngelBurst(kind, from, to));
    }

    private static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) revert(p);
    }

    private static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            revert(p);
            p.removeData(SESSION);
        }
    }

    private static void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            p.removeData(SESSION);
            revert(p);
        }
    }

    private static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) revert(p);
    }

    private static void died(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) revert(p);
    }

    private static void gameMode(PlayerEvent.PlayerChangeGameModeEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) revert(p);
    }

    private static void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("weaverangel").requires(source -> source.hasPermission(2))
            .then(Commands.literal("grant").then(Commands.argument("player", EntityArgument.player())
                .executes(ctx -> { grant(EntityArgument.getPlayer(ctx, "player"), 1); return 1; })))
            .then(Commands.literal("refill").executes(ctx -> {
                var p = ctx.getSource().getPlayerOrException();
                setEnergy(p, AngelEnergy.capacity(rank(p)));
                sync(p);
                return 1;
            }))
            .then(Commands.literal("energy").then(Commands.argument("amount", DoubleArgumentType.doubleArg(0, 350))
                .executes(ctx -> {
                    var p = ctx.getSource().getPlayerOrException();
                    setEnergy(p, DoubleArgumentType.getDouble(ctx, "amount"));
                    sync(p);
                    return 1;
                })))
            .then(Commands.literal("remove").executes(ctx -> {
                var p = ctx.getSource().getPlayerOrException();
                revert(p);
                data(p).putInt("Rank", 0);
                setEnergy(p, 0);
                sync(p);
                return 1;
            })));
    }

    private static final class Session {
        boolean active;
        boolean fast;
        ServerLevel level;
        long defendUntil;
        long defendStarted;
        long shieldDisabledUntil;
        long nextAttack;
        long nextInvoke;
        long nextFlight;
        long swing;
        long meleeAt;
        int wing;
        long spinUntil;
        Vec3 spinPosition;
        final Set<UUID> spinHits = new HashSet<>();
        final List<Feather> feathers = new ArrayList<>();
    }

    private static final class Feather {
        Vec3 position;
        final Vec3 velocity;
        int remaining;

        Feather(Vec3 position, Vec3 velocity, int remaining) {
            this.position = position;
            this.velocity = velocity;
            this.remaining = remaining;
        }
    }
}
