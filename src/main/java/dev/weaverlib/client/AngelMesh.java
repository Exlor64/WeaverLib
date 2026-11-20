package dev.weaverlib.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class AngelMesh {
    static final int GOLD = 0xFFB73F;
    static final int WHITE = 0xFFF9E1;

    private AngelMesh() {}

    static RenderType layer() {
        return Layer.TYPE;
    }

    /** Physical feathers participate in scene depth; glow and fire retain their color-only passes. */
    static RenderType wingLayer() { return Wings.TYPE; }

    private static final class Wings extends RenderType {
        private static final RenderType TYPE = create("weaverlib_angel_feathers", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 16384, false, true, CompositeState.builder()
                .setShaderState(POSITION_COLOR_SHADER)
                .setCullState(NO_CULL)
                .setDepthTestState(LEQUAL_DEPTH_TEST)
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setWriteMaskState(COLOR_DEPTH_WRITE)
                .setOutputState(MAIN_TARGET)
                .createCompositeState(false));

        private Wings() {
            super("weaverlib_angel_feathers", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
                16384, false, true, () -> {}, () -> {});
        }
    }

    private static final class Layer extends RenderType {
        private static final RenderType TYPE = create("weaverlib_angel_light", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 4096, false, true, CompositeState.builder()
                .setShaderState(POSITION_COLOR_SHADER)
                .setCullState(NO_CULL)
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setWriteMaskState(COLOR_WRITE)
                .createCompositeState(false));

        private Layer() {
            super("weaverlib_angel_light", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
                4096, false, true, () -> {}, () -> {});
        }
    }

    static void quad(VertexConsumer buffer, PoseStack.Pose pose, int rgb, float a, float b, float c, float d,
            Vec3 p, Vec3 q, Vec3 r, Vec3 s) {
        vertex(buffer, pose, rgb, a, p);
        vertex(buffer, pose, rgb, b, q);
        vertex(buffer, pose, rgb, c, r);
        vertex(buffer, pose, rgb, d, s);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, int rgb, float alpha, Vec3 p) {
        int argb = (Math.round(Mth.clamp(alpha, 0, 1) * 255) << 24) | rgb;
        buffer.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(argb);
    }

    static int mix(int a, int b, float progress) {
        int r = Mth.lerpInt(progress, a >> 16 & 255, b >> 16 & 255);
        int g = Mth.lerpInt(progress, a >> 8 & 255, b >> 8 & 255);
        int blue = Mth.lerpInt(progress, a & 255, b & 255);
        return r << 16 | g << 8 | blue;
    }
}
