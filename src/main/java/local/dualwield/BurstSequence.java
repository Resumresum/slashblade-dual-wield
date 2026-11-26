package local.dualwield;

/** Shared twelve-cut schedule; the double cut and four SA slots keep their old times. */
public final class BurstSequence {
    public static final int CUTS=12, INTERVAL_TICKS=4, DOUBLE_TICK=52, STAGES=CUTS+5;
    public static final float CUT_DAMAGE_SCALE=8f/CUTS;
    private BurstSequence() {}
    public static int startTick(int stage) {
        if(stage<0 || stage>=STAGES) throw new IndexOutOfBoundsException(stage);
        return stage<CUTS ? stage*INTERVAL_TICKS : DOUBLE_TICK+(stage-CUTS)*4;
    }
    public static boolean doubleBeat(int beat) { return beat==CUTS || beat==CUTS+1; }
    public static boolean compact(int beat) { return beat>=0 && beat<CUTS && beat%3==1; }
    public static float damage(double base,float ratio,float scale) {
        // Scale after the floor, so twelve low-power hits do not gain 50% floor damage.
        return (float)Math.max(.5,base*ratio)*scale;
    }
}
