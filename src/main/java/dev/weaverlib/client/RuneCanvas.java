package dev.weaverlib.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.math.Axis;
import dev.weaverlib.RuneCast;
import dev.weaverlib.rune.AonDrawing;
import dev.weaverlib.rune.AonDrawing.Point;
import dev.weaverlib.rune.Weave;
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
    private final List<List<Point>> strokes = new ArrayList<>();
    private final List<Point> activeStroke = new ArrayList<>();
    private final InputConstants.Key canvasKey;
    private final int drawButton;
    private int guide = -1;

    RuneCanvas(InputConstants.Key canvasKey) {
        super(Component.translatable("weaverlib.canvas"));
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
        }
        activeStroke.clear();
    }

    private void castAndClose() {
        finishStroke();
        if (minecraft.isWindowActive() && minecraft.player != null
                && minecraft.getConnection() != null && !strokes.isEmpty()) {
            PacketDistributor.sendToServer(new RuneCast(strokes));
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
        if (Math.hypot(current.x() - last.x(), current.y() - last.y()) < 2.0 / size()) {
            return;
        }
        if (activeStroke.size() == AonDrawing.MAX_POINTS) {
            for (int index = activeStroke.size() - 2; index > 0; index -= 2) {
                activeStroke.remove(index);
            }
        }
        activeStroke.add(current);
    }

    private Point point(double x, double y) {
        return new Point(Math.clamp((x - left()) / size(), 0.0, 1.0),
            Math.clamp((y - top()) / size(), 0.0, 1.0));
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (canvasKey.equals(InputConstants.getKey(key, scanCode))) {
            return true;
        }
        if (key == GLFW.GLFW_KEY_Z && hasControlDown() && activeStroke.isEmpty() && !strokes.isEmpty()) {
            strokes.removeLast();
            return true;
        }
        if (key == GLFW.GLFW_KEY_G) {
            guide = guide == Weave.values().length - 1 ? -1 : guide + 1;
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
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

        if (guide >= 0) {
            var template = Weave.values()[guide].template().strokes();
            for (int index = 0; index < template.size(); index++) {
                List<Point> stroke = template.get(index);
                drawStroke(graphics, stroke, GUIDE_INK);
                Point start = stroke.getFirst();
                graphics.drawString(font, Integer.toString(index + 1),
                    x + (int) (start.x() * size) - 7, y + (int) (start.y() * size) - 9,
                    PAGE_TEXT, false);
            }
        }

        for (List<Point> stroke : strokes) {
            drawStroke(graphics, stroke, CAST_INK);
        }
        drawStroke(graphics, activeStroke, CAST_INK);
        drawPageText(graphics, title, x + size / 2, y - 13, size);

        int guideCenter = bookLeft() + bookWidth() * 74 / 100;
        int textY = y + Math.max(0, (size - 66) / 2);
        Component guideTitle = guide < 0
            ? Component.translatable("weaverlib.guide_off")
            : Component.translatable("weaverlib.guide_active", Weave.values()[guide].word());
        drawPageText(graphics, guideTitle, guideCenter, textY, size);
        graphics.hLine(guideCenter - size / 3, guideCenter + size / 3, textY + 12, 0x806F4934);
        drawPageText(graphics, Component.translatable("weaverlib.canvas_cast"), guideCenter, textY + 18, size);
        drawPageText(graphics, Component.translatable("weaverlib.canvas_guide"), guideCenter, textY + 30, size);
        drawPageText(graphics, Component.translatable("weaverlib.canvas_undo"), guideCenter, textY + 42, size);
        drawPageText(graphics, Component.translatable("weaverlib.canvas_cancel"), guideCenter, textY + 54, size);
    }

    private void drawPageText(GuiGraphics graphics, Component text, int center, int y, int maxWidth) {
        var clipped = font.substrByWidth(text, maxWidth);
        var line = net.minecraft.locale.Language.getInstance().getVisualOrder(clipped);
        graphics.drawString(font, line, center - font.width(line) / 2, y, PAGE_TEXT, false);
    }

    private void drawStroke(GuiGraphics graphics, List<Point> stroke, int color) {
        int side = size();
        int canvasX = left();
        int canvasY = top();
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
