package dev.weaverlib.spell;

import com.leclowndu93150.thaumaturge.api.items.ChargeDisplay;
import com.leclowndu93150.thaumaturge.api.items.ChargeProfile;
import com.leclowndu93150.thaumaturge.api.items.RechargeAccess;
import com.leclowndu93150.thaumaturge.api.spell.Spell;
import com.leclowndu93150.thaumaturge.api.spell.cast.SpellState;
import com.leclowndu93150.thaumaturge.content.infusion.InfusionRecipe;
import com.leclowndu93150.thaumaturge.content.recipe.crucible.CrucibleRecipe;
import com.leclowndu93150.thaumaturge.registry.TTDataComponents;
import dev.weaverlib.WeaverLib;
import dev.weaverlib.sol.Sol;
import dev.weaverlib.compat.CurioInscriptions;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class Inscriptions {
    public static final int FEE = 150;
    private static final ChargeProfile BATTERY = new ChargeProfile(200, ChargeDisplay.NEVER);
    private static final DeferredRegister.DataComponents COMPONENTS =
        DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, WeaverLib.ID);
    private static final DeferredHolder<DataComponentType<?>, DataComponentType<InscribedSpell>> INSCRIPTION =
        COMPONENTS.registerComponentType("inscription", builder -> builder.persistent(InscribedSpell.CODEC)
            .networkSynchronized(ByteBufCodecs.fromCodec(InscribedSpell.CODEC)));
    private static final List<TagKey<Item>> METALS = Stream.of("ingots", "nuggets", "plates", "storage_blocks")
        .flatMap(form -> Stream.of("thaumium", "void_metal").map(metal ->
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", form + "/" + metal))))
        .toList();

    // Float stats survive native carriers and continuations. Each 16-bit UUID segment is exact.
    private static final List<ResourceLocation> SOURCE_ID = IntStream.range(0, 8)
        .mapToObj(i -> ResourceLocation.fromNamespaceAndPath(WeaverLib.ID, "inscription_source_" + i)).toList();

    private Inscriptions() {}

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        NeoForge.EVENT_BUS.addListener(Inscriptions::addTooltip);
    }

    public static InscribedSpell get(ItemStack stack) {
        return stack.get(INSCRIPTION.get());
    }

    public static void bind(ItemStack stack, InscribedSpell inscription) {
        boolean addedBattery = !RechargeAccess.isRechargeable(stack);
        int originalStackSize = stack.getMaxStackSize();
        stack.set(INSCRIPTION, new InscribedSpell(inscription.id(), inscription.spell(), inscription.words(),
            inscription.efficient(), Optional.of(addedBattery), Optional.of(originalStackSize)));
        if (originalStackSize > 1) {
            stack.set(DataComponents.MAX_STACK_SIZE, 1);
        }
        if (addedBattery) {
            stack.set(TTDataComponents.RECHARGEABLE, BATTERY);
            stack.set(TTDataComponents.CHARGE, 0);
        }
    }

    public static boolean erase(ItemStack stack) {
        InscribedSpell inscription = get(stack);
        if (inscription == null || stack.getCount() != 1) {
            return false;
        }
        var defaults = stack.getItem().components();
        // Older inscriptions did not record which item components they changed.
        boolean addedBattery = inscription.addedBattery()
            .orElseGet(() -> !defaults.has(TTDataComponents.RECHARGEABLE.get()));
        if (addedBattery && BATTERY.equals(RechargeAccess.profile(stack))) {
            stack.remove(TTDataComponents.RECHARGEABLE);
            stack.remove(TTDataComponents.CHARGE);
        }
        if (stack.getMaxStackSize() == 1) {
            int originalSize = inscription.originalStackSize()
                .orElseGet(() -> defaults.getOrDefault(DataComponents.MAX_STACK_SIZE, 1));
            stack.set(DataComponents.MAX_STACK_SIZE, originalSize);
        }
        stack.remove(INSCRIPTION);
        return true;
    }

    public static boolean efficientRecipe(ServerPlayer player, ItemStack stack) {
        return player.serverLevel().getRecipeManager().getRecipes().stream().anyMatch(holder -> {
            Recipe<?> recipe = holder.value();
            return recipe.getResultItem(player.registryAccess()).is(stack.getItem())
                && ingredients(recipe).anyMatch(Inscriptions::requiresMetal);
        });
    }

    private static Stream<Ingredient> ingredients(Recipe<?> recipe) {
        return switch (recipe) {
            case InfusionRecipe infusion -> Stream.concat(Stream.of(infusion.catalyst()), infusion.components().stream());
            case CrucibleRecipe crucible -> Stream.of(crucible.catalyst());
            default -> recipe.getIngredients().stream();
        };
    }

    private static boolean requiresMetal(Ingredient ingredient) {
        var alternatives = ingredient.getItems();
        return alternatives.length > 0 && Arrays.stream(alternatives)
            .allMatch(stack -> METALS.stream().anyMatch(stack::is));
    }

    public static SpellState tagSource(SpellState state, ItemStack source, Spell spell) {
        InscribedSpell inscription = get(source);
        if (inscription == null || inscription.spell().left().orElse(null) != spell) {
            return state;
        }
        var stats = new java.util.HashMap<>(state.stats());
        writeSourceBits(stats, 0, inscription.id().getMostSignificantBits());
        writeSourceBits(stats, 4, inscription.id().getLeastSignificantBits());
        return new SpellState(stats);
    }

    public static UUID sourceId(SpellState state) {
        if (!state.stats().containsKey(SOURCE_ID.getFirst())) {
            return null;
        }
        return new UUID(readSourceBits(state, 0), readSourceBits(state, 4));
    }

    private static void writeSourceBits(java.util.Map<ResourceLocation, Float> stats, int offset, long bits) {
        for (int i = 0; i < 4; i++) {
            stats.put(SOURCE_ID.get(offset + i), (float) ((bits >>> (i * 16)) & 0xffff));
        }
    }

    private static long readSourceBits(SpellState state, int offset) {
        long bits = 0;
        for (int i = 0; i < 4; i++) {
            bits |= (long) state.stats().getOrDefault(SOURCE_ID.get(offset + i), 0F).intValue() << (i * 16);
        }
        return bits;
    }

    public static ItemStack firstInscribedCurio(ServerPlayer player) {
        return WeaverLib.hasCurios() ? CurioInscriptions.find(player, stack -> get(stack) != null) : ItemStack.EMPTY;
    }

    private static boolean matchesInscription(ItemStack stack, UUID id) {
        var inscription = get(stack);
        return stack.getCount() == 1 && inscription != null && id.equals(inscription.id());
    }

    public static boolean payContinuation(ServerPlayer caster, UUID id, float normalVis) {
        var inventory = caster.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (matchesInscription(stack, id)) {
                return RechargeAccess.spendVis(stack, get(stack).cost(Sol.visCost(caster, normalVis)));
            }
        }
        if (!WeaverLib.hasCurios()) {
            return false;
        }
        ItemStack curio = CurioInscriptions.find(caster, stack -> matchesInscription(stack, id));
        return !curio.isEmpty() && RechargeAccess.spendVis(curio, get(curio).cost(Sol.visCost(caster, normalVis)));
    }

    private static void addTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        InscribedSpell inscription = get(stack);
        if (inscription != null) {
            event.getToolTip().add(Component.translatable("weaverlib.inscription_tooltip", inscription.words())
                .withStyle(ChatFormatting.LIGHT_PURPLE));
            ChargeProfile profile = RechargeAccess.profile(stack);
            if (profile != null) {
                event.getToolTip().add(Component.translatable("weaverlib.inscription_charge",
                    RechargeAccess.storedVis(stack), profile.capacity(), inscription.multiplier())
                    .withStyle(ChatFormatting.AQUA));
            }
        }
    }
}
