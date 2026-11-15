package dev.weaverlib.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.math.Axis;
import dev.weaverlib.RuneCast;
import dev.weaverlib.rune.AonDrawing;
import dev.weaverlib.rune.AonDrawing.Point;
import dev.weaverlib.rune.Weave;
import dev.weaverlib.rune.FocusComponent;
import dev.weaverlib.rune.FocusSpell;
import com.leclowndu93150.thaumaturge.api.spell.Spells;
import com.leclowndu93150.thaumaturge.api.spell.part.SpellPart;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import dev.weaverlib.rune.RuneModifier;
import dev.weaverlib.rune.AonRecognition.Template;
import java.util.Arrays;
import java.util.stream.Stream;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

final class RuneCanvas extends Screen {
    // Thaumaturge's book spread occupies the top 360 rows of its 512px atlas.
    private static final ResourceLocation BOOK =
        ResourceLocation.fromNamespaceAndPath("thaumaturge", "textures/gui/gui_researchbook.png");
    private static final int ATLAS_SIZE = 512;
    private static final int SPREAD_HEIGHT = 360;
    private static final int PAGE_TEXT = 0xFF492C21;
    private static final int GUIDE_INK = 0xFFAA8469;
    private static final int CAST_INK = 0xFFA01FDB;
    private static final int MAX_CAPTURE_POINTS = 512;
    private static final int RETAINED_CAPTURE_POINTS = 384;
    private static final List<Template> GUIDES = Stream.concat(
        Arrays.stream(Weave.values()).map(Weave::template),
        Stream.concat(Arrays.stream(RuneModifier.values()).map(RuneModifier::template),
            FocusComponent.TEMPLATES.stream())).toList();
    private final List<List<List<Point>>> glyphs = new ArrayList<>();
    private final List<List<Point>> strokes = new ArrayList<>();
    private final List<Point> activeStroke = new ArrayList<>();
    private final Map<List<Point>, List<Point>> completedPreviews = new IdentityHashMap<>();
    private List<Point> activePreview = List.of();
    private Component runeStatus;
    private InputConstants.Key canvasKey;
    private int drawButton;
    private int guide = -1;
    private final Map<Integer, Map<String, String>> arguments = new HashMap<>();
    private SpellPart editingPart;
    private int editingGlyph;
    private int editingField;

    RuneCanvas() {
        super(Component.translatable("weaverlib.canvas"));
    }

    void setCanvasKey(InputConstants.Key canvasKey) {
        this.canvasKey = canvasKey;
        drawButton = canvasKey.equals(InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_LEFT))
            ? GLFW.GLFW_MOUSE_BUTTON_RIGHT : GLFW.GLFW_MOUSE_BUTTON_LEFT;
    }

    @Override
    public void tick() {
        if (!minecraft.isWindowActive()) {
            onClose();
        }
    }

    @Override
    public void removed() {
        finishStroke();
        editingPart = null;
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int bookWidth() {
        return Math.max(1, Math.min(640, Math.min(width - 12, (height - 12) * ATLAS_SIZE / SPREAD_HEIGHT)));
    }

    private int bookHeight() {
        return bookWidth() * SPREAD_HEIGHT / ATLAS_SIZE;
    }

    private int bookLeft() {
        return (width - bookWidth()) / 2;
    }

    private int bookTop() {
        return (height - bookHeight()) / 2;
    }

    private int size() {
        return Math.max(1, bookWidth() * 41 / 100);
    }

    private int left() {
        return bookLeft() + bookWidth() * 26 / 100 - size() / 2;
    }

    private int top() {
        return bookTop() + bookHeight() * 55 / 100 - size() / 2;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == drawButton && strokes.size() < AonDrawing.MAX_STROKES
                && x >= left() && x <= left() + size() && y >= top() && y <= top() + size()) {
            editingPart = null;
            arguments.remove(glyphs.size());
            activeStroke.clear();
            activeStroke.add(point(x, y));
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double deltaX, double deltaY) {
        if (button == drawButton && !activeStroke.isEmpty()) {
            appendPoint(x, y);
            return true;
        }
        return super.mouseDragged(x, y, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (canvasKey.equals(InputConstants.Type.MOUSE.getOrCreate(button))) {
            castAndClose();
            return true;
        }
        if (button == drawButton && !activeStroke.isEmpty()) {
            appendPoint(x, y);
            finishStroke();
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    private void finishStroke() {
        if (activeStroke.size() >= 2) {
            strokes.add(List.copyOf(activeStroke));
            updateRuneStatus();
        }
        activeStroke.clear();
        activePreview = List.of();
    }

    private void castAndClose() {
        finishStroke();
        if (minecraft.isWindowActive() && minecraft.player != null
                && minecraft.getConnection() != null && (!strokes.isEmpty() || !glyphs.isEmpty())) {
            var drawing = new ArrayList<>(glyphs);
            if (!strokes.isEmpty()) {
                drawing.add(strokes);
            }
            var settings = new ArrayList<String>(drawing.size());
            for (int i = 0; i < drawing.size(); i++) {
                settings.add(encodeArguments(arguments.getOrDefault(i, Map.of())));
            }
            var encodedInk = drawing.stream().map(RuneCanvas::recognitionInk).toList();
            PacketDistributor.sendToServer(new RuneCast(encodedInk, settings));
        }
        onClose();
    }

    @Override
    public boolean keyReleased(int key, int scanCode, int modifiers) {
        if (canvasKey.equals(InputConstants.getKey(key, scanCode))) {
            castAndClose();
            return true;
        }
        return super.keyReleased(key, scanCode, modifiers);
    }

    private void appendPoint(double x, double y) {
        Point current = point(x, y);
        Point last = activeStroke.getLast();
        if (Math.hypot(current.x() - last.x(), current.y() - last.y()) < .5 / size()) {
            return;
        }
        if (activeStroke.size() == MAX_CAPTURE_POINTS) {
            List<Point> retained = StrokeInk.reduce(activeStroke, RETAINED_CAPTURE_POINTS);
            activeStroke.clear();
            activeStroke.addAll(retained);
        }
        activeStroke.add(current);
        activePreview = null;
    }

    private Point point(double x, double y) {
        return new Point(Math.clamp((x - left()) / size(), 0.0, 1.0),
            Math.clamp((y - top()) / size(), 0.0, 1.0));
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            glyphs.clear();
            strokes.clear();
            activeStroke.clear();
            completedPreviews.clear();
            arguments.clear();
            runeStatus = null;
            onClose();
            return true;
        }
        if (canvasKey.equals(InputConstants.getKey(key, scanCode))) {
            return true;
        }
        if (key == GLFW.GLFW_KEY_R && activeStroke.isEmpty()) {
            if (editingPart == null) {
                openArguments();
            } else {
                editingPart = null;
            }
            return true;
        }
        if (editingPart != null) {
            if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
                editingField = Math.floorMod(editingField + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1), argumentCount());
                return true;
            }
            if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
                changeArgument(key == GLFW.GLFW_KEY_RIGHT ? 1 : -1);
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_Z && hasControlDown() && activeStroke.isEmpty()) {
            if (strokes.isEmpty() && !glyphs.isEmpty()) {
                strokes.addAll(glyphs.removeLast());
            }
            if (!strokes.isEmpty()) {
                completedPreviews.remove(strokes.removeLast());
                arguments.remove(glyphs.size());
                editingPart = null;
                updateRuneStatus();
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB && activeStroke.isEmpty()) {
            if (!strokes.isEmpty() && glyphs.size() < AonDrawing.MAX_GLYPHS - 1) {
                glyphs.add(List.copyOf(strokes));
                strokes.clear();
                editingPart = null;
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_G) {
            int delta = hasShiftDown() ? -1 : 1;
            guide = Math.floorMod(guide + 1 + delta, GUIDES.size() + 1) - 1;
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private void updateRuneStatus() {
        if (strokes.isEmpty() && glyphs.isEmpty()) {
            runeStatus = null;
            return;
        }
        var latest = strokes.isEmpty() ? glyphs.getLast() : strokes;
        var ink = recognitionInk(latest);
        int number = glyphs.size() + (strokes.isEmpty() ? 0 : 1);
        FocusComponent component = FocusComponent.recognize(ink);
        String name = component == null ? null : component.word();
        if (name == null) {
            if (number == 1) {
                var effect = Weave.recognize(ink);
                if (effect.template() != null) {
                    name = effect.template().id();
                }
            } else {
                RuneModifier modifier = RuneModifier.recognize(ink);
                if (modifier != null) {
                    name = modifier.template().id();
                }
            }
        }
        runeStatus = name == null
            ? Component.translatable("weaverlib.rune_unrecognized", number)
            : Component.translatable("weaverlib.rune_recognized", number, name);
    }

    private void openArguments() {
        if (minecraft.level == null || (strokes.isEmpty() && glyphs.isEmpty())) {
            return;
        }
        editingGlyph = strokes.isEmpty() ? glyphs.size() - 1 : glyphs.size();
        var ink = strokes.isEmpty() ? glyphs.getLast() : strokes;
        FocusComponent component = FocusComponent.recognize(recognitionInk(ink));
        if (component != null) {
            editingPart = Spells.part(minecraft.level.registryAccess(), component.key()).orElse(null);
            editingField = 0;
            if (editingPart != null && argumentCount() == 0) {
                editingPart = null;
            }
        }
    }

    private static List<List<Point>> recognitionInk(List<List<Point>> glyph) {
        return glyph.stream().map(stroke -> StrokeInk.reduce(stroke, AonDrawing.MAX_POINTS)).toList();
    }

    private int argumentCount() {
        return editingPart.settings().size() + (editingPart.aspect().selectable() ? 1 : 0);
    }

    private void changeArgument(int delta) {
        var values = arguments.computeIfAbsent(editingGlyph, ignored -> new LinkedHashMap<>());
        if (editingField < editingPart.settings().size()) {
            var spec = editingPart.settings().get(editingField);
            int value = Integer.parseInt(values.getOrDefault(spec.key(), Integer.toString(spec.defaultValue())));
            values.put(spec.key(), Integer.toString(spec.offset(value, delta)));
        } else {
            var ids = editingPart.aspect().options(minecraft.level.registryAccess()).stream()
                .map(key -> key.location().toString()).toList();
            int current = ids.indexOf(values.get("aspect")) + 1;
            int next = Math.floorMod(current + delta, ids.size() + 1);
            if (next == 0) {
                values.remove("aspect");
            } else {
                values.put("aspect", ids.get(next - 1));
            }
        }
        if (encodeArguments(values).length() > FocusSpell.MAX_ARGUMENT_BYTES) {
            arguments.remove(editingGlyph);
            editingPart = null;
        }
    }

    private static String encodeArguments(Map<String, String> values) {
        return values.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(java.util.stream.Collectors.joining(","));
    }

    private void drawArguments(GuiGraphics graphics, int x, int y, int width) {
        var values = arguments.getOrDefault(editingGlyph, Map.of());
        Component name;
        Component value;
        if (editingField < editingPart.settings().size()) {
            var spec = editingPart.settings().get(editingField);
            name = Component.translatable(spec.nameKey());
            value = spec.label(Integer.parseInt(values.getOrDefault(spec.key(), Integer.toString(spec.defaultValue()))));
        } else {
            name = Component.translatable("weaverlib.aspect");
            value = Component.literal(values.getOrDefault("aspect", "default"));
        }
        drawPageText(graphics, name, x, y, width);
        drawPageText(graphics, value, x, y + 15, width);
        drawPageText(graphics, Component.translatable("weaverlib.argument_cycle"), x, y + 32, width);
        drawPageText(graphics, Component.translatable("weaverlib.argument_change"), x, y + 42, width);
        drawPageText(graphics, Component.translatable("weaverlib.argument_done"), x, y + 52, width);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = left();
        int y = top();
        int size = size();
        graphics.fill(0, 0, width, height, 0xA018111D);
        graphics.blit(BOOK, bookLeft(), bookTop(), bookWidth(), bookHeight(), 0, 0,
            ATLAS_SIZE, SPREAD_HEIGHT, ATLAS_SIZE, ATLAS_SIZE);

        for (int index = 0; index <= 10; index++) {
            int offset = index * size / 10;
            int rule = index == 0 || index == 10 ? 0xA06F4934 : 0x446F4934;
            graphics.hLine(x, x + size, y + offset, rule);
            graphics.vLine(x + offset, y, y + size, rule);
        }

        boolean traceEffect = guide >= 0 && guide < Weave.values().length && glyphs.isEmpty();
        if (traceEffect) {
            int side = size / 2;
            int inset = (size - side) / 2;
            drawGuide(graphics, GUIDES.get(guide), x + inset, y + inset, side);
        }

        for (var glyph : glyphs) {
            for (var stroke : glyph) {
                drawStroke(graphics, completedPreviews.computeIfAbsent(stroke, StrokeInk::preview), CAST_INK, x, y, size);
            }
        }
        for (List<Point> stroke : strokes) {
            drawStroke(graphics, completedPreviews.computeIfAbsent(stroke, StrokeInk::preview), CAST_INK, x, y, size);
        }
        if (activePreview == null) {
            activePreview = StrokeInk.preview(activeStroke);
        }
        drawStroke(graphics, activePreview, CAST_INK, x, y, size);
        drawPageText(graphics, runeStatus == null ? title : runeStatus, x + size / 2, y - 13, size);
        drawPageText(graphics, Component.translatable("weaverlib.canvas_cancel"), x + size / 2, y + size + 6, size);

        int guideCenter = bookLeft() + bookWidth() * 74 / 100;
        int textY = y + Math.max(0, (size - 92) / 2);
        if (editingPart != null) {
            drawArguments(graphics, guideCenter, textY, size);
            return;
        }
        Component guideTitle = guide < 0
            ? Component.translatable("weaverlib.guide_off")
            : Component.translatable("weaverlib.guide_active", GUIDES.get(guide).id());
        drawPageText(graphics, guideTitle, guideCenter, y - 13, size);
        if (guide >= 0 && !traceEffect) {
            int preview = Math.max(24, size * 2 / 5);
            drawGuide(graphics, GUIDES.get(guide), guideCenter - preview / 2, y - 1, preview);
            textY = y + preview + 2;
        }
        drawPageText(graphics, Component.translatable("weaverlib.canvas_slots"), guideCenter, textY, size);
        drawPageText(graphics, Component.translatable("weaverlib.canvas_next"), guideCenter, textY + 10, size);
        drawPageText(graphics, Component.translatable("weaverlib.canvas_guide"), guideCenter, textY + 20, size);
        drawPageText(graphics, Component.translatable("weaverlib.canvas_undo"), guideCenter, textY + 30, size);
        drawPageText(graphics, Component.translatable("weaverlib.canvas_cast"), guideCenter, textY + 40, size);
        drawPageText(graphics, Component.translatable("weaverlib.canvas_arguments"), guideCenter, textY + 50, size);
    }

    private void drawGuide(GuiGraphics graphics, Template template, int x, int y, int side) {
        for (int index = 0; index < template.strokes().size(); index++) {
            var stroke = template.strokes().get(index);
            drawStroke(graphics, stroke, GUIDE_INK, x, y, side);
            Point start = stroke.getFirst();
            graphics.drawString(font, Integer.toString(index + 1),
                x + (int) (start.x() * side) - 6, y + (int) (start.y() * side) - 7,
                PAGE_TEXT, false);
        }
    }

    private void drawPageText(GuiGraphics graphics, Component text, int center, int y, int maxWidth) {
        var clipped = font.substrByWidth(text, maxWidth);
        var line = net.minecraft.locale.Language.getInstance().getVisualOrder(clipped);
        graphics.drawString(font, line, center - font.width(line) / 2, y, PAGE_TEXT, false);
    }

    private void drawStroke(GuiGraphics graphics, List<Point> stroke, int color, int canvasX, int canvasY, int side) {
        var pose = graphics.pose();
        for (int index = 1; index < stroke.size(); index++) {
            Point from = stroke.get(index - 1);
            Point to = stroke.get(index);
            double dx = side * (to.x() - from.x());
            double dy = side * (to.y() - from.y());
            pose.pushPose();
            pose.translate(canvasX + side * from.x(), canvasY + side * from.y(), 0);
            pose.mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
            graphics.fill(-1, -1, (int) Math.ceil(Math.hypot(dx, dy)) + 1, 1, color);
            pose.popPose();
        }
    }
}
