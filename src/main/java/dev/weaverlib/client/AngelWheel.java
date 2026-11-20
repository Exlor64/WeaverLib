package dev.weaverlib.client;

import dev.weaverlib.angel.AngelAction;
import dev.weaverlib.angel.Angels;
import dev.weaverlib.angel.AngelSpellRules;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

final class AngelWheel extends Screen {
    private static final String[] NAMES = {"Sanctus", "Pyra", "Nemesis"};
    private static final String[] MEANINGS = {"Divinity", "Inferno", "Retribution"};
    private final int[] sequence = new int[3];
    private int count;

    AngelWheel() {
        super(Component.translatable("weaverlib.angel.wheel"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int sector(double mouseX, double mouseY) {
        double x = mouseX - width / 2.0;
        double y = mouseY - height / 2.0;
        if (x * x + y * y < 24 * 24 || x * x + y * y > 86 * 86) return -1;
        double angle = Math.atan2(y, x) + Math.PI / 2;
        if (angle < 0) angle += Math.TAU;
        return Math.min(2, (int) (angle / (Math.TAU / 3)));
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        int part = sector(x, y);
        if (button != 0 || part < 0 || minecraft.player == null || part >= Angels.view(minecraft.player).rank()) {
            return super.mouseClicked(x, y, button);
        }
        sequence[count++] = part;
        if (count == 3) {
            PacketDistributor.sendToServer(new AngelAction(AngelAction.INVOKE,
                sequence[0] * 9 + sequence[1] * 3 + sequence[2]));
            onClose();
        }
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        int cx = width / 2;
        int cy = height / 2;
        int hover = sector(mouseX, mouseY);
        int rank = minecraft.player == null ? 0 : Angels.view(minecraft.player).rank();
        var buffer = graphics.bufferSource().getBuffer(RenderType.gui());
        var matrix = graphics.pose().last().pose();
        for (int sector = 0; sector < 3; sector++) {
            int color = sector >= rank ? 0xDE292631 : sector == hover ? 0xF29B672A : 0xED352B29;
            for (int segment = 0; segment < 24; segment++) {
                double a = -Math.PI / 2 + sector * Math.TAU / 3 + (segment + .09) * Math.TAU / 72;
                double b = -Math.PI / 2 + sector * Math.TAU / 3 + (segment + .91) * Math.TAU / 72;
                buffer.addVertex(matrix, cx + (float) Math.cos(a) * 24, cy + (float) Math.sin(a) * 24, 0).setColor(color);
                buffer.addVertex(matrix, cx + (float) Math.cos(a) * 86, cy + (float) Math.sin(a) * 86, 0).setColor(color);
                buffer.addVertex(matrix, cx + (float) Math.cos(b) * 86, cy + (float) Math.sin(b) * 86, 0).setColor(color);
                buffer.addVertex(matrix, cx + (float) Math.cos(b) * 24, cy + (float) Math.sin(b) * 24, 0).setColor(color);
            }
        }
        graphics.flush();
        for (int part = 0; part < 3; part++) {
            double angle = -Math.PI / 2 + (part + .5) * Math.TAU / 3;
            int x = cx + (int) (Math.cos(angle) * 55);
            int y = cy + (int) (Math.sin(angle) * 55);
            int color = part < rank ? 0xFFFFE2A0 : 0xFF77717C;
            rune(graphics, part, x, y - 6, 1, color);
            graphics.drawCenteredString(font, NAMES[part], x, y + 8, color);
            if (part == hover) {
                graphics.drawCenteredString(font, MEANINGS[part], cx, cy + 102, color);
            }
        }
        if (count == 2 && hover >= 0 && hover < rank) {
            int code = sequence[0] * 9 + sequence[1] * 3 + hover;
            int spell = AngelSpellRules.spell(code, rank);
            if (spell >= 0) {
                var preview = Component.translatable(spell == AngelSpellRules.FORGE ? "weaverlib.angel.spell_preview_item" : "weaverlib.angel.spell_preview",
                    Component.translatable(AngelSpellRules.key(spell)), AngelSpellRules.cost(spell));
                graphics.drawCenteredString(font, preview, cx, cy + 118, 0xFFFFE2A0);
            }
        }
        for (int i = 0; i < 3; i++) {
            int x = cx - 22 + i * 22;
            if (i < count) rune(graphics, sequence[i], x, cy, .6F, 0xFFFFE2A0);
            else graphics.fill(x - 2, cy - 2, x + 2, cy + 2, 0x80665C57);
        }
    }

    static void rune(GuiGraphics graphics, int rune, int x, int y, float scale, int color) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1);
        if (rune == 0) {
            line(graphics, -8, 4, 0, -9, color);
            line(graphics, 0, -9, 8, 4, color);
            line(graphics, -8, 4, 8, 4, color);
            line(graphics, 0, -3, 0, 10, color);
            line(graphics, -4, 8, 4, 8, color);
        } else if (rune == 1) {
            line(graphics, -7, 8, -4, -2, color);
            line(graphics, -4, -2, 0, 2, color);
            line(graphics, 0, 2, 2, -10, color);
            line(graphics, 2, -10, 8, 7, color);
            line(graphics, 8, 7, -7, 8, color);
        } else {
            line(graphics, -8, -7, 8, 7, color);
            line(graphics, 8, -7, -8, 7, color);
            line(graphics, 0, -10, 0, 10, color);
            line(graphics, -4, -10, 4, -10, color);
        }
        pose.popPose();
    }

    private static void line(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x1, y1, 0);
        pose.mulPose(com.mojang.math.Axis.ZP.rotation((float) Math.atan2(y2 - y1, x2 - x1)));
        graphics.fill(0, 0, (int) Math.ceil(Math.hypot(x2 - x1, y2 - y1)), 1, color);
        pose.popPose();
    }
}
