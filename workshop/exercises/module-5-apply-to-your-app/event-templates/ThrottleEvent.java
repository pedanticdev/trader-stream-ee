package fish.payara.trader.jfr.templates;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/**
 * JFR event for throttle / rate-limit / circuit-breaker activations.
 *
 * <p>Emit one event each time a protective mechanism kicks in, NOT each time a request is throttled. The semantics is "the gate opened" or "the gate closed";
 * the rate of throttled requests is a downstream metric.
 *
 * <p>Stack traces are enabled on this template by default because throttle activations are rare enough that the call stack matters: you almost always want to
 * know which code path triggered the protection.
 *
 * <p>Usage:
 * <pre>{@code
 * if (circuitBreaker.tripped()) {
 *     ThrottleEvent event = new ThrottleEvent();
 *     if (event.isEnabled()) {
 *         event.mechanism = "circuit-breaker";
 *         event.scope = "outbound-pricing-api";
 *         event.state = "OPENED";
 *         event.failureCount = circuitBreaker.recentFailures();
 *         event.commit();
 *     }
 * }
 * }</pre>
 */
@Name("app.throttle")
@Label("Application Throttle")
@Category({"Application", "Resilience"})
@Description("Fires when a circuit breaker, rate limit, or backpressure mechanism changes state.")
@StackTrace(true)
public class ThrottleEvent extends Event {

    @Label("Mechanism")
    @Description("Which protective mechanism fired: 'circuit-breaker', 'rate-limit', 'bulkhead', 'backpressure'.")
    public String mechanism;

    @Label("Scope")
    @Description("What the mechanism is protecting: an outbound dependency, an inbound endpoint, a queue.")
    public String scope;

    @Label("State")
    @Description("State transition: 'OPENED', 'CLOSED', 'HALF_OPEN'.")
    public String state;

    @Label("Failure count")
    @Description("Recent failure count within the mechanism's evaluation window.")
    public int failureCount;
}
