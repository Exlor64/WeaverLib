package dev.weaverlib.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.weaverlib.angel.ChainBinding;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Solid, normally lit steel chains; no particles, light ribbons or emissive material. */
final class ChainBindingRenderer extends EntityRenderer<ChainBinding> {
    ChainBindingRenderer(EntityRendererProvider.Context context) { super(context); shadowRadius = 0; }
    @Override public ResourceLocation getTextureLocation(ChainBinding chain) { return ChainMesh.TEXTURE; }
    @Override public boolean shouldRender(ChainBinding chain, Frustum frustum, double x, double y, double z) {
        if (chain.distanceToSqr(x, y, z) > 80 * 80) return false;
        Vec3 anchor = chain.ground() ? chain.anchor() : chain.owner() == null ? chain.position() : chain.owner().position();
        return frustum.isVisible(chain.getBoundingBox().minmax(new AABB(anchor, anchor).inflate(1)));
    }
    @Override public void render(ChainBinding chain, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        var target = chain.target();
        if (target == null || target.isInvisible()) return;
        var buffer = buffers.getBuffer(ChainMesh.layer());
        Vec3 origin = chain.getPosition(partial), center = target.getPosition(partial).subtract(origin);
        double height = target.getBbHeight(), radius = target.getBbWidth() * .5 + .13;
        double extending = chain.extension(partial);
        double wrap = AngelWingMotion.smooth((chain.age(partial) - 3) / 9);
        Vec3 attach = center.add(radius, height * .45, 0);
        if (chain.ground()) {
            Vec3 ground = chain.anchor().subtract(origin);
            for (int strand = 0; strand < 3; strand++) {
                double angle = strand * Math.TAU / 3;
                Vec3 start = ground.add(Math.cos(angle) * .55, .01, Math.sin(angle) * .55);
                Vec3 finish = center.add(Math.cos(angle) * radius, height * .55, Math.sin(angle) * radius);
                ChainMesh.tether(pose, buffer, light, start, start.lerp(finish, extending), chain.tight() ? .01 : .10);
            }
        } else if (chain.owner() != null) {
            Vec3 start = ChainMesh.wrist(chain.owner(), partial).subtract(origin);
            ChainMesh.tether(pose, buffer, light, start, start.lerp(attach, extending), .10 * extending);
        }
        if (wrap > .01) {
            double turns = 2.8 * wrap;
            double tightening = chain.tight() ? .018 * Math.sin(chain.age(partial) * .9) : 0;
            ChainMesh.path(pose, buffer, light, t -> {
                double angle = t * Math.TAU * turns;
                return center.add(Math.cos(angle) * (radius - tightening), height * (.16 + .68 * t * wrap),
                    Math.sin(angle) * (radius - tightening));
            }, (int) Math.ceil(turns * Math.TAU * radius / .14), .14);
        }
        var client = Minecraft.getInstance();
        if (client.crosshairPickEntity == chain && chain.latched()) {
            renderNameTag(chain, Component.translatable("weaverlib.angel.chain_health",
                String.format(java.util.Locale.ROOT, "%.0f", chain.health())), pose, buffers, light, partial);
        }
    }
}
