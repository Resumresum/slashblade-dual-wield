import local.dualwield.BladeMotion;
import local.dualwield.BurstSequence;

public class FlurrySequenceTest {
    public static void main(String[] args) {
        check(BurstSequence.CUTS==12 && BurstSequence.STAGES==17,"twelve singles, double, four arts");
        for(int i=0;i<12;i++) check(BurstSequence.startTick(i)==4*i,"four-tick alternating cadence");
        for(int i=12;i<17;i++) check(BurstSequence.startTick(i)==52+4*(i-12),"preserved double/art times");
        check(2*BurstSequence.INTERVAL_TICKS==BladeMotion.durationTicks(BladeMotion.BURST)+2,"same hand resumes after follow-through and recovery");
        for(double main:new double[]{0,.1,1,4,25}) for(double off:new double[]{0,.1,1,8,30}) {
            double old=4*(BurstSequence.damage(main,.52f,1)+BurstSequence.damage(off,.36f,1));
            double now=6*(BurstSequence.damage(main,.52f,BurstSequence.CUT_DAMAGE_SCALE)+BurstSequence.damage(off,.36f,BurstSequence.CUT_DAMAGE_SCALE));
            check(Math.abs(old-now)<.00002,"preserve front-section base damage including low-power floor");
        }
        check(BladeMotion.durationTicks(BladeMotion.BURST,10)==6,"tenth alternating cut must retain a full follow-through");
        for(int beat:new int[]{12,13}) {
            var p=BladeMotion.sample(BladeMotion.BURST,beat,.5f);
            check(Math.abs(p.dx())<.0001,"double cut moves to packets 12/13 after twelve singles");
            check(BladeMotion.durationTicks(BladeMotion.BURST,beat)==4,"double finishes before first SA");
        }
        System.out.println("PASS: twelve-cut schedule, post-floor damage budget, motion boundaries and unchanged double/SA handoff");
    }
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
