package fish.payara.trader.pressure.patterns;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulates limit order book allocation matching L2/L3 market data structures.
 *
 * <p>Creates hierarchical object graphs with price levels containing arrays of orders, mimicking
 * real order book data structures used in high-frequency trading systems. Each allocation generates
 * a complete order book snapshot with configurable depth.
 *
 * <p>Typical allocation: 5 price levels × 20 orders per level ≈ 5KB per order book
 */
public class OrderBookPattern implements AllocationPattern {

  private static final String[] SYMBOLS = {
    "AAPL", "MSFT", "GOOGL", "AMZN", "TSLA", "META", "NVDA", "JPM"
  };

  @Override
  public Object allocate(int approximateBytes) {
    // Target: ~5KB per book (5 levels × 20 orders × ~50 bytes per order)
    // Calculate orders per level to approximate target size
    int numLevels = 5;
    int ordersPerLevel = approximateBytes / (numLevels * 250);
    ordersPerLevel = Math.max(10, Math.min(ordersPerLevel, 50)); // Clamp 10-50

    OrderBook book = new OrderBook(randomSymbol(), numLevels, ordersPerLevel);
    return book;
  }

  @Override
  public String getName() {
    return "OrderBook";
  }

  private String randomSymbol() {
    return SYMBOLS[ThreadLocalRandom.current().nextInt(SYMBOLS.length)];
  }

  /**
   * Represents a complete order book snapshot with bid and ask price levels.
   *
   * <p>In real HFT systems, this structure is updated thousands of times per second as orders are
   * added, modified, or executed.
   */
  static class OrderBook {
    final String symbol;
    final PriceLevel[] levels;

    OrderBook(String symbol, int numLevels, int ordersPerLevel) {
      this.symbol = symbol;
      this.levels = new PriceLevel[numLevels];

      double basePrice = 100.0 + ThreadLocalRandom.current().nextDouble(100.0);
      for (int i = 0; i < numLevels; i++) {
        levels[i] =
            new PriceLevel(
                basePrice + (i * 0.01),
                ordersPerLevel,
                i < numLevels / 2 // First half bid, second half ask
                );
      }
    }
  }

  /**
   * Represents a single price level in the order book containing multiple orders.
   *
   * <p>Each level aggregates orders at the same price point, separated by side (bid/ask).
   */
  static class PriceLevel {
    final double price;
    final Order[] orders;
    final boolean isBid;

    PriceLevel(double price, int numOrders, boolean isBid) {
      this.price = price;
      this.isBid = isBid;
      this.orders = new Order[numOrders];

      for (int i = 0; i < numOrders; i++) {
        orders[i] = new Order(ThreadLocalRandom.current().nextInt(100, 10000), System.nanoTime());
      }
    }
  }

  /**
   * Represents an individual order at a price level.
   *
   * <p>In production systems, orders also include trader IDs, timestamps, and other metadata. This
   * simplified version focuses on allocation profile matching.
   */
  static class Order {
    final int quantity;
    final long timestamp;

    Order(int quantity, long timestamp) {
      this.quantity = quantity;
      this.timestamp = timestamp;
    }
  }
}
