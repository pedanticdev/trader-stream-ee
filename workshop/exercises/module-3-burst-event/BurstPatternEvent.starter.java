package fish.payara.trader.jfr;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/**
 * STARTER FILE.
 * <p>
 * Complete the TODOs to instrument the publisher with a custom JFR event that fires when the per-second publish rate exceeds the rolling baseline by 5x or
 * more.
 *
 * <p>
 * After you finish, install this into the source tree:
 *
 * <pre>
 *   ./workshop/scripts/install-exercise.sh module-3-burst-event
 * </pre>
 */
@Category({"Market Data", "Workshop"})
@Label("Burst Pattern Detected")
@Description("Fires when the publisher detects a sustained 5x spike over its rolling baseline.")
public class BurstPatternEvent {

    // TODO 1: Make this an event class.
    // Replace the static inner class below with a public top-level class that
    // extends jdk.jfr.Event. Apply @Name, @Label, @StackTrace correctly.
    //
    // Hints:
    //   - @Name should be the event's stable identifier, e.g. "burst.pattern.detected"
    //   - @Label is a human-readable label shown in JMC
    //   - This event fires once per detection, NOT once per message; do you want
    //     stack traces enabled or disabled? Argue for one. Then apply @StackTrace.

    @Name("burst.pattern.detected")
    @Label("Burst Pattern Detected")
    @StackTrace(false)
    public static class Detail extends Event {

        // TODO 2: Add the fields. Suggested set:
        //   - multiplier  (int)    - the ratio of current rate to baseline (e.g. 5 for 5x)
        //   - windowMillis (long)  - the rolling window length used for the baseline
        //   - peakRate    (double) - messages per second at the peak of the window
        //   - baselineRate (double) - the baseline rate the multiplier was computed against
        //
        // Use primitive types where possible. Strings cost more.

        public int multiplier;

        // TODO 3 (optional): If you want this event to record duration, use
        // event.begin() / event.commit() instead of just commit(). Otherwise
        // the event timestamps a point in time, not a span.
    }

    // TODO 4: Wire this into the publisher.
    //
    // In src/main/java/fish/payara/trader/aeron/MarketDataPublisher.java, find
    // the existing burst-mode-activated emission (look for `BurstModeActivated`).
    // Near it, add a separate detection step that computes the rolling rate and,
    // when the multiplier crosses your threshold, emits BurstPatternEvent.Detail.
    //
    // Pattern:
    //   BurstPatternEvent.Detail event = new BurstPatternEvent.Detail();
    //   if (event.isEnabled()) {
    //       event.multiplier = computedMultiplier;
    //       event.commit();
    //   }
    //
    // The isEnabled() check is critical on hot paths. Without it, every
    // detection sample allocates an event object even when JFR is off.
}
