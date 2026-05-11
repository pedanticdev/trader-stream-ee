package fish.payara.trader.jfr.templates;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/**
 * JFR event for finite-resource reservation and release.
 *
 * <p>Use this template for any resource pool where exhaustion has operational consequence: database connection pools, HTTP client pools, file handles, thread
 * pool slots, cache slots subject to eviction.
 *
 * <p>The point is NOT to emit one event per reservation. That is too noisy. Emit when the resource state crosses a threshold (pool 90% full, eviction
 * triggered) or when reservation is refused.
 *
 * <p>Usage:
 * <pre>{@code
 * if (pool.utilisation() > 0.9) {
 *     ResourceEvent event = new ResourceEvent();
 *     if (event.isEnabled()) {
 *         event.resourceType = "db-connection-pool";
 *         event.resourceName = "primary-read";
 *         event.state = "NEAR_EXHAUSTION";
 *         event.inUse = pool.inUse();
 *         event.capacity = pool.capacity();
 *         event.commit();
 *     }
 * }
 * }</pre>
 */
@Name("app.resource")
@Label("Application Resource")
@Category({"Application", "Resources"})
@Description("Fires on finite-resource state transitions (pool near exhaustion, eviction triggered, reservation refused).")
@StackTrace(false)
public class ResourceEvent extends Event {

    @Label("Resource type")
    @Description("Generic resource category: 'db-connection-pool', 'http-client-pool', 'cache', 'thread-pool', 'file-handle'.")
    public String resourceType;

    @Label("Resource name")
    @Description("Specific instance: which pool, which cache.")
    public String resourceName;

    @Label("State")
    @Description("'NEAR_EXHAUSTION', 'EXHAUSTED', 'EVICTED', 'REFUSED'.")
    public String state;

    @Label("In use")
    public int inUse;

    @Label("Capacity")
    public int capacity;
}
