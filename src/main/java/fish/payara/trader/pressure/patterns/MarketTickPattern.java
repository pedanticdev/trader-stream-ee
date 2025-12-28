package fish.payara.trader.pressure.patterns;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulates market tick data batches using primitive arrays for efficient allocation.
 *
 * <p>Represents high-frequency tick ingestion where prices, volumes, and timestamps arrive in rapid
 * batches. Uses parallel primitive arrays (similar to columnar storage) to match real HFT tick
 * buffers.
 *
 * <p>Typical allocation: 100 ticks × ~40 bytes per tick ≈ 4KB per batch
 */
public class MarketTickPattern implements AllocationPattern {

  private static final String[] SYMBOLS = {
    "AAPL", "MSFT", "GOOGL", "AMZN", "TSLA", "META", "NVDA", "JPM"
  };

  @Override
  public Object allocate(int approximateBytes) {
    // Target: ~4KB per batch (100 ticks × ~40 bytes)
    // Calculate tick count to approximate target size
    int numTicks = approximateBytes / 40;
    numTicks = Math.max(50, Math.min(numTicks, 200)); // Clamp 50-200

    TickBatch batch = new TickBatch(randomSymbol(), numTicks);
    return batch;
  }

  @Override
  public String getName() {
    return "MarketTick";
  }

  private String randomSymbol() {
    return SYMBOLS[ThreadLocalRandom.current().nextInt(SYMBOLS.length)];
  }

  /**
   * Represents a batch of market ticks stored in columnar format.
   *
   * <p>Real HFT systems use this structure to efficiently process thousands of ticks per second,
   * enabling SIMD operations and cache-friendly sequential access.
   */
  static class TickBatch {
    final String symbol;
    final double[] prices;
    final long[] volumes;
    final long[] timestamps;

    TickBatch(String symbol, int numTicks) {
      this.symbol = symbol;
      this.prices = new double[numTicks];
      this.volumes = new long[numTicks];
      this.timestamps = new long[numTicks];

      double basePrice = 100.0 + ThreadLocalRandom.current().nextDouble(100.0);
      for (int i = 0; i < numTicks; i++) {
        // Simulate price walking with small random changes
        prices[i] = basePrice + ThreadLocalRandom.current().nextDouble(-1.0, 1.0);
        volumes[i] = ThreadLocalRandom.current().nextInt(100, 10000);
        timestamps[i] = System.nanoTime() + i;
      }
    }
  }
}
