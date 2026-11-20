package dev.weaverlib.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Ordinary opaque feather material: scene light, real surface normals, no emissive or fringe pass. */
final class RetributionWings {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("weaverlib", "textures/entity/angel_feather_white.png");
    static RenderType layer() { return RenderType.entityCutoutNoCull(WHITE); }
    private RetributionWings() {}

    static int color(AngelFeathers.Feather feather) {
        int gray = feather.covert() ? 66 + feather.index() % 7 * 5 : 23 + feather.index() % 6 * 5;
        return gray << 16 | gray << 8 | gray;
    }

    static void geometry(VertexConsumer buffer, PoseStack.Pose pose, AngelWingMotion.Joints joints, int light) {
        for (var feather : AngelFeathers.ALL) {
            if (AngelFeathers.growth(joints, feather) <= .001) continue;
            int color = color(feather);
            for (int face : new int[]{-1, 1}) {
                for (int band = 0; band < 7; band++) {
                    double a = band / 7.0, b = (band + 1) / 7.0;
                    double wa = AngelFeathers.width(joints, feather, a), wb = AngelFeathers.width(joints, feather, b);
                    for (int side : new int[]{-1, 1}) {
                        // Raised shaft and lowered edges give each vane a shallow three-dimensional profile.
                        Vec3 p = point(joints, feather, a, 0, face, .015);
                        Vec3 q = point(joints, feather, a, side * wa, face, .004);
                        Vec3 r = point(joints, feather, b, side * wb, face, .004);
                        Vec3 s = point(joints, feather, b, 0, face, .015);
                        quad(buffer, pose, side < 0 ? color : AngelMesh.mix(color, 0, .10F), light, p, q, r, s, face * side);
                    }
                    quad(buffer, pose, AngelMesh.mix(color, 0x909090, .18F), light,
                        point(joints, feather, a, -.0025, face, .017), point(joints, feather, a, .0025, face, .017),
                        point(joints, feather, b, .002, face, .017), point(joints, feather, b, -.002, face, .017), face);
                }
                // Fine, non-luminous barbs follow the same bending surface on both faces.
                for (int barb = 0; barb < (feather.covert() ? 4 : 8); barb++) {
                    double start = .16 + barb * (feather.covert() ? .16 : .085);
                    for (int side : new int[]{-1, 1}) {
                        double end = start + .085;
                        double width = side * AngelFeathers.width(joints, feather, end) * .91;
                        quad(buffer, pose, AngelMesh.mix(color, 0, .17F), light,
                            point(joints, feather, start, 0, face, .019), point(joints, feather, start + .006, 0, face, .019),
                            point(joints, feather, end + .006, width, face, .009), point(joints, feather, end, width, face, .009), face * side);
                    }
                }
            }
        }
    }

    private static Vec3 point(AngelWingMotion.Joints joints, AngelFeathers.Feather feather,
            double along, double offset, int face, double height) {
        var p = AngelFeathers.point(joints, feather, along, offset);
        var n = AngelFeathers.normal(joints, feather, along);
        return new Vec3(p.x() + n.x() * height * face, p.y() + n.y() * height * face, p.z() + n.z() * height * face);
    }

    private static void quad(VertexConsumer buffer, PoseStack.Pose pose, int color, int light,
            Vec3 p, Vec3 q, Vec3 r, Vec3 s, int orientation) {
        Vec3 normal = q.subtract(p).cross(s.subtract(p));
        if (normal.lengthSqr() < 1e-12) normal = r.subtract(q).cross(p.subtract(q));
        normal = normal.normalize().scale(orientation);
        for (var v : new Vec3[]{p, q, r, s}) buffer.addVertex(pose, (float) v.x, (float) v.y, (float) v.z)
            .setColor(0xFF000000 | color).setUv(.5F, .5F).setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }
}
