package dev.weaverlib.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.weaverlib.angel.AngelBurst;
import dev.weaverlib.angel.CelestialEntity;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

/** Layered rune gates, seal geometry, riven sky, consecrated ground and a real solid sword. No particles. */
final class CelestialVisuals {
    private CelestialVisuals(){}
    static void anchorLight(VertexConsumer out,PoseStack stack,CelestialEntity e,double time,float partial){
        double fade=AngelWingMotion.smooth(Math.min(e.age(partial)/7,(e.life()-e.age(partial))/8));
        var pose=stack.last();
        if(e.role()==CelestialEntity.GATE){
            stack.pushPose();stack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-e.getYRot()));pose=stack.last();
            // A dark, translucent aperture behind torn golden edges.
            for(int i=0;i<72;i++){
                double a=i*Math.TAU/72,b=(i+1)*Math.TAU/72;
                Vec3 p=new Vec3(Math.cos(a)*.86,1.35+Math.sin(a)*1.35,0);
                Vec3 q=new Vec3(Math.cos(b)*.86,1.35+Math.sin(b)*1.35,0);
                AngelMesh.quad(out,pose,0x0A0711,(float)(.75*fade),(float)(.45*fade),(float)(.45*fade),(float)(.75*fade),new Vec3(0,1.35,0),p,q,new Vec3(0,1.35,0));
                line(out,pose,p,q,.035,0xFFE8AC,fade*.9);
                if(i%6==0)line(out,pose,p.scale(.91).add(0,.12,0),p.scale(1.07).add(0,-.09,0),.02,0xFFF5D1,fade*.8);
            }
            stack.popPose();
        }else if(e.role()==CelestialEntity.SEAL){
            double radius=e.target()==null?Math.max(1,e.size()):e.target().getBbWidth()*.65+.35;
            double h=e.target()==null?Math.min(8,2+e.size()*.5):e.target().getBbHeight()+.3;
            for(int i=0;i<3;i++)ring(out,pose,new Vec3(0,.08+i*h*.45,0),radius,.04,0xFFE4A0,fade*.75,time*.02*(i%2==0?1:-1));
            for(int i=0;i<8;i++){
                double a=i*Math.TAU/8;
                Vec3 p=new Vec3(Math.cos(a)*radius,.08,Math.sin(a)*radius);
                line(out,pose,p,p.add(0,h,0),.025,0xFFF3C2,fade*.6);
                rune(out,pose,p.add(0,h*.45,0),.22+.04*Math.log1p(e.size()),a,fade);
            }
        }else if(e.role()==CelestialEntity.SKY){
            Vec3 top=new Vec3(0,18,0);
            for(int shell=0;shell<3;shell++){
                double radius=1.2+shell*.33;
                ring(out,pose,top,radius,.055, shell==0?0xFFFFFF:0xFFCF76,fade*(.9-shell*.2),time*.045*(shell%2==0?1:-1));
            }
            // A jagged dark lens, with an irregular wound-like rim instead of a flat explosion disc.
            for(int i=0;i<80;i++){
                double a=i*Math.TAU/80,b=(i+1)*Math.TAU/80;
                double ra=1.12+.09*Math.sin(a*13+time*.08),rb=1.12+.09*Math.sin(b*13+time*.08);
                Vec3 p=top.add(Math.cos(a)*ra,0,Math.sin(a)*ra*.55),q=top.add(Math.cos(b)*rb,0,Math.sin(b)*rb*.55);
                AngelMesh.quad(out,pose,0xFFF5C2,(float)(fade*.1),(float)fade,(float)fade,(float)(fade*.1),top,p,q,top);
                line(out,pose,p,q,.065,0xFFF4C4,fade);
            }
            for(int i=0;i<12;i++){
                double a=i*Math.TAU/12+time*.006;
                Vec3 p=top.add(Math.cos(a)*1.7,0,Math.sin(a)*1.7);
                line(out,pose,p,p.add(Math.cos(a)*(.7+.3*Math.sin(time*.1+i)),.13,Math.sin(a)*.7),.025,0xFFF8DA,fade*.7);
            }
            tube(out,pose,top,new Vec3(0,.05,0),.45,0xFFF9DC,fade*.26,time);
            ring(out,pose,new Vec3(0,.04,0),3,.04,0xFFF0BB,fade*.5,time*.01);
        }else if(e.role()==CelestialEntity.GROUNDS){
            double radius=16*AngelWingMotion.smooth(e.age(partial)/10);
            ring(out,pose,new Vec3(0,.035,0),radius,.07,0xFFE4A2,fade*.8,time*.006);
            ring(out,pose,new Vec3(0,.045,0),radius-.4,.025,0xFFF9D7,fade*.45,-time*.005);
            ring(out,pose,new Vec3(0,.05,0),2,.04,0xFFF1C2,fade*.75,time*.01);
            for(int i=0;i<16;i++){
                double angle=i*Math.TAU/16;
                Vec3 p=new Vec3(Math.cos(angle)*radius,.09,Math.sin(angle)*radius);
                rune(out,pose,p,.30,angle,fade*.85);
                line(out,pose,p,p.scale(.88).add(0,.01,0),.025,0xFFE4A0,fade*.35);
            }
        }else if(e.role()==CelestialEntity.CHAINS){
            ring(out,pose,new Vec3(0,-.6,0),1.4,.04,0x9E7C9B,fade*.4,-time*.01);
        }
    }
    static void anchorFire(VertexConsumer out,PoseStack stack,CelestialEntity e,double time,float partial){
        double fade=AngelWingMotion.smooth(Math.min(e.age(partial)/5,(e.life()-e.age(partial))/8));
        var pose=stack.last();
        if(e.role()==CelestialEntity.SKY){
            fireTube(out,pose,new Vec3(0,18,0),new Vec3(0,.07,0),.30,time,fade*.62);
            for(int i=0;i<18;i++){
                double a=i*Math.TAU/18+time*.01;
                tongue(out,pose,new Vec3(Math.cos(a)*1.35,18,Math.sin(a)*.8),.12,.75,time+i*7,fade*.9);
            }
        }else if(e.role()==CelestialEntity.GATE){
            stack.pushPose();stack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-e.getYRot()));pose=stack.last();
            for(int i=0;i<18;i++){
                double a=i*Math.TAU/18;
                tongue(out,pose,new Vec3(Math.cos(a)*.85,1.35+Math.sin(a)*1.35,.015),.07,.3,time+i*4,fade*.7);
            }
            stack.popPose();
        }else if(e.role()==CelestialEntity.GROUNDS){
            for(int i=0;i<24;i++){
                double a=i*Math.TAU/24;
                tongue(out,pose,new Vec3(Math.cos(a)*15.8,.06,Math.sin(a)*15.8),.09,.65,time+i*3,fade*.45);
            }
        }
    }
    static void blast(VertexConsumer out,PoseStack.Pose pose,AngelBurst burst,Vec3 camera,double age,double life,double time){
        double fade=AngelWingMotion.smooth(Math.min(age/2,(life-age)/8));
        Vec3 from=burst.from().subtract(camera),to=burst.to().subtract(camera);
        fireTube(out,pose,from,to,.85,time,fade);
        for(int i=0;i<12;i++){
            double t=(time*.08+i/12.0)%1;
            Vec3 p=from.lerp(to,t);
            PyraBeamVisual.sphere(out,pose,p,.25+.18*Math.sin(t*Math.PI),time+i,PyraFire.CORE,fade*.28);
        }
    }
    private static void fireTube(VertexConsumer out,PoseStack.Pose pose,Vec3 from,Vec3 to,double radius,double time,double alpha){
        Vec3 axis=to.subtract(from),forward=axis.normalize(),right=forward.cross(new Vec3(0,1,0));
        if(right.lengthSqr()<.001)right=new Vec3(1,0,0);right=right.normalize();Vec3 up=forward.cross(right).normalize();
        for(int shell=0;shell<3;shell++)for(int segment=0;segment<24;segment++)for(int side=0;side<12;side++){
            double a=segment/24.0,b=(segment+1)/24.0,u=side/12.0,v=(side+1)/12.0;
            double ra=radius*(.24+shell*.40)*(1+.07*Math.sin(time*.4+a*30)),rb=radius*(.24+shell*.40)*(1+.07*Math.sin(time*.4+b*30));
            Vec3 p=from.add(axis.scale(a)),q=from.add(axis.scale(b));
            PyraFire.quad(out,pose,shell==0?PyraFire.CORE:PyraFire.GILDED_WING,
                radial(p,right,up,u,ra),radial(p,right,up,v,ra),radial(q,right,up,v,rb),radial(q,right,up,u,rb),u,a,v,b,
                alpha*(shell==0?.95:.24),alpha*(shell==0?.95:.24));
        }
    }
    private static Vec3 radial(Vec3 p,Vec3 right,Vec3 up,double angle,double r){return p.add(right.scale(Math.cos(angle*Math.TAU)*r)).add(up.scale(Math.sin(angle*Math.TAU)*r));}
    private static void tongue(VertexConsumer out,PoseStack.Pose pose,Vec3 at,double width,double height,double time,double alpha){
        double sway=Math.sin(time*.14)*width*.8;
        PyraFire.plumeQuad(out,pose,PyraFire.CORE,(time%127)/127,
            at.add(-width,0,0),at.add(width,0,0),at.add(sway+width*.15,height,0),at.add(sway-width*.15,height,0),0,1,alpha,0);
    }
    private static void ring(VertexConsumer out,PoseStack.Pose pose,Vec3 center,double radius,double width,int color,double alpha,double phase){
        if(radius<=0)return;
        for(int i=0;i<96;i++){
            double a=i*Math.TAU/96+phase,b=(i+1)*Math.TAU/96+phase;
            Vec3 p=new Vec3(Math.cos(a),0,Math.sin(a)),q=new Vec3(Math.cos(b),0,Math.sin(b));
            AngelMesh.quad(out,pose,color,(float)alpha,(float)alpha,(float)alpha,(float)alpha,
                center.add(p.scale(radius-width)),center.add(q.scale(radius-width)),center.add(q.scale(radius+width)),center.add(p.scale(radius+width)));
        }
    }
    private static void rune(VertexConsumer out,PoseStack.Pose pose,Vec3 center,double scale,double angle,double alpha){
        Vec3 across=new Vec3(Math.cos(angle),0,Math.sin(angle)).scale(scale),up=new Vec3(0,scale,0);
        Vec3 top=center.add(up),bottom=center.subtract(up),left=center.subtract(across),right=center.add(across);
        line(out,pose,top,left,.018,0xFFF4C6,alpha);line(out,pose,left,bottom,.018,0xFFF4C6,alpha);
        line(out,pose,bottom,right,.018,0xFFF4C6,alpha);line(out,pose,right,top,.018,0xFFF4C6,alpha);
        line(out,pose,top.add(up.scale(.4)),bottom.subtract(up.scale(.4)),.012,0xFFD989,alpha);
    }
    private static void tube(VertexConsumer out,PoseStack.Pose pose,Vec3 from,Vec3 to,double radius,int color,double alpha,double time){
        for(int i=0;i<24;i++){
            double a=i*Math.TAU/24,b=(i+1)*Math.TAU/24;
            Vec3 p=new Vec3(Math.cos(a)*radius,0,Math.sin(a)*radius),q=new Vec3(Math.cos(b)*radius,0,Math.sin(b)*radius);
            AngelMesh.quad(out,pose,color,(float)alpha,(float)alpha,(float)alpha,(float)alpha,from.add(p),from.add(q),to.add(q),to.add(p));
        }
    }
    private static void line(VertexConsumer out,PoseStack.Pose pose,Vec3 a,Vec3 b,double width,int color,double alpha){
        Vec3 side=b.subtract(a).cross(new Vec3(0,1,0));if(side.lengthSqr()<.001)side=new Vec3(1,0,0);side=side.normalize().scale(width);
        AngelMesh.quad(out,pose,color,(float)alpha,(float)alpha,(float)alpha,(float)alpha,a.subtract(side),a.add(side),b.add(side),b.subtract(side));
    }
    static void sword(VertexConsumer out,PoseStack.Pose pose,int light){
        // Downward pointed blade, diamond cross-section, guard and wrapped grip.
        Vec3 tip=new Vec3(0,0,0),front=new Vec3(0,1.35,.045),back=new Vec3(0,1.35,-.045),left=new Vec3(-.14,1.35,0),right=new Vec3(.14,1.35,0);
        solid(out,pose,light,0xE9DCBD,tip,left,front,tip);solid(out,pose,light,0xBCB6AC,tip,front,right,tip);
        solid(out,pose,light,0x8E929A,tip,right,back,tip);solid(out,pose,light,0xD7CAB0,tip,back,left,tip);
        box(out,pose,light,0xD6AC57,-.38,1.32,-.065,.38,1.42,.065);
        box(out,pose,light,0x4B3030,-.045,1.42,-.045,.045,1.78,.045);
        box(out,pose,light,0xD6AC57,-.085,1.77,-.07,.085,1.90,.07);
        for(int i=0;i<4;i++)box(out,pose,light,0xA88C5B,-.049,1.46+i*.07,-.049,.049,1.475+i*.07,.049);
    }
    private static void box(VertexConsumer out,PoseStack.Pose pose,int light,int color,double x,double y,double z,double X,double Y,double Z){
        Vec3 a=new Vec3(x,y,z),b=new Vec3(X,y,z),c=new Vec3(X,Y,z),d=new Vec3(x,Y,z),e=new Vec3(x,y,Z),f=new Vec3(X,y,Z),g=new Vec3(X,Y,Z),h=new Vec3(x,Y,Z);
        solid(out,pose,light,color,a,b,c,d);solid(out,pose,light,color,e,h,g,f);solid(out,pose,light,color,a,e,f,b);
        solid(out,pose,light,color,d,c,g,h);solid(out,pose,light,color,a,d,h,e);solid(out,pose,light,color,b,f,g,c);
    }
    private static void solid(VertexConsumer out,PoseStack.Pose pose,int light,int color,Vec3 a,Vec3 b,Vec3 c,Vec3 d){
        Vec3 normal=b.subtract(a).cross(c.subtract(a)).normalize();
        for(Vec3 p:new Vec3[]{a,b,c,d})out.addVertex(pose,(float)p.x,(float)p.y,(float)p.z).setColor(0xFF000000|color).setUv(.5F,.5F)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,(float)normal.x,(float)normal.y,(float)normal.z);
    }
}
