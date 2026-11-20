import dev.weaverlib.client.AngelFeathers;
import dev.weaverlib.client.AngelWingMotion;
import java.util.HashSet;

/** Feather anchors and crimson feather colors are shared by solid wings and attached flames. */
public final class AngelFeatherRulesTest {
    private static int checks;
    private static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("Feather check " + checks);
    }
    public static void run() {
        check(AngelFeathers.ALL.size()==49);
        var patterns=new HashSet<Long>();
        for (int player=0;player<64;player++) for(int pair=0;pair<3;pair++) for(int side:new int[]{-1,1}) {
            long seed=AngelFeathers.seed(player*713L,player*997L,pair,side);
            patterns.add(seed);
            check(seed==AngelFeathers.seed(player*713L,player*997L,pair,side));
            for(var feather:AngelFeathers.ALL) {
                int rgb=AngelFeathers.pyraColor(feather);
                check((rgb>>16&255)>(rgb>>8&255)*1.7); // every physical Pyra vane is crimson, with no gold fill
            }
        }
        check(patterns.size()>350);
        for(int pose=0;pose<6;pose++) {
            var j=AngelWingMotion.sample(pose*.7,pose>0?1:0,pose>1?1:0,.6,pose==4?1:0,pose==5?.45:-1,0,1);
            j=AngelWingMotion.aerial(j,pose==2?1:0,pose==3?1:0,0,1);
            for(var feather:AngelFeathers.ALL) {
                var root=AngelFeathers.point(j,feather,0,0);
                var expected=j.surface(feather.span(),feather.start());
                check(root.equals(expected));
                check(Math.abs(AngelFeathers.width(j,feather,0))<1e-10);
                check(Math.abs(AngelFeathers.width(j,feather,1))<1e-10);
                for(double along:new double[]{.1,.5,.9,1.2}) {
                    var p=AngelFeathers.point(j,feather,along,0);
                    var n=AngelFeathers.normal(j,feather,along);
                    check(Double.isFinite(p.x()+p.y()+p.z()));
                    check(Math.abs(n.x()*n.x()+n.y()*n.y()+n.z()*n.z()-1)<1e-8);
                }
            }
        }
        System.out.println("Passed " + checks + " feather anchoring and uniform crimson-palette checks.");
    }
}
