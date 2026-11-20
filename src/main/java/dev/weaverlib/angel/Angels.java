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
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.phys.HitResult;
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
import net.minecraft.world.entity.Mob;
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
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class Angels {
    private static final String KEY = "weaverlib:angel";
    private static final ResourceLocation FLIGHT_ATTRIBUTE =
        ResourceLocation.fromNamespaceAndPath(WeaverLib.ID, "angel_flight");
    public static final double SLOW_SPEED = .03;
    private static final double MELEE_RANGE = 3.2 * 1.25;
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

    public record Formation(long started, boolean dim) {
        public static final Formation NONE = new Formation(-1, false);
    }

    private static final StreamCodec<io.netty.buffer.ByteBuf, Formation> FORMATION_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_LONG, Formation::started, ByteBufCodecs.BOOL, Formation::dim, Formation::new);
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Formation>> FORMATION =
        ATTACHMENTS.register("angel_formation", () -> AttachmentType.builder(() -> Formation.NONE)
            .sync(FORMATION_CODEC).build());

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
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> DASH_STARTED =
        ATTACHMENTS.register("angel_dash_started", () -> AttachmentType.builder(() -> -1L)
            .sync(ByteBufCodecs.VAR_LONG).build());

    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> INFERNO =
        ATTACHMENTS.register("angel_inferno", () -> AttachmentType.builder(() -> false)
            .sync(ByteBufCodecs.BOOL).build());

    private static final ResourceKey<DamageType> PYRA_FIRE = ResourceKey.create(Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath(WeaverLib.ID, "pyra_fire"));

    private static final ResourceKey<DamageType> PYRA_BLAST = ResourceKey.create(Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath(WeaverLib.ID, "pyra_blast"));

    public record Charge(long started, double invested, boolean fireball) {
        public static final Charge NONE = new Charge(-1, 0, false);
        public boolean active() { return started >= 0 && invested >= PyraCharge.START_COST; }
        public double energy() { return invested; }
    }
    private static final StreamCodec<io.netty.buffer.ByteBuf, Charge> CHARGE_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_LONG, Charge::started, ByteBufCodecs.DOUBLE, Charge::invested,
        ByteBufCodecs.BOOL, Charge::fireball, Charge::new);
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Charge>> CHARGE =
        ATTACHMENTS.register("pyra_charge_v2", () -> AttachmentType.builder(() -> Charge.NONE).sync(CHARGE_CODEC).build());

    private Angels() {}

    public static void register(IEventBus bus, net.neoforged.fml.ModContainer container) {
        AngelFuel.register(container);
        ATTACHMENTS.register(bus);
        ITEMS.register(bus);
        AngelProgress.register(bus);
        NeoForge.EVENT_BUS.addListener(Angels::tick);
        NeoForge.EVENT_BUS.addListener(Angels::login);
        NeoForge.EVENT_BUS.addListener(Angels::logout);
        NeoForge.EVENT_BUS.addListener(Angels::respawn);
        NeoForge.EVENT_BUS.addListener(Angels::dimension);
        NeoForge.EVENT_BUS.addListener(Angels::died);
        NeoForge.EVENT_BUS.addListener(Angels::gameMode);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, Angels::collisionShield);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, Angels::shield);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, Angels::mantle);
        NeoForge.EVENT_BUS.addListener(Angels::commands);
    }

    public static View view(Player player) {
        return player.getExistingData(VIEW).orElse(View.HUMAN);
    }

    public static boolean inferno(Player player) {
        return player.getExistingData(INFERNO).orElse(false);
    }

    public static Charge charge(Player player) {
        return player.getExistingData(CHARGE).orElse(Charge.NONE);
    }

    public static Vec3 pyraHands(Player player, float partial) {
        return player.getEyePosition(partial).add(player.getViewVector(partial).scale(.95 * player.getScale()))
            .add(0, -.32 * player.getScale(), 0);
    }

    public static Formation formation(Player player) {
        return player.getExistingData(FORMATION).orElse(Formation.NONE);
    }

    public static long dashStarted(Player player) {
        return player.getExistingData(DASH_STARTED).orElse(-1L);
    }

    static CompoundTag data(ServerPlayer player) {
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

    static double energy(ServerPlayer player) {
        double value = data(player).getDouble("Energy");
        return Double.isFinite(value) ? Math.clamp(value, 0, AngelEnergy.capacity(rank(player))) : 0;
    }

    static void setEnergy(ServerPlayer player, double energy) {
        data(player).putDouble("Energy", Math.clamp(energy, 0, AngelEnergy.capacity(rank(player))));
    }

    public static boolean awaken(ServerPlayer player) {
        int completed = AngelProgress.advancements(player);
        if (completed < AngelLevels.ENTRY) {
            player.sendSystemMessage(Component.translatable("weaverlib.angel.entry_requirement",
                completed, AngelLevels.ENTRY));
            return false;
        }
        if (rank(player) > 0) {
            AngelProgress.refresh(player);
            return true;
        }
        revert(player);
        data(player).putInt("Rank", AngelLevels.rankAt(AngelProgress.points(player)));
        setEnergy(player, AngelEnergy.capacity(rank(player)));
        sync(player);
        return true;
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
                int form = AngelForms.invocation(action.sequence(), rank(player));
                if (form < 0) {
                    player.displayClientMessage(Component.translatable("weaverlib.angel.unwritten"), true);
                    return;
                }
                boolean fire = form == AngelForms.INFERNO;
                if (session.active && inferno(player) == fire) revert(player);
                else transform(player, fire);
            }
            case AngelAction.ATTACK -> attack(player, now);
            case AngelAction.DASH -> startDash(player, now);
            case AngelAction.STOP_DASH -> stopDash(player, now);
            case AngelAction.DEFEND -> {
                if (pyraCanCharge(player)) {
                    session.defendUntil = 0;
                    holdPyra(player, now);
                } else if (canUseWings(player)) {
                    cancelPyra(player);
                    if (session.defendUntil < now) session.defendStarted = now;
                    session.defendUntil = now + 8;
                }
            }
            case AngelAction.RELEASE -> {
                session.defendUntil = 0;
                releasePyra(player, now);
            }
            case AngelAction.CANCEL -> {
                session.defendUntil = 0;
                cancelPyra(player);
            }
            case AngelAction.FLIGHT -> {
                if (session.active && now >= session.nextFlight && !player.onGround()
                        && !player.isInWater() && !player.isPassenger()) {
                    cancelPyra(player);
                    session.nextFlight = now + 8;
                    session.fast = !session.fast;
                    if (!session.fast) stopDash(player, now);
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

    private static void transform(ServerPlayer player, boolean fire) {
        boolean sunlight = sun(player);
        boolean paid = pay(player, fire ? PyraCombat.TRANSFORM_COST : 5);
        if (!paid && !sunlight) {
            burst(player, fire ? AngelBurst.DIM | AngelBurst.FIRE : AngelBurst.DIM,
                player.position(), player.position());
            return;
        }
        var s = session(player);
        cancelPyra(player);
        // A channel must pay the activation fee for its own aspect, rather than upgrading mid-dash.
        if (s.active) stopDash(player, player.serverLevel().getGameTime());
        s.defendUntil = 0;
        s.meleeAt = 0;
        s.arcAt = 0;
        s.collisionUntil = -1;
        player.setData(INFERNO, fire);
        player.setData(FORMATION, new Formation(player.serverLevel().getGameTime(), !sunlight));
        if (s.active) {
            // Changing aspect preserves flight grants, velocity and the current flight mode.
            burst(player, sunlight ? AngelBurst.AWAKEN : AngelBurst.DIM, player.position(), player.position());
            player.serverLevel().playSound(null, player, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS,
                sunlight ? .8F : .25F, fire ? .65F : 1.3F);
            return;
        }
        var saved = data(player);
        saved.putBoolean("FlightGranted", true);
        saved.putBoolean("PreviousFlying", player.getAbilities().flying);
        saved.putFloat("PreviousFlySpeed", player.getAbilities().getFlyingSpeed());
        s.active = true;
        s.fast = false;
        s.level = player.serverLevel();
        // Sunlight also rescues a reserve below the activation fee, avoiding a stranded human form.
        var flight = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        flight.removeModifier(FLIGHT_ATTRIBUTE);
        flight.addTransientModifier(new AttributeModifier(FLIGHT_ATTRIBUTE, 1, AttributeModifier.Operation.ADD_VALUE));
        player.getAbilities().setFlyingSpeed((float) SLOW_SPEED);
        player.onUpdateAbilities();
        burst(player, sunlight ? AngelBurst.AWAKEN : AngelBurst.DIM, player.position(), player.position());
        player.serverLevel().playSound(null, player, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS,
            sunlight ? .8F : .25F, fire ? .65F : sunlight ? 1.3F : .8F);
        sync(player);
    }

    private static void revert(ServerPlayer player) {
        boolean fire = inferno(player);
        cancelPyra(player);
        player.setData(INFERNO, false);
        player.setData(FORMATION, Formation.NONE);
        player.setData(DASH_STARTED, -1L);
        var s = player.getExistingData(SESSION).orElse(null);
        boolean wasActive = s != null && s.active;
        if (s != null) {
            s.active = false;
            s.fast = false;
            s.defendUntil = 0;
            s.dash = AngelDash.NONE;
            s.spinHits.clear();
            s.spinPosition = null;
            s.meleeAt = 0;
            s.feathers.clear();
            s.pyraShots.clear();
            s.arcAt = 0;
            s.collisionUntil = -1;
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
            burst(player, fire ? AngelBurst.DIM | AngelBurst.FIRE : AngelBurst.DIM, player.position(), player.position());
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

    static boolean pay(ServerPlayer player, double cost) {
        return AngelFuel.pay(player, cost, true);
    }

    private static double upkeep(ServerPlayer player, boolean flying, boolean fast) {
        return inferno(player) ? PyraCombat.upkeep(flying, fast) : AngelEnergy.upkeep(flying, fast);
    }

    private static boolean pyraCanCharge(ServerPlayer player) {
        return inferno(player) && canUseWings(player) && !session(player).fast;
    }

    public static Vec3 pyraOrb(Player player, float partial, double invested) {
        return player.getEyePosition(partial).add(player.getViewVector(partial)
            .scale((1.8 + Math.max(0, PyraCombat.fireballOrb(invested) - .45)) * player.getScale()));
    }

    private static void holdPyra(ServerPlayer player, long now) {
        var s = session(player);
        if (s.fireballReleaseAt >= 0) return;
        boolean fireball = player.getAbilities().flying;
        if (s.charge.held(now) && s.chargeFireball == fireball) s.charge = s.charge.renew(now);
        else {
            cancelPyra(player);
            double initial = fireball ? PyraCombat.FIREBALL_START : PyraCharge.START_COST;
            if (now < s.nextAttack || !pay(player, initial)) return;
            s.chargeFireball = fireball;
            s.charge = PyraCharge.begin(now, initial);
            player.serverLevel().playSound(null, player, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, .6F, .55F);
        }
        syncCharge(player);
    }

    private static void syncCharge(ServerPlayer player) {
        var state = session(player).charge;
        Charge next = state.started() < 0 ? Charge.NONE : new Charge(state.started(), state.invested(), session(player).chargeFireball);
        if (!next.equals(charge(player))) player.setData(CHARGE, next);
    }

    private static void cancelPyra(ServerPlayer player) {
        var s = player.getExistingData(SESSION).orElse(null);
        if (s != null) {
            s.charge = PyraCharge.NONE;
            s.fireballReleaseAt = -1;
        }
        if (charge(player).active()) player.setData(CHARGE, Charge.NONE);
    }

    private static void releasePyra(ServerPlayer player, long now) {
        var s = session(player);
        var paid = s.charge;
        boolean fireball = s.chargeFireball;
        boolean ready = pyraCanCharge(player) && fireball == player.getAbilities().flying && paid.held(now);
        if (!ready) { cancelPyra(player); return; }
        if (fireball && now < paid.started() + 18) {
            // Even a quick tap completes the wing-tip gathering motion before launch; only paid energy is stored.
            s.fireballReleaseAt = paid.started() + 18;
            return;
        }
        cancelPyra(player);
        s.nextAttack = Math.max(s.nextAttack, now + 16);
        if (fireball) {
            s.pyraShots.add(PyraProjectiles.fireball(player, paid.invested()));
            player.serverLevel().playSound(null, player, SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, 1.5F, .8F);
            return;
        }
        double energy = paid.invested(), radius = PyraCharge.radius(energy);
        Vec3 direction = player.getLookAngle();
        Vec3 eye = player.getEyePosition();
        Vec3 desired = pyraHands(player, 1);
        var muzzle = player.serverLevel().clip(new ClipContext(eye, desired, ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, player));
        // The muzzle cannot be placed through a wall when the player is standing against it.
        Vec3 start = muzzle.getLocation().subtract(direction.scale(.03));
        double range = muzzle.getType() == HitResult.Type.BLOCK ? .03 : PyraCharge.range(energy);
        for (double distance = 0; distance <= range; distance += 1) {
            Vec3 at = start.add(direction.scale(distance));
            if (!player.serverLevel().getChunkSource().hasChunk((int)Math.floor(at.x) >> 4, (int)Math.floor(at.z) >> 4)) {
                range = Math.max(0, distance - 1);
                break;
            }
        }
        Vec3 end = player.serverLevel().clip(new ClipContext(start, start.add(direction.scale(range)),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation();
        var damage = pyraDamage(player);
        for (LivingEntity target : player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                new AABB(start, end).inflate(radius), living -> canHit(player, living))) {
            var expanded = target.getBoundingBox().inflate(radius);
            var intersect = expanded.clip(start, end);
            if (!expanded.contains(start) && intersect.isEmpty()) continue;
            Vec3 closest = target.getBoundingBox().getCenter();
            var obstruction = player.serverLevel().clip(new ClipContext(start, closest,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (obstruction.getType() != HitResult.Type.MISS) continue;
            if (target.hurt(damage, (float)PyraCharge.damage(energy))) {
                target.igniteForSeconds((float)(4 + 6 * Math.sqrt(PyraCharge.power(energy))));
                target.knockback(.35 + .65 * PyraCharge.power(energy), -direction.x, -direction.z);
            }
            if (!s.active) break;
        }
        var effect = new AngelBurst(AngelBurst.PYRA_BEAM | AngelBurst.FIRE, start, end, (float)energy);
        Vec3 segment = end.subtract(start);
        for (ServerPlayer observer : player.serverLevel().players()) {
            double along = segment.lengthSqr() < .001 ? 0
                : Math.clamp(observer.getEyePosition().subtract(start).dot(segment) / segment.lengthSqr(), 0, 1);
            if (observer.getEyePosition().distanceToSqr(start.add(segment.scale(along))) <= (96 + radius) * (96 + radius))
                PacketDistributor.sendToPlayer(observer, effect);
        }
        player.serverLevel().playSound(null, player, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS,
            1.2F, (float)(1.15 - PyraCharge.power(energy) / (1 + PyraCharge.power(energy)) * .45));
    }

    private static void attack(ServerPlayer player, long now) {
        var s = session(player);
        if (!canUseWings(player) || now < s.nextAttack || s.defendUntil >= now || s.charge.started() >= 0) return;
        if (s.fast) {
            startDash(player, now);
        } else if (player.getAbilities().flying) {
            boolean fire = inferno(player);
            if (!pay(player, fire ? PyraCombat.ARC_COST : 3)) return;
            s.nextAttack = now + (fire ? 16 : 10);
            if (fire) {
                s.swing = now;
                s.wing = player.getRandom().nextInt(rank(player) * 2);
                s.arcAt = now + 4;
                player.serverLevel().playSound(null, player, SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.PLAYERS, 1F, .65F);
            } else {
                s.feathers.add(new Feather(player.getEyePosition().add(player.getLookAngle().scale(.45)),
                    player.getLookAngle().scale(2), 24, false));
                player.serverLevel().playSound(null, player, SoundEvents.TRIDENT_THROW.value(),
                    SoundSource.PLAYERS, .45F, 1.9F);
            }
        } else {
            if (!pay(player, inferno(player) ? PyraCombat.MELEE_COST : 2)) return;
            s.nextAttack = now + 12;
            s.swing = now;
            s.wing = player.getRandom().nextInt(rank(player) * 2);
            s.meleeAt = now + 4;
            player.serverLevel().playSound(null, player, SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, .65F, 1.25F);
        }
    }

    private static void startDash(ServerPlayer player, long now) {
        var s = session(player);
        if (!s.fast || !canUseWings(player)) return;
        if (s.dash.active(now)) {
            s.dash = s.dash.renew(now);
            return;
        }
        if (s.dash.started() >= 0) stopDash(player, now);
        if (now < s.nextAttack || s.defendUntil >= now || !pay(player, inferno(player) ? PyraCombat.DASH_START_COST : AngelDash.START_COST)) return;
        s.nextAttack = now + 24;
        s.dash = AngelDash.begin(now);
        s.spinHits.clear();
        s.spinPosition = player.getBoundingBox().getCenter();
        player.setData(DASH_STARTED, now);
        player.setDeltaMovement(player.getLookAngle().scale(1.8));
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    private static void stopDash(ServerPlayer player, long now) {
        var s = session(player);
        if (s.dash.started() < 0) return;
        s.dash = AngelDash.NONE;
        s.spinHits.clear();
        s.spinPosition = null;
        s.nextAttack = Math.max(s.nextAttack, now + 6);
        player.setData(DASH_STARTED, -1L);
    }

    private static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var s = player.getExistingData(SESSION).orElse(null);
        if (s == null || !s.active) {
            if (player.tickCount % 20 == 0) sync(player);
            return;
        }
        if (!player.isAlive() || player.isSpectator() || player.isSleeping() || s.level != player.serverLevel()
                || rank(player) < (inferno(player) ? 2 : 1)) {
            revert(player);
            return;
        }
        long now = player.serverLevel().getGameTime();
        if (s.fast && (player.onGround() || player.isInWater() || player.isPassenger())) {
            s.fast = false;
            stopDash(player, now);
            player.stopFallFlying();
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
        boolean sunlight = sun(player);
        if (sunlight) setEnergy(player, energy(player) + AngelEnergy.sunlightPerSecond(rank(player)) / 20);
        if (!pay(player, upkeep(player, player.getAbilities().flying, s.fast))) {
            revert(player);
            return;
        }
        if (inferno(player) && now % 10 == 0) {
            for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(3),
                    mob -> mob.isAlive() && mob.distanceToSqr(player) <= 9)) {
                mob.igniteForSeconds(3);
            }
        }
        if (!canUseWings(player) || inferno(player) && !flying(player)) s.defendUntil = 0;
        if (s.fireballReleaseAt >= 0) {
            if (!pyraCanCharge(player) || !player.getAbilities().flying) cancelPyra(player);
            else if (now >= s.fireballReleaseAt) {
                double invested = s.charge.invested();
                cancelPyra(player);
                s.nextAttack = Math.max(s.nextAttack, now + 16);
                s.pyraShots.add(PyraProjectiles.fireball(player, invested));
                player.serverLevel().playSound(null, player, SoundEvents.GENERIC_EXPLODE.value(),
                    SoundSource.PLAYERS, 1.5F, .8F);
            }
        } else if (s.charge.started() >= 0) {
            if (!pyraCanCharge(player) || s.chargeFireball != player.getAbilities().flying || !s.charge.held(now)) cancelPyra(player);
            else {
                double cost = s.charge.nextCost(now) > 0
                    ? (s.chargeFireball ? PyraCombat.FIREBALL_PER_TICK : PyraCharge.PER_TICK) : 0;
                if (cost > 0 && pay(player, cost)) s.charge = s.charge.paid(now, cost);
                syncCharge(player);
            }
        }
        if (s.dash.started() >= 0) {
            if (!s.fast || !canUseWings(player) || !s.dash.active(now)
                    || !pay(player, inferno(player) ? PyraCombat.DASH_PER_TICK : AngelDash.COST_PER_TICK)) stopDash(player, now);
        }
        if (s.defendUntil >= now && player.getAbilities().flying && !s.fast && !inferno(player)) repel(player);
        if (s.arcAt != 0 && now >= s.arcAt) {
            s.arcAt = 0;
            if (canUseWings(player) && inferno(player) && player.getAbilities().flying && !s.fast)
                s.pyraShots.add(PyraProjectiles.arc(player, s.wing));
        }
        var shots = List.copyOf(s.pyraShots);
        s.pyraShots.clear();
        for (var shot : shots) {
            if (!s.active) break;
            if (PyraProjectiles.advance(player, shot) && s.active) s.pyraShots.add(shot);
        }
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
        if (s.fast && s.dash.active(now)) spinCollision(player, s);
        if (s.fast) {
            accelerate(player, s.dash.active(now));
        }
        if (energy(player) <= 0 && !sunlight && !AngelFuel.pay(player, upkeep(player, false, false), false)) revert(player);
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
                player.getBoundingBox().inflate(MELEE_RANGE, .5, MELEE_RANGE), e -> canHit(player, e))) {
            Vec3 delta = target.getBoundingBox().getCenter().subtract(origin);
            if (delta.lengthSqr() <= MELEE_RANGE * MELEE_RANGE && forward.dot(delta.normalize()) >= .2
                    && player.hasLineOfSight(target)) {
                boolean fire = inferno(player);
                boolean hit = target.hurt(player.damageSources().playerAttack(player), fire ? PyraCombat.MELEE_DAMAGE : 5);
                if (fire && hit) target.igniteForSeconds(5);
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
            boolean damaged = hit.getEntity().hurt(player.damageSources().playerAttack(player),
                5);
            if (feather.fire && damaged) hit.getEntity().igniteForSeconds(5);
        }
        burst(player, AngelBurst.FEATHER, feather.position, end, feather.fire);
        if (hit != null || end.distanceToSqr(next) > .00001) {
            burst(player, AngelBurst.FEATHER_IMPACT, end, end, feather.fire);
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
        if (inferno(player) && PyraProjectiles.wallAhead(player, previous, current)) {
            dashImpact(player, s, player.serverLevel().getGameTime());
            return;
        }
        for (LivingEntity target : player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                new AABB(previous, current).inflate(.8),
                e -> canHit(player, e) && !s.spinHits.contains(e.getUUID()))) {
            if (!s.active || !s.dash.active(player.serverLevel().getGameTime())) break;
            var hitbox = target.getBoundingBox().inflate(.65);
            if ((!hitbox.contains(previous) && !hitbox.contains(current) && hitbox.clip(previous, current).isEmpty())
                    || !player.hasLineOfSight(target)) continue;
            s.spinHits.add(target.getUUID());
            boolean fire = inferno(player);
            boolean damaged = fire ? PyraCombat.dashHit(
                () -> AngelFuel.pay(player, PyraCombat.DASH_HIT_COST, false),
                () -> target.hurt(player.damageSources().playerAttack(player), PyraCombat.DASH_DAMAGE),
                () -> pay(player, PyraCombat.DASH_HIT_COST))
                : target.hurt(player.damageSources().playerAttack(player), 9);
            if (fire && !damaged) continue;
            if (fire) target.igniteForSeconds(6);
            target.knockback(.9, -motion.x, -motion.z);
            burst(player, AngelBurst.IMPACT, target.position().add(0, .7, 0), target.position());
        }
    }

    static DamageSource pyraDamage(ServerPlayer player) {
        return new DamageSource(player.serverLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
            .getHolderOrThrow(PYRA_FIRE), player);
    }

    static DamageSource pyraBlastDamage(ServerPlayer player) {
        return new DamageSource(player.serverLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
            .getHolderOrThrow(PYRA_BLAST), player);
    }

    static boolean canHit(ServerPlayer player, LivingEntity target) {
        return target != player && target.isAlive() && !target.isSpectator() && !player.isAlliedTo(target)
            && (!(target instanceof Player other) || player.canHarmPlayer(other));
    }

    private static void dashImpact(ServerPlayer player, Session s, long now) {
        if (now < s.nextImpact) return;
        s.nextImpact = now + 20;
        s.collisionUntil = now + 6;
        stopDash(player, now);
        if (pay(player, PyraCombat.DASH_BLAST_COST)) PyraProjectiles.dashBlast(player);
    }

    private static void collisionShield(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !inferno(player)
                || !event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FLY_INTO_WALL)) return;
        var s = session(player);
        long now = player.serverLevel().getGameTime();
        if (!s.active || !(s.fast && s.dash.active(now) || now <= s.collisionUntil)) return;
        // Reserve collision protection before paying for the destructive impact.
        boolean protectedHit = pay(player, event.getAmount() * PyraCombat.COLLISION_PER_DAMAGE);
        if (s.dash.active(now)) dashImpact(player, s, now);
        if (protectedHit) event.setCanceled(true);
        sync(player);
    }

    private static void shield(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !canDefend(player)
                || flying(player) || inferno(player) || event.getSource().is(DamageTypeTags.BYPASSES_SHIELD)) return;
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
        if (!pay(player, prevented * (inferno(player) ? PyraCombat.MANTLE_MULTIPLIER : 1))) return;
        event.setNewDamage(event.getNewDamage() * .25F);
        sync(player);
    }

    private static boolean canDefend(ServerPlayer player) {
        var s = player.getExistingData(SESSION).orElse(null);
        return s != null && s.active && s.defendUntil >= player.serverLevel().getGameTime() && canUseWings(player);
    }

    static void sync(ServerPlayer player) {
        var s = player.getExistingData(SESSION).orElse(null);
        boolean active = s != null && s.active;
        long now = player.serverLevel().getGameTime();
        View next = new View(rank(player), (int) Math.round(energy(player) * 100), active,
            active && s.fast, active && s.defendUntil >= now && now >= s.shieldDisabledUntil,
            s == null ? 0 : s.wing, s == null ? 0 : s.swing, active ? s.dash.until() : 0);
        if (!next.equals(view(player))) player.setData(VIEW, next);
    }

    private static void burst(ServerPlayer player, int kind, Vec3 from, Vec3 to) {
        burst(player, kind, from, to, inferno(player));
    }

    private static void burst(ServerPlayer player, int kind, Vec3 from, Vec3 to, boolean fire) {
        PacketDistributor.sendToPlayersNear(player.serverLevel(), null, from.x, from.y, from.z, 72,
            new AngelBurst(kind | (fire ? AngelBurst.FIRE : 0), from, to));
    }

    private static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            revert(p);
            AngelProgress.refresh(p);
        }
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
            AngelProgress.refresh(p);
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

    private static int setStage(ServerPlayer player, int stage) {
        var saved = data(player);
        int previous = rank(player);
        double reserve = energy(player);
        if (!saved.contains("StageOverride")) saved.putInt("StageSavedRank", previous);
        saved.putInt("StageOverride", stage);
        saved.putInt("Rank", stage);
        setEnergy(player, previous == 0 ? AngelEnergy.capacity(stage) : reserve);
        sync(player);
        player.sendSystemMessage(Component.translatable("weaverlib.angel.stage_set",
            Component.translatable("weaverlib.angel.rank." + stage)));
        return 1;
    }

    private static int automaticStage(ServerPlayer player) {
        var saved = data(player);
        if (saved.contains("StageOverride")) {
            int previous = saved.getInt("StageSavedRank");
            double reserve = energy(player);
            saved.remove("StageOverride");
            saved.remove("StageSavedRank");
            int natural = previous > 0 || AngelProgress.advancements(player) >= AngelLevels.ENTRY
                ? Math.max(previous, AngelLevels.rankAt(AngelProgress.points(player))) : 0;
            if (natural == 0) revert(player);
            saved.putInt("Rank", natural);
            setEnergy(player, reserve);
            sync(player);
        }
        player.sendSystemMessage(Component.translatable("weaverlib.angel.stage_auto"));
        return 1;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack>
            stageCommands() {
        var command = Commands.literal("stage").requires(source -> source.hasPermission(2));
        String[] stages = {"angel", "archangel", "seraphim"};
        for (int i = 0; i < stages.length; i++) {
            int stage = i + 1;
            command.then(Commands.literal(stages[i])
                .executes(ctx -> setStage(ctx.getSource().getPlayerOrException(), stage))
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(ctx -> setStage(EntityArgument.getPlayer(ctx, "player"), stage))));
        }
        command.then(Commands.literal("auto")
            .executes(ctx -> automaticStage(ctx.getSource().getPlayerOrException()))
            .then(Commands.argument("player", EntityArgument.player())
                .executes(ctx -> automaticStage(EntityArgument.getPlayer(ctx, "player")))));
        return command;
    }

    private static void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("weaverangel")
            .then(stageCommands())
            .then(Commands.literal("status").executes(ctx -> {
                AngelProgress.status(ctx.getSource().getPlayerOrException());
                return 1;
            }))
            .then(Commands.literal("prime").requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(ctx -> AngelProgress.appointPrime(EntityArgument.getPlayer(ctx, "player")) ? 1 : 0)))
            .then(Commands.literal("grant").requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(ctx -> awaken(EntityArgument.getPlayer(ctx, "player")) ? 1 : 0)))
            .then(Commands.literal("refill").requires(source -> source.hasPermission(2)).executes(ctx -> {
                var p = ctx.getSource().getPlayerOrException();
                setEnergy(p, AngelEnergy.capacity(rank(p)));
                sync(p);
                return 1;
            }))
            .then(Commands.literal("energy").requires(source -> source.hasPermission(2)).then(Commands.argument("amount", DoubleArgumentType.doubleArg(0, 350))
                .executes(ctx -> {
                    var p = ctx.getSource().getPlayerOrException();
                    setEnergy(p, DoubleArgumentType.getDouble(ctx, "amount"));
                    sync(p);
                    return 1;
                })))
            .then(Commands.literal("remove").requires(source -> source.hasPermission(2)).executes(ctx -> {
                var p = ctx.getSource().getPlayerOrException();
                revert(p);
                data(p).putInt("Rank", 0);
                data(p).remove("Prime");
                data(p).remove("StageOverride");
                data(p).remove("StageSavedRank");
                setEnergy(p, 0);
                sync(p);
                return 1;
            })));
    }

    private static final class Session {
        boolean active;
        boolean fast;
        boolean chargeFireball;
        long fireballReleaseAt = -1;
        long arcAt;
        long collisionUntil = -1;
        long nextImpact;
        final List<PyraProjectiles.Shot> pyraShots = new ArrayList<>();
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
        AngelDash dash = AngelDash.NONE;
        PyraCharge charge = PyraCharge.NONE;
        Vec3 spinPosition;
        final Set<UUID> spinHits = new HashSet<>();
        final List<Feather> feathers = new ArrayList<>();
    }

    private static final class Feather {
        Vec3 position;
        final Vec3 velocity;
        int remaining;
        final boolean fire;

        Feather(Vec3 position, Vec3 velocity, int remaining, boolean fire) {
            this.position = position;
            this.velocity = velocity;
            this.remaining = remaining;
            this.fire = fire;
        }
    }
}
