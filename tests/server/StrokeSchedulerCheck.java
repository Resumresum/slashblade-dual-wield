package local.dualwield;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Run with assertions enabled; no game, dependencies or framework required. */
public final class StrokeSchedulerCheck {
    public static void main(String[] args) {
        var time = new AtomicLong(100);
        var owner = new Object();
        var events = new ArrayList<Long>();
        StrokeScheduler.after(owner, time::get, 2, () -> true, () -> events.add(time.get()));
        StrokeScheduler.tick();
        time.incrementAndGet();
        StrokeScheduler.tick();
        require(events.isEmpty(), "a cut damaged during windup");
        time.incrementAndGet();
        StrokeScheduler.tick();
        StrokeScheduler.tick();
        require(events.equals(List.of(102L)), "contact did not execute exactly once at +2");

        for (int beat = 0; beat < 3; beat++) {
            long start = 200 + beat * 3;
            time.set(start);
            StrokeScheduler.after(owner, time::get, 1, () -> true, () -> events.add(time.get()));
            StrokeScheduler.tick();
            time.incrementAndGet();
            StrokeScheduler.tick();
        }
        require(events.equals(List.of(102L, 201L, 204L, 207L)), "thrust contacts lost their 3-tick spacing");

        StrokeScheduler.after(owner, time::get, 2, () -> true, () -> { throw new AssertionError("cancelled contact ran"); });
        StrokeScheduler.cancel(owner);
        time.addAndGet(2);
        StrokeScheduler.tick();
        boolean[] valid = {true};
        StrokeScheduler.after(owner, time::get, 2, () -> valid[0], () -> { throw new AssertionError("invalid contact revived"); });
        valid[0] = false;
        StrokeScheduler.tick();
        valid[0] = true;
        time.addAndGet(2);
        StrokeScheduler.tick();

        // Equipment may change inside the first of two simultaneous contacts.
        StrokeScheduler.after(owner, time::get, 1, () -> true, () -> StrokeScheduler.cancel(owner));
        StrokeScheduler.after(owner, time::get, 1, () -> true, () -> { throw new AssertionError("cancelled double hit ran"); });
        time.incrementAndGet();
        StrokeScheduler.tick();

        StrokeScheduler.after(owner, time::get, 1, () -> true,
            () -> StrokeScheduler.after(owner, time::get, 1, () -> true, () -> events.add(time.get())));
        time.incrementAndGet();
        StrokeScheduler.tick();
        time.incrementAndGet();
        StrokeScheduler.tick();
        require(events.getLast() == time.get(), "contact callbacks cannot schedule another action");
        System.out.println("PASS: contact phases, 3-tick thrust spacing, exactly-once execution, cancellation, invalidation and reentrant scheduling");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
