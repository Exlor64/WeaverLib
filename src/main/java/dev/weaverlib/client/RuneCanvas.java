package dev.weaverlib.client;

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
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

final class RuneCanvas extends Screen {
    private final List<List<Point>> strokes = new ArrayList<>();
    private final List<Point> activeStroke = new ArrayList<>();
    private int guide = -1;

    RuneCanvas() {
        super(Component.translatable("weaverlib.canvas"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int size() {
        return Math.min(270, Math.max(1, Math.min(width - 24, height - 54)));
    }

    private int left() {
        return (width - size()) / 2;
    }

    private int top() {
        return (height - size()) / 2;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && strokes.size() < AonDrawing.MAX_STROKES
                && x >= left() && x <= left() + size() && y >= top() && y <= top() + size()) {
            activeStroke.clear();
            activeStroke.add(point(x, y));
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double deltaX, double deltaY) {
        if (button == 0 && !activeStroke.isEmpty()) {
            appendPoint(x, y);
            return true;
        }
        return super.mouseDragged(x, y, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && !activeStroke.isEmpty()) {
            appendPoint(x, y);
            if (activeStroke.size() >= 2) {
                strokes.add(List.copyOf(activeStroke));
            }
            activeStroke.clear();
            return true;
        }
        return super.mouseReleased(x, y, button);
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
        if (key == GLFW.GLFW_KEY_ENTER && !strokes.isEmpty() && activeStroke.isEmpty()) {
            PacketDistributor.sendToServer(new RuneCast(strokes));
            onClose();
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
        graphics.fill(0, 0, width, height, 0xAA11151B);
        graphics.fill(x, y, x + size, y + size, 0xFF151E2B);
        for (int index = 0; index <= 10; index++) {
            int offset = index * size / 10;
            graphics.hLine(x, x + size, y + offset, 0xFF2E4254);
            graphics.vLine(x + offset, y, y + size, 0xFF2E4254);
        }

        Component heading = title;
        if (guide >= 0) {
            Weave spell = Weave.values()[guide];
            heading = Component.literal(spell.word());
            var template = spell.template().strokes();
            for (int index = 0; index < template.size(); index++) {
                List<Point> stroke = template.get(index);
                drawStroke(graphics, stroke, 0xFF698DA6);
                Point start = stroke.getFirst();
                graphics.drawString(font, Integer.toString(index + 1),
                    x + (int) (start.x() * size) + 4, y + (int) (start.y() * size), 0xFFC5D6E3);
            }
        }

        for (List<Point> stroke : strokes) {
            drawStroke(graphics, stroke, 0xFFE0F6FF);
        }
        drawStroke(graphics, activeStroke, 0xFFE0F6FF);
        graphics.drawCenteredString(font, heading, width / 2, y - 13, 0xFFF2F7FA);
        graphics.drawCenteredString(font, Component.translatable("weaverlib.canvas_controls"),
            width / 2, y + size + 7, 0xFFE3EAF2);
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
