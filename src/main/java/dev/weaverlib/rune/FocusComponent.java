package dev.weaverlib.rune;

import com.leclowndu93150.thaumaturge.api.spell.part.SpellPart;
import com.leclowndu93150.thaumaturge.api.spell.part.SpellPartKind;
import dev.weaverlib.rune.AonDrawing.Point;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public record FocusComponent(String word, SpellPartKind kind, ResourceKey<SpellPart> key,
                             AonRecognition.Template template) {
    private static final Map<String, FocusComponent> COMPONENTS = new LinkedHashMap<>();

    static {
        add(SpellPartKind.EFFECT,
            "fire frost air earth flux curse heal burst imbue break exchange rift ward primal hellbat blink",
            List.of(new Point(.1, .78), new Point(.1, .2), new Point(.9, .2), new Point(.9, .78)));
        add(SpellPartKind.EFFECT, "harvest",
            List.of(new Point(.1, .2), new Point(.9, .2), new Point(.9, .8), new Point(.1, .8), new Point(.1, .2)));
        add(SpellPartKind.DELIVERY,
            "touch bolt beam projectile lob spray chain aura self cloud mine wall spellbat sprite plan",
            List.of(new Point(.1, .18), new Point(.1, .82), new Point(.9, .82), new Point(.9, .18)));
        add(SpellPartKind.MODIFIER,
            "amplify extend widen quicken efficient pierce homing bounce silk_touch fortune scatter fan echo",
            List.of(new Point(.1, .84), new Point(.1, .16), new Point(.9, .84), new Point(.9, .16)));
        add(SpellPartKind.FLOW, "branch relay delay sieve",
            List.of(new Point(.1, .48), new Point(.5, .1), new Point(.9, .48),
                new Point(.5, .9), new Point(.1, .48)));
    }

    public static final List<AonRecognition.Template> TEMPLATES = COMPONENTS.values().stream()
        .map(FocusComponent::template).toList();

    public static FocusComponent named(String name) {
        return COMPONENTS.get(name.toLowerCase(Locale.ROOT).replace('-', '_'));
    }

    public static FocusComponent recognize(List<List<Point>> strokes) {
        var match = AonRecognition.recognize(strokes, TEMPLATES,
            AonRecognition.Tolerance.defaults(.065, .013));
        return match.template() == null ? null : named(match.template().id());
    }

    private static void add(SpellPartKind kind, String names, List<Point> frame) {
        String[] words = names.split(" ");
        for (int index = 0; index < words.length; index++) {
            String word = words[index];
            ResourceKey<SpellPart> key = ResourceKey.create(SpellPart.REGISTRY_KEY,
                ResourceLocation.fromNamespaceAndPath("thaumaturge", word));
            var template = new AonRecognition.Template(word, List.of(frame, teeth(index)));
            COMPONENTS.put(word, new FocusComponent(word, kind, key, template));
        }
    }

    // Eight slots use extended Hamming parity so patterns in one family differ in at least four teeth.
    private static List<Point> teeth(int index) {
        int[] generators = {0x71, 0xB2, 0xD4, 0xE8};
        int code = 0;
        for (int bit = 0; bit < generators.length; bit++) {
            if ((index & (1 << bit)) != 0) {
                code ^= generators[bit];
            }
        }
        var points = new ArrayList<Point>(17);
        points.add(new Point(.14, .5));
        for (int bit = 0; bit < 8; bit++) {
            double x = .14 + .09 * bit;
            points.add(new Point(x + .045, (code & (1 << bit)) == 0 ? .34 : .66));
            points.add(new Point(x + .09, .5));
        }
        return List.copyOf(points);
    }
}
