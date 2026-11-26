import local.dualwield.BladeMotion;
import local.dualwield.client.BladeStrokeRender;
import org.joml.Vector3f;
public class BladeFrameTest {
 public static void main(String[] args) {
  int count=0;
  var source=new Vector3f(-.998f,-.06f,0).normalize();
  var sourceEdge=new Vector3f(0,1,0).fma(-source.y,source).normalize();
  for(int kind=0;kind<6;kind++)for(int beat=0;beat<14;beat++)for(int i=0;i<=200;i++)for(int side:new int[]{-1,1})for(int up:new int[]{-1,1}) {
   var raw=BladeMotion.sample(kind,beat,i/100f);
   var p=up==1 ? BladeMotion.viewPose(raw,kind<=BladeMotion.THRUST,.625f) : raw;
   var d=new Vector3f(p.dx()*side,p.dy()*up,p.dz());
   var e=new Vector3f(p.ex()*side,p.ey()*up,p.ez());
   var q=BladeStrokeRender.align(source,sourceEdge,d,e);
   check(new Vector3f(source).rotate(q).distance(d)<.0001f,"actual negative-X point mapping");
   check(new Vector3f(sourceEdge).rotate(q).distance(e)<.0001f,"actual sharpened edge mapping");
   check(Math.abs(q.lengthSquared()-1)<.0001f,"proper rotation after reflection");
   count++;
  }
  System.out.println("PASS: "+count+" model orientations; main/off, left/right arm, first/third person, negative-X tip and +Y edge");
 }
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
}
