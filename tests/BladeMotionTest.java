import local.dualwield.BladeMotion;
public class BladeMotionTest {
    public static void main(String[] args) {
        int samples=0;
        for (int kind=0;kind<=5;kind++) for (int beat=0;beat<14;beat++) {
            float start=BladeMotion.strikeStart(kind), end=BladeMotion.strikeEnd(kind,beat);
            var plane=BladeMotion.sample(kind,beat,(start+end)/2f);
            float[] n=cross(d(plane),e(plane));
            for(int i=0;i<=200;i++) {
                float t=i/100f;
                var p=BladeMotion.sample(kind,beat,t);
                check(Float.isFinite(p.gx()+p.gy()+p.gz()+p.dx()+p.dy()+p.dz()+p.ex()+p.ey()+p.ez()),"finite pose");
                check(Math.abs(dot(d(p),d(p))-1)<0.0001,"unit blade axis");
                check(Math.abs(dot(e(p),e(p))-1)<0.0001,"unit cutting edge");
                check(Math.abs(dot(d(p),e(p)))<0.0001,"edge perpendicular to blade length");
                check(Math.abs(p.gx())<1 && Math.abs(p.gy())<1 && p.gz()<-.3,"useful viewing depth");
                if(t>start+.015f && t<end-.015f) {
                    var a=BladeMotion.sample(kind,beat,t-.0005f);
                    var b=BladeMotion.sample(kind,beat,t+.0005f);
                    float[] velocity=sub(point(b,1.4f),point(a,1.4f));
                    if(kind==BladeMotion.THRUST||kind==BladeMotion.FINISH) {
                        check(dot(normalize(velocity),d(p))>.999,"stab travels along the blade point");
                    } else {
                        check(Math.abs(dot(n,d(p)))<.0001,"blade remains in cutting plane");
                        for(float length:new float[]{.4f,1.4f,2.6f}) {
                            velocity=sub(point(b,length),point(a,length));
                            check(dot(normalize(velocity),e(p))>.999,"edge leads the cut, never the flat");
                        }
                    }
                }
                samples++;
            }
            var finish=BladeMotion.sample(kind,beat,.90f);
            var hold=BladeMotion.sample(kind,beat,1f);
            if(kind!=BladeMotion.THRUST && kind!=BladeMotion.FINISH)
                check(distance(point(finish,1.4f),point(hold,1.4f))<.00001,"clear held finish");
            for(float boundary:new float[]{start,(float)BladeMotion.contactTicks(kind)/BladeMotion.durationTicks(kind,beat),end,1f,1f+2f/BladeMotion.durationTicks(kind,beat)}) {
                var a=BladeMotion.sample(kind,beat,boundary-.00001f);
                var b=BladeMotion.sample(kind,beat,boundary+.00001f);
                check(distance(point(a,1.4f),point(b,1.4f))<.002,"continuous phase boundary");
            }
        }
        var ready=BladeMotion.sample(BladeMotion.THRUST,0,0);
        var impact=BladeMotion.sample(BladeMotion.THRUST,0,1f/3f);
        var recovered=BladeMotion.sample(BladeMotion.THRUST,0,1);
        check(ready.gz()-impact.gz()>.45,"visible forward thrust");
        check(distance(point(ready,1),point(recovered,1))<.0001,"same-axis retraction between three stabs");
        check(BladeMotion.durationTicks(BladeMotion.THRUST)==3,"three-tick thrust cadence");
        check(BladeMotion.durationTicks(BladeMotion.BURST)==6,"six-tick burst follow-through");
        check(BladeMotion.durationTicks(BladeMotion.BURST,12)==4,"double cut before SA");
        check(BladeMotion.contactTicks(BladeMotion.CUT)==2 && BladeMotion.contactTicks(BladeMotion.FOLLOW_UP)==2,"unchanged contact ticks");
        var old=BladeMotion.sample(BladeMotion.CUT,0,1);
        float oldWeight=BladeMotion.weight(BladeMotion.CUT,0,7f,0f,false,-1f);
        check(Math.abs(oldWeight-.5f)<.0001f,"half-faded recovery at seven ticks");
        check(Math.abs(BladeMotion.weight(BladeMotion.RETURN,0,0f,oldWeight,false,-1f)-oldWeight)<.0001f,
            "interrupted native recovery must keep the rendered weight, not jump to fully drawn");
        var next=BladeMotion.sample(BladeMotion.RETURN,0,0);
        var prior=old;
        for(int i=1;i<=1000;i++) {
            var p=BladeMotion.blend(old,next,i/1000f);
            check(distance(e(prior),e(p))<.02,"turn the edge smoothly for the return cut; no half-way flip");
            prior=p;
        }
        for(int kind=0;kind<6;kind++) for(int nextKind=0;nextKind<6;nextKind++) {
            var a=BladeMotion.sample(kind,0,1.35f);
            var b=BladeMotion.sample(nextKind,0,0);
            var p=a;
            for(int i=1;i<=1000;i++) {
                var q=BladeMotion.blend(a,b,i/1000f);
                check(distance(e(p),e(q))<.03,"continuous edge roll between attack types "+kind+"->"+nextKind+" at "+i);
                p=q;
            }
        }
        for(int kind=0;kind<6;kind++) {
            float duration=BladeMotion.durationTicks(kind,0);
            check(BladeMotion.weight(kind,0,duration+4,0,false,-1)==0,"ordinary stroke expires after recovery");
            check(BladeMotion.weight(kind,0,30,0,true,-1)==1,"burst keeps its drawn guard between beats");
            check(Math.abs(BladeMotion.weight(kind,0,31,0,false,30)-.5f)<.0001,"released guard fades without a snap");
            check(BladeMotion.weight(kind,0,32,0,false,30)==0,"released burst stops rendering");
        }
        System.out.println("PASS: "+samples+" frames; cutting planes, edge/point alignment, held finishes, continuous joins, cadence");
    }
    static float[] d(BladeMotion.Pose p){return new float[]{p.dx(),p.dy(),p.dz()};}
    static float[] e(BladeMotion.Pose p){return new float[]{p.ex(),p.ey(),p.ez()};}
    static float[] point(BladeMotion.Pose p,float l){return new float[]{p.gx()+l*p.dx(),p.gy()+l*p.dy(),p.gz()+l*p.dz()};}
    static float dot(float[] a,float[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    static float[] cross(float[] a,float[] b){return new float[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}
    static float[] sub(float[] a,float[] b){return new float[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
    static float[] normalize(float[] a){float n=(float)Math.sqrt(dot(a,a));return new float[]{a[0]/n,a[1]/n,a[2]/n};}
    static float distance(float[] a,float[] b){float[] delta=sub(a,b);return (float)Math.sqrt(dot(delta,delta));}
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
