package dev.weaverlib.rune;

import com.leclowndu93150.thaumaturge.api.spell.part.SpellPart;
import com.leclowndu93150.thaumaturge.api.spell.part.SpellPartKind;
import dev.weaverlib.rune.AonDrawing.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public record FocusComponent(String word, SpellPartKind kind, ResourceKey<SpellPart> key,
                             AonRecognition.Template template) {
    private static final Map<String, FocusComponent> COMPONENTS = new LinkedHashMap<>();

    // Coordinates use a 100-unit square. Each string is one pen-down stroke.
    // A control:end pair draws a quadratic curve from the previous point.
    static {
        add(SpellPartKind.EFFECT, "fire", "26,81 14,58:39,49 61,37:53,16 91,48:74,76 61,90:46,83");
        add(SpellPartKind.EFFECT, "frost", "22,21 72,72 85,67", "75,23 30,78 15,73");
        add(SpellPartKind.EFFECT, "air", "12,57 42,28:67,39 88,48:75,64 66,71:58,65");
        add(SpellPartKind.EFFECT, "earth", "17,72 37,38 56,57 71,25 86,72 17,72");
        add(SpellPartKind.EFFECT, "flux", "71,19 21,15:25,56 30,89:66,78 86,64:73,50", "37,47 57,59 64,40");
        add(SpellPartKind.EFFECT, "curse", "18,25 51,46 42,82 68,65 80,30", "22,61 55,55");
        add(SpellPartKind.EFFECT, "heal", "50,18 50,79", "22,44 50,55 78,42");
        add(SpellPartKind.EFFECT, "burst", "11,53 37,56 25,26 49,42 66,15 64,47 89,44 72,67 87,81");
        add(SpellPartKind.EFFECT, "imbue", "16,47 20,78:49,79 76,78:84,45", "52,17 52,59");
        add(SpellPartKind.EFFECT, "break", "21,76 21,29 76,29 76,75", "49,16 38,47 61,52 46,83");
        add(SpellPartKind.EFFECT, "exchange", "14,38 42,22 57,39 79,31", "85,64 57,79 42,62 20,72");
        add(SpellPartKind.EFFECT, "rift", "34,15 24,44 42,64 30,85", "66,15 56,44 76,64 65,85");
        add(SpellPartKind.EFFECT, "ward", "17,25 83,25 82,65:50,84 20,65:17,25", "50,39 50,64");
        add(SpellPartKind.EFFECT, "primal", "19,75 29,35 55,64 74,24 84,68", "14,51 47,18 67,79", "36,84 58,83");
        add(SpellPartKind.EFFECT, "hellbat", "13,34 42,62 49,45 57,63 87,28 77,74 59,70 50,83 37,70 22,73 13,34");
        add(SpellPartKind.EFFECT, "blink", "13,65 40,65 39,35 65,35", "73,17 83,31 72,44");
        add(SpellPartKind.EFFECT, "harvest", "26,84 71,47:62,18", "40,65 23,50 20,29", "55,49 75,44 84,30");

        add(SpellPartKind.DELIVERY, "touch", "23,75 45,63 46,36 60,36 60,18", "60,36 78,46");
        add(SpellPartKind.DELIVERY, "bolt", "23,22 68,22 44,51 76,51 42,84");
        add(SpellPartKind.DELIVERY, "beam", "15,36 78,36", "15,59 78,59", "67,24 86,47 67,71");
        add(SpellPartKind.DELIVERY, "projectile", "14,70 38,60 52,49", "56,49 55,20:78,26 95,40:78,57 64,67:56,49");
        add(SpellPartKind.DELIVERY, "lob", "14,77 37,6:70,26 83,34:86,69", "73,56 86,71 93,54");
        add(SpellPartKind.DELIVERY, "spray", "20,76 20,46 78,18", "23,50 84,49", "23,54 72,80");
        add(SpellPartKind.DELIVERY, "chain", "12,63 20,31:40,46 46,56:35,69 22,83:12,63",
            "41,46 58,40 64,25 84,28 86,49 68,58 55,43");
        add(SpellPartKind.DELIVERY, "aura", "16,59 8,25:48,18 83,18:86,58 85,80:62,83",
            "50,38 67,47:52,61 35,59:42,47 46,41:50,38");
        add(SpellPartKind.DELIVERY, "self", "80,37 66,12:34,24 11,36:18,61 25,79:59,76 74,68:71,53 67,44:49,51");
        add(SpellPartKind.DELIVERY, "cloud", "15,61 5,43:26,40 36,12:55,35 78,23:77,48 96,55:78,67 42,80:15,61");
        add(SpellPartKind.DELIVERY, "mine", "15,70 43,70 50,47 58,70 84,70", "32,40 26,26", "64,39 75,25");
        add(SpellPartKind.DELIVERY, "wall", "16,77 16,28 83,28 83,78", "33,45 70,45 62,64 43,64");
        add(SpellPartKind.DELIVERY, "spellbat", "14,67 13,24 35,43 48,29 62,46 87,23 77,65", "32,69 50,82 70,66");
        add(SpellPartKind.DELIVERY, "sprite", "24,65 42,54 52,22 58,51 80,59 59,64 50,80 42,65 24,65");
        add(SpellPartKind.DELIVERY, "plan", "19,76 19,34 62,34 62,76 19,76", "21,32 38,18 81,18 81,59 62,76");

        add(SpellPartKind.MODIFIER, "amplify", "50,81 50,20", "19,47 50,20 80,47");
        add(SpellPartKind.MODIFIER, "extend", "18,65 37,65 37,49 61,49 61,30 84,30");
        add(SpellPartKind.MODIFIER, "widen", "49,24 25,44 10,78", "51,24 73,44 89,78");
        add(SpellPartKind.MODIFIER, "quicken", "16,71 41,34 49,58 78,21", "60,24 79,21 82,40");
        add(SpellPartKind.MODIFIER, "efficient", "17,71 39,20 74,34 58,73 17,71", "38,47 60,47");
        add(SpellPartKind.MODIFIER, "pierce", "14,80 86,20", "30,31 59,68:76,73");
        add(SpellPartKind.MODIFIER, "homing", "19,27 27,71:53,71 86,70:76,46 69,32:52,47", "64,48 51,49 54,36");
        add(SpellPartKind.MODIFIER, "bounce", "15,24 42,72 78,25", "14,83 70,83");
        add(SpellPartKind.MODIFIER, "silk_touch", "12,53 34,14:50,55 70,88:88,50", "29,71 53,52 75,26");
        add(SpellPartKind.MODIFIER, "fortune", "19,67 39,25 60,65 19,67", "63,64 74,45 86,67");
        add(SpellPartKind.MODIFIER, "scatter", "18,69 43,50 52,19", "43,50 81,40", "43,50 67,78");
        add(SpellPartKind.MODIFIER, "fan", "17,63 47,19:82,63", "49,76 49,39");
        add(SpellPartKind.MODIFIER, "echo", "21,35 53,25:51,56 48,70:24,71", "53,19 93,18:87,54 83,78:51,83");

        add(SpellPartKind.FLOW, "branch", "49,83 49,51 21,19", "49,51 80,19");
        add(SpellPartKind.FLOW, "relay", "17,75 17,44 48,44 48,20 79,20", "72,11 85,20 72,31");
        add(SpellPartKind.FLOW, "delay", "23,18 77,18 69,39:50,50 31,60:23,81 77,81", "77,81 69,61:50,50 31,40:23,18");
        add(SpellPartKind.FLOW, "sieve", "15,20 85,20 59,52 59,75 43,85 43,53 15,20", "28,36 73,36");
    }

    public static final List<AonRecognition.Template> TEMPLATES = COMPONENTS.values().stream()
        .map(FocusComponent::template).toList();
    private static final AonRecognition.OrderedMatcher MATCHER =
        new AonRecognition.OrderedMatcher(recognitionTemplates(), AonRecognition.Tolerance.defaults(.065, .013));

    public static FocusComponent named(String name) {
        return COMPONENTS.get(name.toLowerCase(Locale.ROOT).replace('-', '_'));
    }

    public static FocusComponent recognize(List<List<Point>> strokes) {
        var match = MATCHER.recognize(strokes);
        return match.template() == null ? null : named(match.template().id());
    }

    private static List<AonRecognition.Template> recognitionTemplates() {
        var templates = new ArrayList<>(TEMPLATES);
        var canonical = named("amplify").template().strokes();
        addAmplifyVariants(templates, canonical.getFirst(), canonical.getLast());
        addAmplifyVariants(templates, stroke("50,84 50,19"), stroke("25,48 50,19 75,49"));
        addAmplifyVariants(templates, stroke("50,84 50,19"), stroke("32,40 50,19 68,40"));
        return templates;
    }

    private static void addAmplifyVariants(List<AonRecognition.Template> templates,
            List<Point> stem, List<Point> head) {
        Point base = stem.getFirst();
        Point tip = head.get(1);
        Point left = head.getFirst();
        Point right = head.getLast();
        for (var shaft : List.of(stem, stem.reversed())) {
            for (var arrowhead : List.of(head, head.reversed())) {
                templates.add(new AonRecognition.Template("amplify", List.of(shaft, arrowhead)));
                templates.add(new AonRecognition.Template("amplify", List.of(arrowhead, shaft)));
            }
            templates.add(new AonRecognition.Template("amplify",
                List.of(shaft, List.of(tip, left), List.of(tip, right))));
            templates.add(new AonRecognition.Template("amplify",
                List.of(shaft, List.of(left, tip), List.of(tip, right))));
        }
        templates.add(new AonRecognition.Template("amplify", List.of(List.of(base, tip, left, tip, right))));
    }

    private static void add(SpellPartKind kind, String word, String... paths) {
        ResourceKey<SpellPart> key = ResourceKey.create(SpellPart.REGISTRY_KEY,
            ResourceLocation.fromNamespaceAndPath("thaumaturge", word));
        var strokes = Arrays.stream(paths).map(FocusComponent::stroke).toList();
        var template = new AonRecognition.Template(word, strokes);
        COMPONENTS.put(word, new FocusComponent(word, kind, key, template));
    }

    private static List<Point> stroke(String path) {
        var points = new ArrayList<Point>();
        for (String segment : path.split(" ")) {
            String[] pair = segment.split(":");
            if (pair.length == 1) {
                points.add(point(pair[0]));
                continue;
            }
            Point start = points.getLast();
            Point control = point(pair[0]);
            Point end = point(pair[1]);
            for (int step = 1; step <= 8; step++) {
                double t = step / 8.0;
                double rest = 1 - t;
                points.add(new Point(rest * rest * start.x() + 2 * rest * t * control.x() + t * t * end.x(),
                    rest * rest * start.y() + 2 * rest * t * control.y() + t * t * end.y()));
            }
        }
        return List.copyOf(points);
    }

    private static Point point(String pair) {
        String[] coordinates = pair.split(",");
        return new Point(Integer.parseInt(coordinates[0]) / 100.0, Integer.parseInt(coordinates[1]) / 100.0);
    }
}
