package dev.weaverlib.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.weaverlib.angel.AngelBurst;
import dev.weaverlib.angel.AngelSpellRules;
import dev.weaverlib.angel.Angels;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Gold light geometry layered with the existing animated fire material; no native particles. */
final class AngelSpellVisual {
    private AngelSpellVisual() {}

    private static Vec3 origin(AngelBurst spell, Vec3 camera, float partial) {
        var level = Minecraft.getInstance().level;
        if (spell.effect() == AngelBurst.ANGEL_HEALING && level != null) {
            var entity = level.getEntity(spell.id());
            if (!(entity instanceof Player player) || !player.isAlive() || !Angels.view(player).active()) return null;
            return player.getPosition(partial).subtract(camera);
        }
        return spell.from().subtract(camera);
    }

    static void light(VertexConsumer out, PoseStack.Pose pose, AngelBurst spell, Vec3 camera,
            double age, double life, double time, float partial) {
        Vec3 center = origin(spell, camera, partial);
        if (center == null) return;
        double fade = AngelWingMotion.smooth(Math.min(age / 3, (life - age) / 5));
        if (spell.effect() == AngelBurst.ANGEL_SMITE) {
            Vec3 bottom = spell.to().subtract(camera);
            ring(out, pose, bottom.add(0, .04, 0), .5 + age * .065, .035, .7 * fade, 0xFFF2C6, time * .04);
            // Interrupted radial rays make the point of judgment readable at ground level.
            for (int i = 0; i < 12; i++) {
                double angle = i * Math.TAU / 12 + time * .015;
                Vec3 p = bottom.add(Math.cos(angle) * .4, .03, Math.sin(angle) * .4);
                Vec3 q = bottom.add(Math.cos(angle) * 1.5, .03, Math.sin(angle) * 1.5);
                Vec3 side = new Vec3(-Math.sin(angle), 0, Math.cos(angle)).scale(.02);
                AngelMesh.quad(out, pose, 0xFFF4BC, (float)(fade*.8), (float)(fade*.8), 0, 0,
                    p.subtract(side), p.add(side), q.add(side), q.subtract(side));
            }
        } else if (spell.effect() == AngelBurst.ANGEL_REPEL) {
            double radius = AngelSpellRules.waveRadius(age);
            for (int tilt = 0; tilt < 3; tilt++) {
                for (int i = 0; i < 96; i++) {
                    double a = i * Math.TAU / 96, b = (i + 1) * Math.TAU / 96;
                    Vec3 p = sphereBand(center.add(0,.6,0), radius, a, tilt);
                    Vec3 q = sphereBand(center.add(0,.6,0), radius, b, tilt);
                    Vec3 side = p.subtract(center.add(0,.6,0)).normalize().scale(.035);
                    AngelMesh.quad(out, pose, 0xFFF1BE, (float)(fade*.65), (float)(fade*.65), 0, 0,
                        p.subtract(side), p.add(side), q.add(side), q.subtract(side));
                }
            }
        } else if (spell.effect() == AngelBurst.ANGEL_HEALING) {
            double pulse = .6 + .4 * Math.cos(age * Math.TAU / 20);
            ring(out, pose, center.add(0, .035, 0), 3, .035, fade * pulse * .35, 0xFFE6A8, time * .012);
            ring(out, pose, center.add(0, .04, 0), .5 + (age % 20) / 20 * 2.5, .024,
                fade * .22, 0xFFF8D2, time * -.018);
        } else if (spell.effect() == AngelBurst.ANGEL_CLEANSE) {
            for (int i=0;i<3;i++) ring(out,pose,center.add(0,.15+age*.05+i*.30,0),
                .35+age*.018,.025,fade*.48,0xFFF3CF,time*.04+i);
        }
    }

    static void fire(VertexConsumer out, PoseStack.Pose pose, AngelBurst spell, Vec3 camera,
            double age, double life, double time, float partial) {
        if(spell.effect()==AngelBurst.HOLY_BLAST){CelestialVisuals.blast(out,pose,spell,camera,age,life,time);return;}
        Vec3 center = origin(spell, camera, partial);
        if (center == null) return;
        double fade = AngelWingMotion.smooth(Math.min(age / 2, (life - age) / 5));
        switch (spell.effect()) {
            case AngelBurst.ANGEL_HEALING -> {
                for (int i=0;i<14;i++) {
                    double angle=i*2.39996, r=.35+2.4*Math.sqrt((i+.5)/14);
                    Vec3 at=center.add(Math.cos(angle)*r,.04,Math.sin(angle)*r);
                    tongue(out,pose,at,.075,.45,time+i*3,fade*.55,PyraFire.CORE);
                }
                for (int i=0;i<3;i++) {
                    double a=i*Math.TAU/3+time*.045;
                    tongue(out,pose,center.add(Math.cos(a)*.5,.1,Math.sin(a)*.5),.12,.95,time+i*7,
                        fade*.5,PyraFire.CORE);
                }
            }
            case AngelBurst.ANGEL_SMITE -> {
                Vec3 end=spell.to().subtract(camera);
                Vec3 axis=end.subtract(center);
                Vec3 up=axis.normalize();
                Vec3 right=up.cross(new Vec3(1,0,0)).normalize();
                if (right.lengthSqr()<.001) right=new Vec3(0,0,1);
                Vec3 across=up.cross(right).normalize();
                for (int shell=0;shell<3;shell++) {
                    double radius=shell==0?.20:shell==1?.50:.80;
                    for (int step=0;step<32;step++) for (int i=0;i<20;i++) {
                        double a=step/32.0,b=(step+1)/32.0,u=i/20.0,v=(i+1)/20.0;
                        Vec3 p=center.add(axis.scale(a)),q=center.add(axis.scale(b));
                        double ra=radius*(1+.08*Math.sin(time*.4+a*25));
                        double rb=radius*(1+.08*Math.sin(time*.4+b*25));
                        PyraFire.quad(out,pose,shell==0?PyraFire.CORE:PyraFire.JET,
                            tube(p,right,across,u,ra),tube(p,right,across,v,ra),
                            tube(q,right,across,v,rb),tube(q,right,across,u,rb),u,a,v,b,
                            fade*(shell==0?.9:.3),fade*(shell==0?.9:.3));
                    }
                }
                for(int i=0;i<10;i++) {
                    double a=i*Math.TAU/10+time*.025;
                    tongue(out,pose,end.add(Math.cos(a)*.65,.02,Math.sin(a)*.65),.16,1.2,
                        time+i*5,fade*.75,PyraFire.WING);
                }
            }
            case AngelBurst.ANGEL_REPEL -> PyraBeamVisual.sphere(out,pose,center.add(0,.6,0),
                AngelSpellRules.waveRadius(age),time,PyraFire.CORE,fade*.13);
            case AngelBurst.ANGEL_CLEANSE -> {
                for(int i=0;i<6;i++) {
                    double a=i*Math.TAU/6+time*.05;
                    tongue(out,pose,center.add(Math.cos(a)*.45,.02,Math.sin(a)*.45),.12,1.5,
                        time+i*4,fade*.55,PyraFire.CORE);
                }
            }
            case AngelBurst.ANGEL_CIRCLE -> {
                double radius=AngelSpellRules.circleRadius(age);
                for (int i=0;i<96;i++) {
                    double a=i*Math.TAU/96,b=(i+1)*Math.TAU/96;
                    double ha=.85+.25*Math.sin(a*7-time*.5),hb=.85+.25*Math.sin(b*7-time*.5);
                    PyraFire.quad(out,pose,PyraFire.WING,
                        center.add(Math.cos(a)*radius,.02,Math.sin(a)*radius),
                        center.add(Math.cos(b)*radius,.02,Math.sin(b)*radius),
                        center.add(Math.cos(b)*(radius+.15),hb,Math.sin(b)*(radius+.15)),
                        center.add(Math.cos(a)*(radius+.15),ha,Math.sin(a)*(radius+.15)),
                        0,0,1,1,fade*.9,fade*.25);
                }
                // Wisps fill the burning disk, matching the server's damaging area rather than an empty hoop.
                for (int i=0;i<48;i++) {
                    double a=i*2.39996,r=radius*Math.sqrt((i+.5)/48);
                    tongue(out,pose,center.add(Math.cos(a)*r,.025,Math.sin(a)*r),.10,.65,
                        time+i*3,fade*.65,PyraFire.WING);
                }
            }
            case AngelBurst.ANGEL_FORGE -> {
                double size=.18+.04*Math.log1p(spell.power());
                PyraBeamVisual.sphere(out,pose,center,size,time,PyraFire.CORE,fade*.85);
                for(int i=0;i<8;i++) {
                    double a=i*Math.TAU/8+time*.1;
                    tongue(out,pose,center.add(Math.cos(a)*size,age*.012,Math.sin(a)*size),.09,.65,
                        time+i*3,fade*.6,PyraFire.WING);
                }
            }
            default -> {}
        }
    }

    private static Vec3 tube(Vec3 center, Vec3 right, Vec3 across, double around, double radius) {
        double angle=around*Math.TAU;
        return center.add(right.scale(Math.cos(angle)*radius)).add(across.scale(Math.sin(angle)*radius));
    }
    private static Vec3 sphereBand(Vec3 center,double radius,double angle,int plane) {
        return plane==0?center.add(Math.cos(angle)*radius,.12*Math.sin(angle*3),Math.sin(angle)*radius)
            :plane==1?center.add(Math.cos(angle)*radius,Math.sin(angle)*radius,0)
            :center.add(0,Math.sin(angle)*radius,Math.cos(angle)*radius);
    }
    private static void ring(VertexConsumer out,PoseStack.Pose pose,Vec3 center,double radius,double width,
            double alpha,int color,double offset) {
        for(int i=0;i<96;i++) {
            if((i/8)%3==2)continue;
            double a=i*Math.TAU/96+offset,b=(i+1)*Math.TAU/96+offset;
            AngelMesh.quad(out,pose,color,(float)alpha,(float)alpha,(float)alpha,(float)alpha,
                center.add(Math.cos(a)*(radius-width),0,Math.sin(a)*(radius-width)),
                center.add(Math.cos(a)*(radius+width),0,Math.sin(a)*(radius+width)),
                center.add(Math.cos(b)*(radius+width),0,Math.sin(b)*(radius+width)),
                center.add(Math.cos(b)*(radius-width),0,Math.sin(b)*(radius-width)));
        }
    }
    private static void tongue(VertexConsumer out,PoseStack.Pose pose,Vec3 at,double width,double height,
            double time,double alpha,int role) {
        for(int face=0;face<2;face++) for(int step=0;step<10;step++) {
            double a=step/10.0,b=(step+1)/10.0;
            Vec3 p=at.add(Math.sin(a*5-time*.17)*a*.14,a*height,Math.cos(a*6-time*.13)*a*.10);
            Vec3 q=at.add(Math.sin(b*5-time*.17)*b*.14,b*height,Math.cos(b*6-time*.13)*b*.10);
            Vec3 side=face==0?new Vec3(width,0,0):new Vec3(0,0,width);
            PyraFire.plumeQuad(out,pose,role,(Math.sin(time*.01)+1)*.5,
                p.subtract(side.scale(1-a)),p.add(side.scale(1-a)),q.add(side.scale(1-b)),q.subtract(side.scale(1-b)),
                a,b,alpha*(1-a),alpha*(1-b));
        }
    }
}
