import local.dualwield.BladeMotion;

public class DoubleCutTest {
    public static void main(String[] args) {
        // After twelve singles, the double stage emits main beat 12 and offhand beat 13.
        for(int beat:new int[]{12,13}) {
            var start=BladeMotion.sample(BladeMotion.BURST,beat,.12f);
            var finish=BladeMotion.sample(BladeMotion.BURST,beat,.90f);
            check(Math.abs(start.gy()-finish.gy())>.9,"vertical double cut needs a larger grip-driven stroke");
            check(start.dy()>.7 && finish.dy()<-.7,"double stage must cut from overhead to below, not diagonally");
            for(int i=12;i<=100;i++) {
                var p=BladeMotion.sample(BladeMotion.BURST,beat,i/100f);
                check(Math.abs(p.dx())<.0001f,"double cut stays in a forward vertical plane");
                check(Math.abs(p.gx()-start.gx())<.0001f,"double cut must not sweep across the opposite hilt");
                check(p.dz()<-.4f,"double cut keeps its point towards the front");
            }
            check(BladeMotion.durationTicks(BladeMotion.BURST,beat)==4 && BladeMotion.contactTicks(BladeMotion.BURST)==2,
                "vertical double cut retains its existing contact and SA transition timing");
        }
        var held=BladeMotion.viewPose(BladeMotion.sample(BladeMotion.BURST,0,2),false,.625f);
        check(-2*held.gx()>=1.25f,"expand long-blade spacing beyond 0.4.5");
        double closest=100;
        String closestCase="";
        for(int beat=0;beat<12;beat++) for(int i=34;i<=72;i++) for(float length:new float[]{1.4f,2f,2.6f}) for(int handed:new int[]{-1,1}) {
            var active=BladeMotion.viewPose(BladeMotion.sample(BladeMotion.BURST,beat,i/100f),false,.625f);
            var idle=beat==0 ? held : BladeMotion.viewPose(BladeMotion.sample(BladeMotion.BURST,beat-1,4f/6f+i/100f),false,.625f);
            boolean off=beat%2!=0;
            int side=(off?-1:1)*handed;
            // Points are projected just as in perspective rendering, including offhand height/depth offsets.
            double[] a=project(active,.34f*length,side,off), b=project(active,length,side,off);
            double[] c=project(idle,0,-side,!off), d=project(idle,-.625f,-side,!off);
            double distance=segmentDistance(a,b,c,d);
            if(distance<closest){closest=distance;closestCase="beat="+beat+", phase="+i+", length="+length;}
        }
        check(closest>.025,"moving edge intersects the opposite waiting hilt in the reference view: "+closest+" "+closestCase);
        for(int beat:new int[]{12,13}) {
            var previous=held;
            var target=BladeMotion.viewPose(BladeMotion.sample(BladeMotion.BURST,beat,0),false,.625f);
            var prior=previous;
            for(int i=1;i<=1000;i++) {
                var p=BladeMotion.blend(previous,target,i/1000f);
                double edgeDelta=Math.sqrt(Math.pow(p.ex()-prior.ex(),2)+Math.pow(p.ey()-prior.ey(),2)+Math.pow(p.ez()-prior.ez(),2));
                check(edgeDelta<.03,"continuous edge turn from wide guard into double cut");
                prior=p;
            }
        }
        for(int i=12;i<=100;i++) {
            var a=BladeMotion.viewPose(BladeMotion.sample(BladeMotion.BURST,12,i/100f),false,.625f);
            var b=BladeMotion.viewPose(BladeMotion.sample(BladeMotion.BURST,13,i/100f),false,.625f);
            check(segmentDistance(project(a,.6f,1,false),project(a,2.6f,1,false),project(b,0,-1,true),project(b,-.625f,-1,true))>.1,
                "vertical double-cut blade stays separate from the opposite hilt");
        }
        System.out.println("PASS: vertical finisher, unchanged timing, wider grips; overlapping blade/hilt projected gap="+closest);
    }
    static double[] project(BladeMotion.Pose p,float length,int side,boolean off) {
        double x=(p.gx()+length*p.dx())*side;
        double y=p.gy()+length*p.dy()-(off?.055:0);
        double z=p.gz()+length*p.dz()-(off?.035:0);
        return new double[]{x/-z,y/-z};
    }
    static double cross(double[] a,double[] b,double[] c){return (b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);}
    static double segmentDistance(double[] a,double[] b,double[] c,double[] d) {
        if(cross(a,b,c)*cross(a,b,d)<=0 && cross(c,d,a)*cross(c,d,b)<=0)return 0;
        return Math.min(Math.min(pointDistance(a,c,d),pointDistance(b,c,d)),Math.min(pointDistance(c,a,b),pointDistance(d,a,b)));
    }
    static double pointDistance(double[] p,double[] a,double[] b){
        double dx=b[0]-a[0],dy=b[1]-a[1];
        double t=Math.max(0,Math.min(1,((p[0]-a[0])*dx+(p[1]-a[1])*dy)/(dx*dx+dy*dy)));
        return Math.hypot(p[0]-a[0]-t*dx,p[1]-a[1]-t*dy);
    }
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
