package dev.weaverlib.rune;

import dev.weaverlib.rune.AonDrawing.Point;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

public enum RuneModifier {
    PULSE(Slot.FORM, List.of(
        List.of(new Point(.15, .63), new Point(.30, .63), new Point(.45, .18),
            new Point(.59, .83), new Point(.72, .44), new Point(.88, .44))
    )),
    GAZE(Slot.TARGET, List.of(
        List.of(new Point(.16, .73), new Point(.16, .25), new Point(.78, .25)),
        List.of(new Point(.63, .12), new Point(.84, .25), new Point(.63, .40)),
        List.of(new Point(.49, .52), new Point(.75, .79))
    )),
    SURGE(Slot.POWER, List.of(
        List.of(new Point(.23, .84), new Point(.42, .47), new Point(.38, .17),
            new Point(.70, .17), new Point(.59, .46), new Point(.78, .47), new Point(.23, .84))
    )),
    NULL(null, List.of(
        List.of(new Point(.1, .25), new Point(.35, .1), new Point(.65, .4), new Point(.9, .25)),
        List.of(new Point(.1, .7), new Point(.35, .9), new Point(.65, .6), new Point(.9, .85)),
        IntStream.rangeClosed(0, 24).mapToObj(i -> {
            double angle = i * Math.TAU / 24 - Math.PI / 2;
            return new Point(.5 + .09 * Math.cos(angle), .5 + .09 * Math.sin(angle));
        }).toList()
    ));

    public enum Slot { FORM, TARGET, POWER }

    private static final List<AonRecognition.Template> TEMPLATES =
        Arrays.stream(values()).map(RuneModifier::template).toList();

    private final Slot slot;
    private final AonRecognition.Template template;

    RuneModifier(Slot slot, List<List<Point>> strokes) {
        this.slot = slot;
        this.template = new AonRecognition.Template(name().toLowerCase(Locale.ROOT), strokes);
    }

    public AonRecognition.Template template() {
        return template;
    }

    public boolean fits(Slot expected) {
        return this == NULL || slot == expected;
    }

    static RuneModifier recognize(List<List<Point>> strokes) {
        var match = AonRecognition.assess(strokes, TEMPLATES,
            AonRecognition.Tolerance.defaults(.13, .025), .075, .028);
        if (match.template() == null) {
            return null;
        }
        return valueOf(match.template().id().toUpperCase(Locale.ROOT));
    }
}
