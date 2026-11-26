package local.dualwield;

/** Shared timing and blade frames in view space (right, up, backwards), in metres. */
public final class BladeMotion {
    public static final int CUT = 0, RETURN = 1, FINISH = 2, THRUST = 3, BURST = 4, FOLLOW_UP = 5;
    // Offhand X is mirrored: reverse cuts and depth changes break up the repeated inward fan.
    private static final float[] BURST_SLOPES = {-52f,146f,62f,-128f,-90f,52f,90f,-146f,-58f,126f,42f,-62f};
    private static final float[] BURST_CANTS = {.18f,.48f,.30f,.44f,.22f,.50f,.36f,.20f,.46f,.28f,.52f,.16f};
    private BladeMotion() {}
    public static int durationTicks(int kind) { return kind == THRUST ? 3 : kind == BURST ? 6 : 4; }
    public static int durationTicks(int kind, int beat) { return kind == BURST && beat >= BurstSequence.CUTS ? 4 : durationTicks(kind); }
    public static int contactTicks(int kind) { return kind == THRUST ? 1 : 2; }
    public static float strikeStart(int kind) { return kind == THRUST || kind == FINISH ? 0f : 0.12f; }
    public static float strikeEnd(int kind, int beat) {
        float contact = (float) contactTicks(kind) / durationTicks(kind, beat);
        return kind == THRUST || kind == FINISH ? contact : contact + 0.22f;
    }
    public static Pose sample(int kind, int beat, float progress) {
        float t = clamp(progress);
        float contact = (float) contactTicks(kind) / durationTicks(kind, beat);
        Pose pose;
        if (kind == THRUST || kind == FINISH) {
            float drive = t <= contact ? ease(t / contact)
                : 1f - ease((t - contact - 0.045f) / (1f - contact - 0.045f));
            // Straight extension and retraction: the point, not the flat, leads the stab.
            float dx = 0.10f, dy = -0.025f, dz = -(float) Math.sqrt(1f-dx*dx-dy*dy);
            pose = frame(-0.25f + drive*0.48f*dx, (kind == FINISH ? 0.20f : -0.08f) + drive*0.48f*dy,
                -0.62f + drive*0.48f*dz, dx, dy, dz, 0f, 1f, 0f);
        } else {
            // The finisher emits two simultaneous packets after the twelve singles.
            boolean doubleCut = kind == BURST && BurstSequence.doubleBeat(beat);
            // Different diagonal planes, not a mirrored copy of one horizontal fan.
            float slope = doubleCut ? -90f : kind == BURST ? BURST_SLOPES[Math.floorMod(beat,BURST_SLOPES.length)]
                : kind == FOLLOW_UP ? 29f : kind == RETURN ? 147f : -39f;
            float phi = (float) Math.toRadians(slope);
            float ux = (float) Math.cos(phi), uy = (float) Math.sin(phi);
            // Cant the whole cutting plane towards the camera: otherwise an arc through
            // the optical axis projects to a nearly invisible straight, edge-on line.
            float cant=kind==BURST ? BURST_CANTS[Math.floorMod(beat,BURST_CANTS.length)] : .36f;
            float fx=-uy*cant, fy=ux*cant, fz=-(float)Math.sqrt(1f-cant*cant);
            if(doubleCut) { ux=0; uy=-1; fx=0; fy=0; fz=-1; }
            float end = strikeEnd(kind, beat);
            boolean wideBurst=kind==BURST && !doubleCut;
            boolean compact=wideBurst && BurstSequence.compact(beat);
            float extent = (float) Math.toRadians(compact ? 72f : wideBurst ? 88f : kind == FOLLOW_UP ? 54f : 63f);
            // Match angular velocity through contact rather than stopping at the target.
            float speed = 2.4f*extent/(end-strikeStart(kind));
            float angle = t <= contact
                ? hermite(-extent, 0, 0, speed*(contact-strikeStart(kind)), clamp((t-strikeStart(kind))/(contact-strikeStart(kind))))
                : hermite(0, extent, speed*(end-contact), 0, clamp((t-contact)/(end-contact)));
            float sin = (float) Math.sin(angle), cos = (float) Math.cos(angle);
            // Grip and every blade point rotate in ONE plane about a shared pivot.
            float dx = fx*cos+ux*sin, dy = fy*cos+uy*sin, dz = fz*cos;
            float ex = -fx*sin+ux*cos, ey = -fy*sin+uy*cos, ez = -fz*sin;
            float px = (kind == FOLLOW_UP ? -0.15f : -0.07f)+uy*0.24f;
            float py = (kind == FOLLOW_UP ? -0.12f : -0.02f)-ux*0.24f;
            if(doubleCut) { px=-.32f; py=-.15f; }
            float radius=compact ? .52f : wideBurst ? .94f : doubleCut ? .52f : .14f;
            float depth=wideBurst && !compact ? -.70f : -.61f;
            pose = new Pose(px+radius*dx, py+radius*dy, depth+radius*dz, dx,dy,dz,ex,ey,ez);
        }
        // Hold the finish, then return to a drawn guard over two unlit ticks.
        if (progress > 1f && kind != THRUST) {
            var ready=kind==BURST || kind==FOLLOW_UP ? longGuard() : guard();
            pose = blend(pose, ready, ease((progress-1f)*durationTicks(kind,beat)/2f));
        }
        return pose;
    }
    public static Pose guard() { return frame(-0.23f,-0.12f,-0.64f,-0.48f,0.40f,-0.78f,0f,-1f,0f); }
    private static Pose longGuard() { return frame(-.40f,-.12f,-.64f,-.24f,.40f,-.885f,0,-1,0); }
    /** First-person long blades need room for the full hilt behind the grip. */
    public static Pose viewPose(Pose pose, boolean shortBlade, float hiltReach) {
        if(shortBlade) return new Pose(-pose.gx,pose.gy,pose.gz,-pose.dx,pose.dy,pose.dz,-pose.ex,pose.ey,pose.ez);
        // Keep a constant translation throughout the cut: a per-frame depth clamp would bend its path.
        float forward=Math.max(.42f,hiltReach-.05f);
        return new Pose(pose.gx-.40f,pose.gy-.18f,pose.gz-forward,
            pose.dx,pose.dy,pose.dz,pose.ex,pose.ey,pose.ez);
    }
    public static float weight(int kind,int beat,float ageTicks,float initialWeight,boolean held,float releasedAtTick) {
        float in=lerp(ease(ageTicks/.4f),initialWeight,1f);
        if(held) return in;
        float fadeStart=Math.max(durationTicks(kind,beat)+2f,releasedAtTick);
        return in*(1f-ease((ageTicks-fadeStart)/2f));
    }
    /** Used outside the cutting pass, to join an interrupted finish to the next preparation. */
    public static Pose blend(Pose a, Pose b, float t) {
        t = clamp(t);
        float dx=lerp(t,a.dx,b.dx),dy=lerp(t,a.dy,b.dy),dz=lerp(t,a.dz,b.dz);
        float dl=(float)Math.sqrt(dx*dx+dy*dy+dz*dz); dx/=dl; dy/=dl; dz/=dl;
        var from=transport(a,dx,dy,dz);
        var end=transport(a,b.dx,b.dy,b.dz);
        // Compute the wrist turn once between the fixed endpoints. Re-evaluating the
        // shortest roll at each intermediate direction can switch signs at 180 degrees.
        float cx=end.ey*b.ez-end.ez*b.ey, cy=end.ez*b.ex-end.ex*b.ez, cz=end.ex*b.ey-end.ey*b.ex;
        float angle=(float)Math.atan2(b.dx*cx+b.dy*cy+b.dz*cz,end.ex*b.ex+end.ey*b.ey+end.ez*b.ez)*t;
        float cos=(float)Math.cos(angle),sin=(float)Math.sin(angle);
        return new Pose(lerp(t,a.gx,b.gx),lerp(t,a.gy,b.gy),lerp(t,a.gz,b.gz),from.dx,from.dy,from.dz,
            from.ex*cos+(from.dy*from.ez-from.dz*from.ey)*sin,
            from.ey*cos+(from.dz*from.ex-from.dx*from.ez)*sin,
            from.ez*cos+(from.dx*from.ey-from.dy*from.ex)*sin);
    }
    private static Pose transport(Pose a,float dx,float dy,float dz) {
        // Minimal rotation from a's blade axis to the new axis. Authored poses all face
        // forwards, so their axes never form an antipodal pair.
        float vx=a.dy*dz-a.dz*dy,vy=a.dz*dx-a.dx*dz,vz=a.dx*dy-a.dy*dx;
        float wx=vy*a.ez-vz*a.ey,wy=vz*a.ex-vx*a.ez,wz=vx*a.ey-vy*a.ex;
        float denominator=Math.max(.000001f,1+a.dx*dx+a.dy*dy+a.dz*dz);
        return frame(0,0,0,dx,dy,dz,a.ex+wx+(vy*wz-vz*wy)/denominator,
            a.ey+wy+(vz*wx-vx*wz)/denominator,a.ez+wz+(vx*wy-vy*wx)/denominator);
    }
    private static Pose frame(float x,float y,float z,float dx,float dy,float dz,float ex,float ey,float ez) {
        float dl = (float) Math.sqrt(dx*dx+dy*dy+dz*dz);
        dx/=dl; dy/=dl; dz/=dl;
        float dot = dx*ex+dy*ey+dz*ez;
        ex-=dx*dot; ey-=dy*dot; ez-=dz*dot;
        float el = (float) Math.sqrt(ex*ex+ey*ey+ez*ez);
        if (el < 0.0001f) {
            ex=Math.abs(dy)<.9f ? 0 : 1; ey=Math.abs(dy)<.9f ? 1 : 0; ez=0;
            dot=dx*ex+dy*ey; ex-=dx*dot; ey-=dy*dot; ez-=dz*dot;
            el=(float)Math.sqrt(ex*ex+ey*ey+ez*ez);
        }
        return new Pose(x,y,z,dx,dy,dz,ex/el,ey/el,ez/el);
    }
    private static float hermite(float a,float b,float va,float vb,float t) {
        float t2=t*t,t3=t2*t;
        return (2*t3-3*t2+1)*a+(t3-2*t2+t)*va+(-2*t3+3*t2)*b+(t3-t2)*vb;
    }
    public static float ease(float t) { t=clamp(t); return t*t*(3f-2f*t); }
    private static float clamp(float t) { return Math.max(0f,Math.min(1f,t)); }
    private static float lerp(float t,float a,float b) { return a+t*(b-a); }
    public record Pose(float gx,float gy,float gz,float dx,float dy,float dz,float ex,float ey,float ez) {}
}
