package fish.payara.trader.jfr;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/**
 * Reference solution for the Module 3 exercise.
 *
 * <p>
 * Fires when the publisher's per-second rate exceeds its rolling 30-second baseline by a configurable multiplier (default 5x). One event per detection, not per
 * message - the event is for transitions, not for traffic.
 *
 * <p>
 * Design notes:
 * <ul>
 * <li>{@code @StackTrace(false)} - the event fires when burst mode is detected (once per spike), not per-message. We do want to know <em>where</em> in code it
 * fired, but recording the stack on every detection still adds overhead and the call site is fixed at one place in the publisher. If your detection lives in
 * multiple call sites, enable stack traces.</li>
 * <li>All numeric fields are primitives. JFR stores primitives inline in the event payload.</li>
 * <li>{@code @Category({"Market Data", "Workshop"})} - puts the event under <code>Market Data → Workshop</code> in the JMC Event Browser tree.</li>
 * </ul>
 */
@Name("burst.pattern.detected")
@Label("Burst Pattern Detected")
@Category({"Market Data", "Workshop"})
@Description("Fires when the publisher's rolling rate exceeds the baseline by the configured multiplier.")
@StackTrace(false)
public class BurstPatternEvent extends Event {

    @Label("Multiplier")
    @Description("Ratio of current rate to baseline rate, rounded to nearest integer.")
    public int multiplier;

    @Label("Window (ms)")
    @Description("Length of the rolling window used to compute the baseline.")
    public long windowMillis;

    @Label("Peak rate (msg/s)")
    public double peakRate;

    @Label("Baseline rate (msg/s)")
    public double baselineRate;

    /**
     * Convenience emitter that wraps the {@code isEnabled} gate. Call this from the publisher when a detection fires; it allocates only when JFR is recording
     * the event type.
     */
    public static void emit(int multiplier, long windowMillis, double peakRate, double baselineRate) {
        BurstPatternEvent event = new BurstPatternEvent();
        if (event.isEnabled()) {
            event.multiplier = multiplier;
            event.windowMillis = windowMillis;
            event.peakRate = peakRate;
            event.baselineRate = baselineRate;
            event.commit();
        }
    }
}
