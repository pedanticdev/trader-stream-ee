package fish.payara.trader.pressure.patterns;

/**
 * Interface for HFT allocation patterns simulating realistic trading system memory profiles.
 * Implementations create domain-specific object graphs (order books, ticks, depth snapshots) that
 * match actual high-frequency trading application allocation behavior.
 *
 * <p>Each pattern generates objects approximating a target byte size, though actual allocation may
 * vary due to object headers, alignment, and JVM-specific overhead.
 */
public interface AllocationPattern {

  /**
   * Allocate objects approximating the specified byte size.
   *
   * <p>The implementation creates domain-specific object graphs (e.g., order book levels with order
   * arrays, tick batches with primitive arrays) that become garbage after the method returns,
   * simulating realistic HFT memory pressure.
   *
   * @param approximateBytes Target allocation size in bytes. Actual allocation may vary by 10-15%
   *     due to object headers, array overhead, and alignment padding.
   * @return Allocated object that will become garbage after method returns. The specific type
   *     depends on the pattern implementation.
   */
  Object allocate(int approximateBytes);

  /**
   * Returns the human-readable name of this allocation pattern.
   *
   * <p>Used for logging and metrics to identify which pattern generated allocations.
   *
   * @return Pattern name (e.g., "OrderBook", "MarketTick", "MarketDepth")
   */
  String getName();
}
