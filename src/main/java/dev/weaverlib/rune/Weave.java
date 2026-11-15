package dev.weaverlib.rune;

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
        List.of(new Point(.2, .5), new Point(.5, .2), new Point(.8, .5),
                new Point(.5, .8), new Point(.2, .5)),
        List.of(new Point(.5, .35), new Point(.5, .65))
    ));

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
