package dev.weaverlib.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Flame roots and vanes sample the same individual feathers as the physical wing. */
final class PyraWings {
    private PyraWings() {}

    static void geometry(VertexConsumer buffer, PoseStack pose, AngelWingMotion.Joints joints, double time) {
        geometry(buffer, pose, joints, time, 0);
    }

    static void geometry(VertexConsumer buffer, PoseStack pose, AngelWingMotion.Joints joints,
            double time, long pattern) {
        for (var feather : AngelFeathers.ALL) {
            // Small coverts carry only occasional wisps; the feather layers stay readable.
            if (feather.covert() && feather.index() % 3 != 0) continue;
            double born = AngelFeathers.growth(joints, feather);
            if (born <= .001) continue;
            var n = AngelFeathers.normal(joints, feather, .58);
            Vec3 normal = new Vec3(n.x(), n.y(), n.z());
            double alpha = born * (feather.covert() ? .17 : .38);
            int material = PyraFire.WING;

            // A faint wash lies inside each vane's exact taper instead of bridging the gaps between feathers.
            for (int band = 0; band < 5; band++) {
                double a = .06 + band * .17, b = a + .17;
                double wa = AngelFeathers.width(joints, feather, a) * .60;
                double wb = AngelFeathers.width(joints, feather, b) * .60;
                PyraFire.quad(buffer,pose.last(),material,
                    surface(joints,feather,a,-wa,normal,.019),surface(joints,feather,a,wa,normal,.019),
                    surface(joints,feather,b,wb,normal,.019),surface(joints,feather,b,-wb,normal,.019),
                    0,a,1,b,alpha*.30,alpha*.30);
            }
        }
    }

    record Source(int id,Vec3 position,Vec3 normal,double width,double heat,boolean gold) {}

    /** Emission points cover the vanes and both wing faces, rather than only the lower feather ends. */
    static List<Source> sources(AngelWingMotion.Joints joints,long pattern) {
        var result=new ArrayList<Source>(32);
        for(var feather:AngelFeathers.ALL) {
            if(feather.covert()?feather.index()%7!=0:feather.index()%2!=0)continue;
            double born=AngelFeathers.growth(joints,feather);
            if(born<.03)continue;
            double along=.22+(feather.index()%4)*.14;
            var p=AngelFeathers.point(joints,feather,along,0);
            var n=AngelFeathers.normal(joints,feather,along);
            Vec3 center=new Vec3(p.x(),p.y(),p.z()),normal=new Vec3(n.x(),n.y(),n.z());
            for(int face:new int[]{-1,1})result.add(new Source(feather.index()*2+(face>0?1:0),
                center.add(normal.scale(face*.028)),normal.scale(face),feather.covert()?.031:.050,
                born*(feather.covert()?.65:1),false));
        }
        return result;
    }

    private static Vec3 surface(AngelWingMotion.Joints joints,AngelFeathers.Feather feather,
            double along,double across,Vec3 normal,double lift) {
        var p = AngelFeathers.point(joints,feather,along,across);
        return new Vec3(p.x(),p.y(),p.z()).add(normal.scale(lift));
    }

    static Vec3 featherEdge(AngelWingMotion.Joints joints,int index,double along,double across) {
        var feather = AngelFeathers.ALL.get(index);
        var n = AngelFeathers.normal(joints,feather,along);
        return surface(joints,feather,along,AngelFeathers.width(joints,feather,along)*across,
            new Vec3(n.x(),n.y(),n.z()),.020);
    }

    /** Melee slashes follow the physical leading edge, without the old sheet's independent wobble. */
    static Vec3 point(AngelWingMotion.Joints joints,double span,double trail) {
        var p = joints.surface(span,trail);
        return new Vec3(p.x(),p.y(),p.z()-.012);
    }
}
