package fish.payara.trader.pressure.patterns;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MarketTickPattern Tests")
class MarketTickPatternTest {

  private final MarketTickPattern pattern = new MarketTickPattern();

  @Test
  @DisplayName("Allocate creates TickBatch with primitive arrays")
  void testAllocate() {
    Object result = pattern.allocate(4000);

    assertNotNull(result, "Allocated object should not be null");
    assertTrue(
        result instanceof MarketTickPattern.TickBatch,
        "Allocated object should be TickBatch instance");

    MarketTickPattern.TickBatch batch = (MarketTickPattern.TickBatch) result;
    assertNotNull(batch.symbol, "Batch symbol should not be null");
    assertNotNull(batch.prices, "Prices array should not be null");
    assertNotNull(batch.volumes, "Volumes array should not be null");
    assertNotNull(batch.timestamps, "Timestamps array should not be null");

    // Verify array lengths match
    assertEquals(
        batch.prices.length,
        batch.volumes.length,
        "Prices and volumes arrays should have same length");
    assertEquals(
        batch.prices.length,
        batch.timestamps.length,
        "Prices and timestamps arrays should have same length");

    // Verify minimum size clamping
    assertTrue(batch.prices.length >= 50, "Batch should have at least 50 ticks (minimum clamping)");

    // Verify data validity
    for (int i = 0; i < batch.prices.length; i++) {
      assertTrue(batch.prices[i] > 0, "Price should be positive");
      assertTrue(batch.volumes[i] > 0, "Volume should be positive");
      assertTrue(batch.timestamps[i] > 0, "Timestamp should be positive");
    }
  }

  @Test
  @DisplayName("Pattern name is MarketTick")
  void testGetName() {
    assertEquals("MarketTick", pattern.getName(), "Pattern name should be 'MarketTick'");
  }

  @Test
  @DisplayName("Multiple allocations create different objects")
  void testMultipleAllocations() {
    Object obj1 = pattern.allocate(4000);
    Object obj2 = pattern.allocate(4000);

    assertNotSame(obj1, obj2, "Multiple allocations should create different object instances");
  }

  @Test
  @DisplayName("Tick batch size respects clamping boundaries")
  void testSizeClamping() {
    // Small allocation should clamp to 50 minimum
    Object small = pattern.allocate(100);
    MarketTickPattern.TickBatch smallBatch = (MarketTickPattern.TickBatch) small;
    assertTrue(smallBatch.prices.length >= 50, "Small allocation should clamp to minimum 50 ticks");

    // Large allocation should clamp to 200 maximum
    Object large = pattern.allocate(100000);
    MarketTickPattern.TickBatch largeBatch = (MarketTickPattern.TickBatch) large;
    assertTrue(
        largeBatch.prices.length <= 200, "Large allocation should clamp to maximum 200 ticks");
  }

  @Test
  @DisplayName("Tick prices show realistic variation")
  void testPriceVariation() {
    Object result = pattern.allocate(4000);
    MarketTickPattern.TickBatch batch = (MarketTickPattern.TickBatch) result;

    // Check that not all prices are identical (should have random walk)
    double firstPrice = batch.prices[0];
    boolean hasVariation = false;

    for (int i = 1; i < batch.prices.length; i++) {
      if (Math.abs(batch.prices[i] - firstPrice) > 0.01) {
        hasVariation = true;
        break;
      }
    }

    assertTrue(hasVariation, "Prices should show variation (random walk behavior)");
  }
}
