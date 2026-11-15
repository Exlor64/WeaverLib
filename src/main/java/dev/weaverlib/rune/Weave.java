package dev.weaverlib.rune;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import dev.weaverlib.rune.AonDrawing.Point;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum Weave {
    LUX(5, List.of(
        List.of(new Point(.25, .2), new Point(.5, .8), new Point(.75, .2)),
        List.of(new Point(.28, .42), new Point(.72, .42))
    )),
    SANA(8, List.of(
        List.of(new Point(.23, .85), new Point(.47, .64), new Point(.43, .41),
                new Point(.59, .23), new Point(.82, .29)),
        List.of(new Point(.45, .50), new Point(.65, .51), new Point(.76, .68)),
        List.of(new Point(.26, .26), new Point(.31, .28), new Point(.34, .34),
                new Point(.31, .40), new Point(.26, .42), new Point(.21, .40),
                new Point(.18, .34), new Point(.21, .28), new Point(.26, .26))
    ));

    public static final Codec<Weave> CODEC = Codec.STRING.comapFlatMap(word -> {
        Weave weave = fromWords(word);
        return weave == null ? DataResult.error(() -> "Unknown weave " + word) : DataResult.success(weave);
    }, Weave::word);

    private static final List<AonRecognition.Template> TEMPLATES =
        Arrays.stream(values()).map(Weave::template).toList();

    private final AonRecognition.Template template;
    private final int cost;

    Weave(int cost, List<List<Point>> strokes) {
        this.cost = cost;
        this.template = new AonRecognition.Template(word(), strokes);
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }

    public int cost() {
        return cost;
    }

    public AonRecognition.Template template() {
        return template;
    }

    public static Weave fromWords(String phrase) {
        String word = phrase.strip().toLowerCase(Locale.ROOT);
        for (Weave spell : values()) {
            if (spell.word().equals(word)) {
                return spell;
            }
        }
        return null;
    }

    public static AonRecognition.Assessment recognize(List<List<Point>> strokes) {
        return AonRecognition.assess(strokes, TEMPLATES,
            AonRecognition.Tolerance.defaults(.16, .025), .10, .035);
    }
}
