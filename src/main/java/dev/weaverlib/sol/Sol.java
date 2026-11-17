package dev.weaverlib.sol;

import com.leclowndu93150.thaumaturge.api.spell.event.SpellCastEvent;
import com.leclowndu93150.thaumaturge.api.warp.WarpHelper;
import com.leclowndu93150.thaumaturge.api.warp.WarpType;
import dev.weaverlib.WeaverLib;
import dev.weaverlib.spell.EternalSpells;
import net.minecraft.commands.Commands;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class Sol {
    private static final String DATA_KEY = "weaverlib:sol";
    public static final int MAX_RESERVE = 100;
    private static final int DROP_ODDS = 100;
    private static final WarpType[] BURN_ORDER = {WarpType.TEMPORARY, WarpType.NORMAL, WarpType.PERMANENT};
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(WeaverLib.ID);
    private static final DeferredItem<SunStone> SUN_STONE =
        ITEMS.registerItem("sun_stone", SunStone::new, new Item.Properties().food(
            new FoodProperties.Builder().alwaysEdible().build()));

    private Sol() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        SolCombat.register(modBus);
        NeoForge.EVENT_BUS.addListener(Sol::dropSunStone);
        NeoForge.EVENT_BUS.addListener(Sol::tick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, Sol::penalizeThaumaturge);
        NeoForge.EVENT_BUS.addListener(Sol::registerCommands);
    }

    private static boolean inSunlight(ServerLevel level, BlockPos pos) {
        return level.dimensionType().hasSkyLight() && !level.dimensionType().hasFixedTime()
            && level.isDay() && !level.isRaining() && level.canSeeSky(pos);
    }

    private static void dropSunStone(BlockDropsEvent event) {
        ServerLevel level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!(event.getBreaker() instanceof ServerPlayer player) || player.isCreative()
                || !event.getState().is(Blocks.GRAVEL) || !inSunlight(level, pos.above())
                || level.random.nextInt(DROP_ODDS) != 0) {
            return;
        }

        ItemEntity drop = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5,
            pos.getZ() + 0.5, new ItemStack(SUN_STONE.get()));
        drop.setDefaultPickUpDelay();
        event.getDrops().add(drop);
    }

    private static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.isSpectator() || !unlocked(player)) {
            return;
        }

        SolCombat.tick(player);
        CompoundTag data = data(player);
        if (player.getY() < player.level().getMinBuildHeight() - 64 && !data.getBoolean("CanRelinquish")) {
            data.putBoolean("CanRelinquish", true);
            player.sendSystemMessage(Component.translatable("weaverlib.sol.void_choice"));
        }
        if (!player.isAlive() || player.tickCount % 20 != 0
                || !inSunlight(player.serverLevel(), BlockPos.containing(player.getEyePosition()))) {
            return;
        }

        var warp = WarpHelper.getWarp(player);
        for (WarpType type : BURN_ORDER) {
            if (warp.get(type) > 0) {
                WarpHelper.addWarp(player, -1, type);
                player.displayClientMessage(Component.translatable("weaverlib.sol.burning_warp"), true);
                player.serverLevel().sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.2,
                    player.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
                return;
            }
        }

        int reserve = data.getInt("Reserve");
        if (reserve < MAX_RESERVE) {
            int charged = reserve + 1;
            data.putInt("Reserve", charged);
            SolCombat.refresh(player);
            player.serverLevel().sendParticles(ParticleTypes.WAX_ON, player.getX(), player.getY() + 1.2,
                player.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
        }
    }

    public static boolean unlocked(ServerPlayer player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG)
            .getCompound(DATA_KEY).getBoolean("Unlocked");
    }

    static void unlock(ServerPlayer player) {
        data(player).putBoolean("Unlocked", true);
        EternalSpells.halt(player);
        SolCombat.refresh(player);
        player.displayClientMessage(Component.translatable("weaverlib.sol.unlocked"), true);
        player.serverLevel().sendParticles(ParticleTypes.WAX_ON, player.getX(), player.getY() + 1.2,
            player.getZ(), 24, 0.45, 0.55, 0.45, 0.0);
    }

    static int reserve(ServerPlayer player) {
        return Math.clamp(data(player).getInt("Reserve"), 0, MAX_RESERVE);
    }

    static boolean spend(ServerPlayer player, int cost) {
        int current = reserve(player);
        if (current < cost) {
            return false;
        }
        data(player).putInt("Reserve", current - cost);
        SolCombat.refresh(player);
        return true;
    }

    public static float visCost(ServerPlayer player, float normalVis) {
        return unlocked(player) ? normalVis * 5 : normalVis;
    }

    public static float potency(ServerPlayer player) {
        return unlocked(player) ? 0.5F : 1.0F;
    }

    private static void penalizeThaumaturge(SpellCastEvent.Pre event) {
        if (event.caster() instanceof ServerPlayer player && unlocked(player)) {
            event.setVisCost(visCost(player, event.visCost()));
            event.setPower(event.power() * potency(player));
        }
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sol").then(Commands.literal("relinquish")
            .executes(context -> relinquish(context.getSource().getPlayerOrException()))));
    }

    private static int relinquish(ServerPlayer player) {
        if (!unlocked(player) || !data(player).getBoolean("CanRelinquish")) {
            player.sendSystemMessage(Component.translatable("weaverlib.sol.no_void_choice"));
            return 0;
        }

        player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).remove(DATA_KEY);
        SolCombat.reset(player);
        player.sendSystemMessage(Component.translatable("weaverlib.sol.relinquished"));
        return 1;
    }

    private static CompoundTag data(ServerPlayer player) {
        // NeoForge copies PlayerPersisted when the player respawns.
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(DATA_KEY, Tag.TAG_COMPOUND)) {
            persisted.put(DATA_KEY, new CompoundTag());
        }
        return persisted.getCompound(DATA_KEY);
    }
}
