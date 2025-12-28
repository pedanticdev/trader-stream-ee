package fish.payara.trader.pressure.patterns;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulates L2/L3 market depth snapshots with bid/ask ladders.
 *
 * <p>Creates multi-dimensional arrays representing aggregate depth at each price level on both
 * sides of the book. Matches real market data feed structures that provide full market depth for
 * liquidity analysis.
 *
 * <p>Typical allocation: 2 sides × 50 levels × ~60 bytes per level ≈ 6KB per snapshot
 */
public class MarketDepthPattern implements AllocationPattern {

  private static final String[] SYMBOLS = {
    "AAPL", "MSFT", "GOOGL", "AMZN", "TSLA", "META", "NVDA", "JPM"
  };

  @Override
  public Object allocate(int approximateBytes) {
    // Target: ~6KB per snapshot (2 sides × 50 levels × ~60 bytes)
    // Calculate depth levels to approximate target size
    int depthLevels = approximateBytes / 120; // 2 sides × 60 bytes per level
    depthLevels = Math.max(10, Math.min(depthLevels, 100)); // Clamp 10-100

    DepthSnapshot snapshot = new DepthSnapshot(randomSymbol(), depthLevels);
    return snapshot;
  }

  @Override
  public String getName() {
    return "MarketDepth";
  }

  private String randomSymbol() {
    return SYMBOLS[ThreadLocalRandom.current().nextInt(SYMBOLS.length)];
  }

  /**
   * Represents a full market depth snapshot with bid and ask ladders.
   *
   * <p>Real trading systems use this to calculate available liquidity, optimal execution prices,
   * and market impact. Updated frequently (10-100ms) as orders change.
   */
  static class DepthSnapshot {
    final String symbol;
    final double[] bidPrices;
    final int[] bidSizes;
    final double[] askPrices;
    final int[] askSizes;
    final long timestamp;

    DepthSnapshot(String symbol, int depthLevels) {
      this.symbol = symbol;
      this.bidPrices = new double[depthLevels];
      this.bidSizes = new int[depthLevels];
      this.askPrices = new double[depthLevels];
      this.askSizes = new int[depthLevels];
      this.timestamp = System.nanoTime();

      double basePrice = 100.0 + ThreadLocalRandom.current().nextDouble(100.0);
      double spread = 0.05; // Bid-ask spread
      for (int i = 0; i < depthLevels; i++) {
        // Bids decrease with depth (best bid is basePrice - spread)
        bidPrices[i] = basePrice - spread - (i * 0.01);
        bidSizes[i] = ThreadLocalRandom.current().nextInt(100, 10000);
        // Asks increase with depth (best ask is basePrice)
        askPrices[i] = basePrice + (i * 0.01);
        askSizes[i] = ThreadLocalRandom.current().nextInt(100, 10000);
      }
    }
  }
}
