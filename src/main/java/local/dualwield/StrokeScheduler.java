package local.dualwield;

import java.util.ArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Server-thread contacts; callbacks can safely cancel other contacts or enqueue new ones. */
final class StrokeScheduler {
    private static final ArrayList<Contact> PENDING = new ArrayList<>();

    private StrokeScheduler() {}

    static void after(Object owner, LongSupplier clock, int delay, BooleanSupplier valid, Runnable action) {
        PENDING.add(new Contact(owner, clock, clock.getAsLong() + delay, valid, action));
    }

    static void cancel(Object owner) {
        PENDING.removeIf(contact -> contact.owner == owner);
    }

    static void tick() {
        for (var contact : new ArrayList<>(PENDING)) {
            if (!PENDING.contains(contact)) continue;
            if (!contact.valid.getAsBoolean()) PENDING.remove(contact);
            else if (contact.clock.getAsLong() >= contact.tick && PENDING.remove(contact)) contact.action.run();
        }
    }

    private record Contact(Object owner, LongSupplier clock, long tick, BooleanSupplier valid, Runnable action) {}
}
