package dev.weaverlib.client;

import dev.weaverlib.angel.CelestialSpells;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Stored retaliation grows a restrained crown of ember ribbons in the player's actual body frame. */
final class RetaliationVisual {
    private RetaliationVisual(){}
    static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        var client=Minecraft.getInstance();if(client.level==null)return;
        var buffers=client.renderBuffers().bufferSource();var out=buffers.getBuffer(PyraFire.layer());
        float partial=e.getPartialTick().getGameTimeDeltaPartialTick(false);
        for(var player:client.level.players()){
            var effect=player.getEffect(CelestialSpells.RETALIATION);
            if(effect==null || !player.isAlive() || player.isInvisible() || player.distanceToSqr(e.getCamera().getPosition())>64*64)continue;
            var frame=AngelFlightPose.sample(player,partial).frame();
            Vec3 center=player.getPosition(partial).subtract(e.getCamera().getPosition()).add(frame.forward().scale(.8));
            double time=player.tickCount+partial,strength=.25+effect.getAmplifier()*.12;
            for(int strand=0;strand<6;strand++)for(int segment=0;segment<8;segment++){
                double a=segment/8.0,b=(segment+1)/8.0,angle=strand*Math.TAU/6+time*.025;
                double ra=.34+.10*a,rb=.34+.10*b;
                Vec3 p=frame.point(center,(a-.3)*.8,ra,angle+a*.9),q=frame.point(center,(b-.3)*.8,rb,angle+b*.9);
                Vec3 side=frame.right().scale(.04*(1-a));
                PyraFire.plumeQuad(out,e.getPoseStack().last(),strand%2==0?PyraFire.CORE:PyraFire.GILDED_WING,strand/6.0,
                    p.subtract(side),p.add(side),q.add(side.scale(1-b)),q.subtract(side.scale(1-b)),a,b,strength*(1-a),strength*(1-b));
            }
        }
        buffers.endBatch(PyraFire.layer());
    }
}
