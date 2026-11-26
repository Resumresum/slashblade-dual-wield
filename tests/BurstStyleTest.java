import local.dualwield.BladeMotion;
import local.dualwield.BurstSequence;

public class BurstStyleTest {
    public static void main(String[] args) {
        int vertical=0,up=0,down=0,compactCount=0,reverse=0;
        float minCant=1,maxCant=0;
        for(int beat=0;beat<12;beat++) {
            boolean compact=BurstSequence.compact(beat);
            if(compact)compactCount++;
            float contact=(float)BladeMotion.contactTicks(BladeMotion.BURST)/BladeMotion.durationTicks(BladeMotion.BURST,beat);
            var hit=BladeMotion.sample(BladeMotion.BURST,beat,contact);
            if(hit.ex()<-.1f)reverse++;
            float cant=Math.abs(hit.dx()*hit.ey()-hit.dy()*hit.ex());
            minCant=Math.min(minCant,cant);maxCant=Math.max(maxCant,cant);
            double slope=Math.toDegrees(Math.atan2(Math.abs(hit.ey()),Math.abs(hit.ex())));
            check(slope>(compact?30:50),"large cuts steep, compact countercuts vary their plane: "+beat+" got "+slope);
            if(Math.abs(hit.ex())<.001)vertical++;
            if(hit.ey()>0)up++;else down++;
            float start=BladeMotion.strikeStart(BladeMotion.BURST),end=BladeMotion.strikeEnd(BladeMotion.BURST,beat);
            var first=BladeMotion.sample(BladeMotion.BURST,beat,start);
            var last=BladeMotion.sample(BladeMotion.BURST,beat,end);
            double gripTravel=distance(new double[]{first.gx(),first.gy(),first.gz()},new double[]{last.gx(),last.gy(),last.gz()});
            check(gripTravel>(compact?.98:1.85),"increase the actual grip travel of both large and linking cuts: "+gripTravel);
            if(Math.abs(hit.ex())>.1f)
                check(Math.abs(last.gx()-first.gx())>(compact?.7:.85),"oblique cuts need a broader horizontal path: beat "+beat);
            double arc=0;double[] previous=point(first,2);
            for(int i=1;i<=200;i++) {
                double[] next=point(BladeMotion.sample(BladeMotion.BURST,beat,start+(end-start)*i/200f),2);
                arc+=distance(previous,next);previous=next;
            }
            check(arc>(compact?6.2:8.9),"two-metre reference blade traces the intended arc: "+arc);
        }
        check(vertical>=2 && up>=2 && down>=2 && compactCount==4,"eight large/four compact, vertical/rising/descending strokes");
        check(reverse>=4,"include reverse crossing cuts instead of repeating one inward fan");
        check(maxCant-minCant>.25f,"weave cutting planes in depth as well as screen slope");
        for(int beat=0;beat<12;beat++) for(float age:new float[]{.25f,.6f,1f,1.25f,2f}) {
            var a=BladeMotion.sample(BladeMotion.BURST,beat,age);
            var b=BladeMotion.sample(BladeMotion.BURST,beat+2,0);
            var prior=a;
            for(int i=1;i<=1000;i++) {
                var p=BladeMotion.blend(a,b,i/1000f);
                check(distance(new double[]{prior.ex(),prior.ey(),prior.ez()},new double[]{p.ex(),p.ey(),p.ez()})<.03,
                    "continuous wrist turn for same-hand burst beat "+beat+" at prior age "+age);
                prior=p;
            }
        }
        for(int kind:new int[]{BladeMotion.FINISH,BladeMotion.THRUST}) for(int i=0;i<=100;i++) {
            var p=BladeMotion.sample(kind,0,i/100f);
            check(p.ey()>.98,"stab with cutting edge facing upwards");
            var view=BladeMotion.viewPose(p,true,.5f);
            check(view.gx()>.18f && view.dx()<0,"right-side stab points slightly inwards towards the target");
        }
        var ready=BladeMotion.viewPose(BladeMotion.sample(BladeMotion.CUT,0,0),true,.5f);
        check(ready.gx()>.15f,"short-blade opening originates from the right");
        System.out.println("PASS: eight large/four compact interwoven arcs; right-side edge-up short stabs");
    }
    static double[] point(BladeMotion.Pose p,double length){return new double[]{p.gx()+length*p.dx(),p.gy()+length*p.dy(),p.gz()+length*p.dz()};}
    static double distance(double[] a,double[] b){return Math.sqrt(Math.pow(a[0]-b[0],2)+Math.pow(a[1]-b[1],2)+Math.pow(a[2]-b[2],2));}
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
