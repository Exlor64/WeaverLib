package dev.weaverlib.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.weaverlib.angel.CelestialEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Tracked spell meshes remain coherent for observers, late tracking and multiple concurrent casters. */
final class CelestialRenderer extends EntityRenderer<CelestialEntity> {
    CelestialRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=0;}
    @Override public ResourceLocation getTextureLocation(CelestialEntity e){return ChainMesh.TEXTURE;}
    @Override public boolean shouldRender(CelestialEntity e,Frustum f,double x,double y,double z){
        return e.distanceToSqr(x,y,z)<112*112 && f.isVisible(e.getBoundingBox().inflate(e.role()==CelestialEntity.SKY?24:e.role()==CelestialEntity.GROUNDS?17:Math.max(3,e.size())));
    }
    @Override public void render(CelestialEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        double time=e.level().getGameTime()+partial;
        if(e.role()==CelestialEntity.CHAINS){
            var out=buffers.getBuffer(ChainMesh.layer());
            double radius=e.target()==null?Math.min(4,.6+Math.log1p(e.size())):e.target().getBbWidth()*.5+.16;
            double height=e.target()==null?2:e.target().getBbHeight();
            double grow=AngelWingMotion.smooth(e.age(partial)/8);
            for(int i=0;i<4;i++){
                double angle=i*Math.TAU/4;
                Vec3 from=new Vec3(Math.cos(angle)*(radius+1.2),-.7,Math.sin(angle)*(radius+1.2));
                Vec3 to=new Vec3(Math.cos(angle)*radius,height*.55,Math.sin(angle)*radius);
                ChainMesh.tether(pose,out,light,from,from.lerp(to,grow),.04);
            }
            ChainMesh.path(pose,out,light,t->new Vec3(Math.cos(t*Math.TAU*3*grow)*radius,height*(.12+.75*t*grow),Math.sin(t*Math.TAU*3*grow)*radius),
                (int)(radius*Math.TAU*3/.16),.16);
            if(Minecraft.getInstance().crosshairPickEntity==e)renderNameTag(e,Component.translatable("weaverlib.angel.chain_health",String.format(java.util.Locale.ROOT,"%.0f",e.health())),pose,buffers,light,partial);
        }
        if(e.role()==CelestialEntity.SWORD){
            pose.pushPose();
            double height=e.target()==null?1.8:e.target().getBbHeight();
            pose.translate(0,height+.55+Math.sin(time*.065)*.045,0);
            pose.mulPose(com.mojang.math.Axis.YP.rotation((float)(time*.015)));
            CelestialVisuals.sword(buffers.getBuffer(RetributionWings.layer()),pose.last(),light);
            pose.popPose();
        }
        CelestialVisuals.anchorLight(buffers.getBuffer(AngelMesh.layer()),pose,e,time,partial);
        if(e.role()==CelestialEntity.SKY || e.role()==CelestialEntity.GATE || e.role()==CelestialEntity.GROUNDS)
            CelestialVisuals.anchorFire(buffers.getBuffer(PyraFire.layer()),pose,e,time,partial);
    }
}
