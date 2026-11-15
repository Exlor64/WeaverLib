package dev.weaverlib.rune;

import dev.weaverlib.rune.AonDrawing.Point;
import dev.weaverlib.rune.RuneModifier.Slot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record RuneSpell(Weave effect, double potency, boolean pulse, boolean gaze, boolean surge) {
    public static RuneSpell basic(Weave effect, double potency) {
        return new RuneSpell(effect, potency, false, false, false);
    }

    public static RuneSpell compile(List<List<List<Point>>> glyphs) {
        if (glyphs.isEmpty() || glyphs.size() > AonDrawing.MAX_GLYPHS) {
            throw new IllegalArgumentException("argument_layout");
        }
        var match = Weave.recognize(glyphs.getFirst());
        if (match.template() == null) {
            throw new IllegalArgumentException("unknown_rune");
        }
        Weave effect = Weave.fromWords(match.template().id());
        Point center = AonDrawing.center(glyphs.getFirst());
        var modifiers = new ArrayList<>(glyphs.subList(1, glyphs.size()));
        modifiers.sort(Comparator.comparingDouble((List<List<Point>> glyph) ->
                AonDrawing.clockwiseAngle(center, AonDrawing.center(glyph)))
            .thenComparingDouble(glyph -> distance(center, AonDrawing.center(glyph))));
        boolean pulse = false;
        boolean gaze = false;
        boolean surge = false;
        var slots = Slot.values();
        for (int i = 0; i < modifiers.size(); i++) {
            var glyph = modifiers.get(i);
            if (distance(center, AonDrawing.center(glyph)) < .03) {
                throw new IllegalArgumentException("argument_layout");
            }
            RuneModifier modifier = RuneModifier.recognize(glyph);
            if (modifier == null || !modifier.fits(slots[i])) {
                throw new IllegalArgumentException("argument_" + slots[i].name().toLowerCase(java.util.Locale.ROOT));
            }
            pulse |= modifier == RuneModifier.PULSE;
            gaze |= modifier == RuneModifier.GAZE;
            surge |= modifier == RuneModifier.SURGE;
        }
        return new RuneSpell(effect, match.potency(.5), pulse, gaze, surge);
    }

    private static double distance(Point a, Point b) {
        return Math.hypot(a.x() - b.x(), a.y() - b.y());
    }
}
