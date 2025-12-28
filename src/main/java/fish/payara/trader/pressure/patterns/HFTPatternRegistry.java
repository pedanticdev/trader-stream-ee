package fish.payara.trader.pressure.patterns;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Registry for HFT allocation patterns with round-robin selection.
 *
 * <p>Ensures even distribution of allocation types across patterns, simulating realistic HFT
 * workloads where order books, ticks, and depth snapshots are processed in parallel streams.
 *
 * <p>Thread-safe for concurrent access from multiple allocation threads (though current
 * implementation uses single allocation thread).
 */
@ApplicationScoped
public class HFTPatternRegistry {

  private final List<AllocationPattern> patterns;
  private final AtomicInteger roundRobinIndex = new AtomicInteger(0);

  /** Initializes registry with standard HFT allocation patterns. */
  public HFTPatternRegistry() {
    this.patterns =
        List.of(new OrderBookPattern(), new MarketTickPattern(), new MarketDepthPattern());
  }

  /**
   * Selects next pattern using round-robin distribution.
   *
   * <p>Thread-safe for concurrent access. Each call advances to the next pattern in sequence,
   * ensuring even distribution over time.
   *
   * @return Next pattern in rotation (OrderBook → Tick → Depth → OrderBook → ...)
   */
  public AllocationPattern selectPattern() {
    int index = roundRobinIndex.getAndIncrement();
    return patterns.get(Math.abs(index % patterns.size()));
  }

  /**
   * Returns all registered allocation patterns.
   *
   * @return Immutable list of patterns in registration order
   */
  public List<AllocationPattern> getAllPatterns() {
    return patterns;
  }

  /**
   * Returns the count of registered patterns.
   *
   * @return Number of patterns (currently 3: OrderBook, Tick, Depth)
   */
  public int getPatternCount() {
    return patterns.size();
  }
}
