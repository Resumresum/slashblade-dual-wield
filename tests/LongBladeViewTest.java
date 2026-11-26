import local.dualwield.BladeMotion;

public class LongBladeViewTest {
    public static void main(String[] args) {
        // The standard unscaled blade has about 80 model units of hilt behind its grip.
        // This is why a long blade looks much closer than its 0.70-scale short version.
        float closest=-100f;
        var held=BladeMotion.viewPose(BladeMotion.guard(),false,.625f);
        check(Math.abs(held.gx())*2>=1f,"paired long-blade ready grips need more space around the screen centre");
        for(float hilt:new float[]{.625f,1f}) {
            for(int kind:new int[]{BladeMotion.BURST,BladeMotion.FOLLOW_UP}) for(int beat=0;beat<14;beat++) {
                float offset=Float.NaN;
                for(int i=0;i<=200;i++) {
                    var source=BladeMotion.sample(kind,beat,i/100f);
                    var view=BladeMotion.viewPose(source,false,hilt);
                    float buttZ=view.gz()-hilt*view.dz();
                    closest=Math.max(closest,buttZ);
                    check(buttZ<=-.55f,"long hilt must stay out of the camera's face zone: z="+buttZ);
                    check(view.gz()+hilt<=-.55f,"entire rear-hilt bound stays out of the face zone, including ornaments");
                    check(view.gy()<=source.gy()-.17f,"lower long-blade grip below the old face-level arc");
                    check(view.gx()<=source.gx()-.09f,"separate the two hands towards screen edges");
                    // Default 900x600 at 70-degree vertical FOV has a horizontal half-view of 1.05.
                    // Wider windups/finishes may approach the edges; keep contact in the old safe area.
                    check(Math.abs(view.gx()/view.gz())<.95f,"expanded grip stays within the visible horizontal view");
                    float contact=(float)BladeMotion.contactTicks(kind)/BladeMotion.durationTicks(kind,beat);
                    if(Math.abs(i/100f-contact)<.08f)
                        check(Math.abs(view.gx()/view.gz())<.85f,"contact grip stays in the central safe view");
                    if(Float.isNaN(offset))offset=view.gz()-source.gz();
                    check(Math.abs(offset-(view.gz()-source.gz()))<.0001f,"constant offset keeps the physical cutting plane intact");
                    check(view.dx()==source.dx() && view.dy()==source.dy() && view.dz()==source.dz()
                        && view.ex()==source.ex() && view.ey()==source.ey() && view.ez()==source.ez(),"do not alter blade edge alignment");
                }
            }
        }
        for(int kind=0;kind<=5;kind++) for(int i=0;i<=200;i++) {
            var p=BladeMotion.sample(kind,0,i/100f);
            var v=BladeMotion.viewPose(p,true,1f);
            check(v.gx()==-p.gx() && v.dx()==-p.dx() && v.ex()==-p.ex(),"short-blade view mirrors its complete X frame");
            check(v.gy()==p.gy() && v.gz()==p.gz() && v.dy()==p.dy() && v.dz()==p.dz() && v.ey()==p.ey() && v.ez()==p.ez(),
                "short-blade side swap preserves its height, depth and edge orientation");
        }
        System.out.println("PASS: long hilt clearance/viewport bounds/constant-plane offset; short view mirrored in X only. Closest hilt z="+closest);
    }
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
