package dev.weaverlib.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/** Emissive, time-advected flame material. All Pyra effects are actual surfaces, not particles. */
final class PyraFire {
    static final int WING = 0, AURA = 1, TRAIL = 2, CORE = 3, JET = 4, GILDED_WING = 5;
    private static ShaderInstance shader;
    private PyraFire() {}

    static void shaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath("weaverlib", "pyra_fire"),
                DefaultVertexFormat.POSITION_TEX_COLOR), loaded -> shader = loaded);
        } catch (IOException exception) {
            throw new java.io.UncheckedIOException("Unable to load the Pyra flame shader", exception);
        }
    }

    static RenderType layer() { return Layer.TYPE; }

    private static final class Layer extends RenderType {
        private static final RenderType TYPE = create("weaverlib_pyra_fire", DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS, 16384, false, true, CompositeState.builder()
                .setShaderState(new ShaderStateShard(() -> shader)).setCullState(NO_CULL)
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setWriteMaskState(COLOR_WRITE)
                .createCompositeState(false));
        private Layer() {
            super("weaverlib_pyra_fire", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS,
                16384, false, true, () -> {}, () -> {});
        }
    }

    static void quad(VertexConsumer buffer, PoseStack.Pose pose, int role,
            Vec3 a, Vec3 b, Vec3 c, Vec3 d, double u0, double v0, double u1, double v1,
            double alpha0, double alpha1) {
        vertex(buffer, pose, role, a, u0, v0, alpha0);
        vertex(buffer, pose, role, b, u1, v0, alpha0);
        vertex(buffer, pose, role, c, u1, v1, alpha1);
        vertex(buffer, pose, role, d, u0, v1, alpha1);
    }

    static void plumeQuad(VertexConsumer buffer, PoseStack.Pose pose, int role, double phase,
            Vec3 a, Vec3 b, Vec3 c, Vec3 d, double v0, double v1, double alpha0, double alpha1) {
        vertex(buffer,pose,role,a,0,v0,alpha0,phase);
        vertex(buffer,pose,role,b,1,v0,alpha0,phase);
        vertex(buffer,pose,role,c,1,v1,alpha1,phase);
        vertex(buffer,pose,role,d,0,v1,alpha1,phase);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, int role, Vec3 p,
            double u, double v, double alpha) {
        vertex(buffer,pose,role,p,u,v,alpha,-1);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, int role, Vec3 p,
            double u, double v, double alpha, double phase) {
        int green=role==JET?0:phase<0?255:128+(int)(Math.clamp(phase,0,1)*126);
        buffer.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
            .setUv((float) u, (float) v).setColor(role == CORE ? 32 : role == JET || role == GILDED_WING ? 0 : role * 127, green, role == GILDED_WING ? 127 : 255,
                (int) Math.round(Math.clamp(alpha, 0, 1) * 255));
    }
}
