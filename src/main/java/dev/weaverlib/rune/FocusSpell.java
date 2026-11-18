package dev.weaverlib.rune;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.spell.Spell;
import com.leclowndu93150.thaumaturge.api.spell.SpellNode;
import com.leclowndu93150.thaumaturge.api.spell.Spells;
import com.leclowndu93150.thaumaturge.api.spell.part.SettingSpec;
import com.leclowndu93150.thaumaturge.api.spell.part.SpellPart;
import com.leclowndu93150.thaumaturge.api.spell.part.SpellPartKind;
import dev.weaverlib.rune.AonDrawing.Point;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public final class FocusSpell {
    public static final int MAX_ARGUMENT_BYTES = 192;
    private static final Pattern TOKEN = Pattern.compile("([a-z_\u002d]+)(?:\\[([^\\[\\]]+)\\])?");

    private FocusSpell() {}

    public static Spell fromWords(String phrase, HolderLookup.Provider registries) {
        return compile(wordInputs(phrase), registries);
    }

    public static Compiled readWords(String phrase, HolderLookup.Provider registries) {
        return compiled(wordInputs(phrase), registries);
    }

    private static List<PartInput> wordInputs(String phrase) {
        String[] words = phrase.strip().toLowerCase(Locale.ROOT).split("(?U)\\s+");
        if (words.length > AonDrawing.MAX_GLYPHS) {
            throw invalid("focus_layout");
        }
        List<PartInput> parts = new ArrayList<>();
        for (String word : words) {
            if (word.equals("nihil") || word.equals("null")) {
                continue;
            }
            var matcher = TOKEN.matcher(word);
            if (!matcher.matches()) {
                throw invalid("focus_arguments");
            }
            FocusComponent part = FocusComponent.spoken(matcher.group(1));
            if (part == null) {
                throw invalid("unknown_words");
            }
            parts.add(new PartInput(part, matcher.group(2) == null ? "" : matcher.group(2)));
        }
        return parts;
    }

    public static Spell fromRunes(List<List<List<Point>>> glyphs, List<String> arguments,
            HolderLookup.Provider registries) {
        return compile(runeInputs(glyphs, arguments), registries);
    }

    public static Compiled readRunes(List<List<List<Point>>> glyphs, List<String> arguments,
            HolderLookup.Provider registries) {
        return compiled(runeInputs(glyphs, arguments), registries);
    }

    private static List<PartInput> runeInputs(List<List<List<Point>>> glyphs, List<String> arguments) {
        List<PartInput> parts = new ArrayList<>();
        for (int index : AonDrawing.clockwiseOrder(glyphs)) {
            var glyph = glyphs.get(index);
            FocusComponent part = FocusComponent.recognize(glyph);
            if (part != null) {
                parts.add(new PartInput(part, arguments.get(index)));
            } else {
                RuneModifier modifier = RuneModifier.recognize(glyph);
                if (modifier == RuneModifier.PULSE) {
                    throw invalid("pulse_needs_base");
                }
                if (index == 0 || !arguments.get(index).isEmpty() || modifier != RuneModifier.NULL) {
                    throw invalid("unknown_rune");
                }
            }
        }
        return parts;
    }

    private static Compiled compiled(List<PartInput> parts, HolderLookup.Provider registries) {
        Spell spell = compile(parts, registries);
        var phrase = new java.util.StringJoiner(" ", "incant ", "");
        for (PartInput input : parts) {
            String arguments = input.arguments();
            phrase.add(input.component().incantation() + (arguments.isEmpty() ? "" : "[" + arguments + "]"));
        }
        return new Compiled(spell, phrase.toString());
    }

    public record Compiled(Spell spell, String incantation) {}

    private static Spell compile(List<PartInput> parts, HolderLookup.Provider registries) {
        var stack = new ArrayDeque<SpellNode>();
        for (PartInput input : parts) {
            FocusComponent component = input.component();
            if (component.word().equals("inscribe")) {
                throw invalid("inscribe_layout");
            }
            if (component == FocusComponent.named("erase")) {
                throw invalid("erase_layout");
            }
            if (component == FocusComponent.named("osseous")) {
                throw invalid("goety_standalone");
            }
            SpellPart part = Spells.part(registries, component.key())
                .filter(found -> !found.hidden() && found.kind() == component.kind())
                .orElseThrow(() -> invalid("focus_unavailable"));
            SpellNode node = applyArguments(SpellNode.of(component.key()), part, input.arguments(), registries);
            if (component.kind() == SpellPartKind.EFFECT) {
                stack.push(node);
                continue;
            }
            int children = component.word().equals("branch") ? 2 : 1;
            if (stack.size() < children) {
                throw invalid("focus_layout");
            }
            if (children == 2) {
                SpellNode right = stack.pop();
                node = node.withChildren(List.of(stack.pop(), right));
            } else {
                node = node.then(stack.pop());
            }
            stack.push(node);
        }
        if (stack.size() != 1) {
            throw invalid("focus_layout");
        }
        return Spell.empty().withRoot(SpellNode.of(Spell.ORIGIN).then(addDefaultDelivery(stack.pop())));
    }

    private static SpellNode addDefaultDelivery(SpellNode node) {
        var subtree = new ArrayList<FocusComponent>();
        node.forEach(child -> subtree.add(FocusComponent.named(child.part().location().getPath())));
        if (subtree.getFirst().kind() == SpellPartKind.DELIVERY) {
            return node;
        }
        if (subtree.stream().noneMatch(part -> part.kind() == SpellPartKind.DELIVERY)) {
            boolean self = subtree.stream().filter(part -> part.kind() == SpellPartKind.EFFECT)
                .allMatch(part -> switch (part.word()) {
                    case "heal", "imbue", "leap", "halt" -> true;
                    default -> false;
                });
            return SpellNode.of(FocusComponent.named(self ? "self" : "touch").key()).then(node);
        }
        return node.withChildren(node.children().stream().map(FocusSpell::addDefaultDelivery).toList());
    }

    private static SpellNode applyArguments(SpellNode node, SpellPart part, String arguments,
            HolderLookup.Provider registries) {
        if (arguments.isEmpty()) {
            return node;
        }
        if (arguments.length() > MAX_ARGUMENT_BYTES) {
            throw invalid("focus_arguments");
        }
        var seen = new java.util.HashSet<String>();
        for (String entry : arguments.split(",", -1)) {
            String[] field = entry.split("=", -1);
            if (field.length != 2 || field[1].isEmpty() || !seen.add(field[0])) {
                throw invalid("focus_arguments");
            }
            if (field[1].equals("nihil") || field[1].equals("null")) {
                if (field[0].equals("aspect") ? !part.aspect().selectable() : part.setting(field[0]).isEmpty()) {
                    throw invalid("focus_arguments");
                }
                continue;
            }
            if (field[0].equals("aspect")) {
                ResourceLocation id = field[1].contains(":") ? ResourceLocation.tryParse(field[1])
                    : ResourceLocation.tryBuild("thaumaturge", field[1]);
                if (id == null || !part.aspect().selectable()) {
                    throw invalid("focus_arguments");
                }
                ResourceKey<IAspect> key = ResourceKey.create(IAspect.REGISTRY_KEY, id);
                if (!part.aspect().options(registries).contains(key)) {
                    throw invalid("focus_arguments");
                }
                node = node.withAspect(Optional.of(key));
            } else {
                SettingSpec setting = part.setting(field[0]).orElseThrow(() -> invalid("focus_arguments"));
                node = node.withSetting(setting.key(), parseSetting(setting, field[1]));
            }
        }
        return node;
    }

    private static int parseSetting(SettingSpec spec, String input) {
        if (spec.labels().isPresent()) {
            List<String> labels = spec.labels().get();
            for (int i = 0; i < labels.size(); i++) {
                String label = labels.get(i);
                if (label.equals(input) || label.substring(label.lastIndexOf('.') + 1).equals(input)) {
                    return spec.min() + i * spec.step();
                }
            }
        }
        try {
            int value = Integer.parseInt(input);
            if (spec.clamp(value) == value) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // Known labels and integers on the native step grid are the accepted values.
        }
        throw invalid("focus_arguments");
    }

    private static IllegalArgumentException invalid(String key) {
        return new IllegalArgumentException(key);
    }

    private record PartInput(FocusComponent component, String arguments) {}
}
