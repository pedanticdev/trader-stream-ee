package fish.payara.trader.pressure.patterns;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MarketDepthPattern Tests")
class MarketDepthPatternTest {

  private final MarketDepthPattern pattern = new MarketDepthPattern();

  @Test
  @DisplayName("Allocate creates DepthSnapshot with bid/ask arrays")
  void testAllocate() {
    Object result = pattern.allocate(6000);

    assertNotNull(result, "Allocated object should not be null");
    assertTrue(
        result instanceof MarketDepthPattern.DepthSnapshot,
        "Allocated object should be DepthSnapshot instance");

    MarketDepthPattern.DepthSnapshot snapshot = (MarketDepthPattern.DepthSnapshot) result;
    assertNotNull(snapshot.symbol, "Snapshot symbol should not be null");
    assertNotNull(snapshot.bidPrices, "Bid prices array should not be null");
    assertNotNull(snapshot.bidSizes, "Bid sizes array should not be null");
    assertNotNull(snapshot.askPrices, "Ask prices array should not be null");
    assertNotNull(snapshot.askSizes, "Ask sizes array should not be null");
    assertTrue(snapshot.timestamp > 0, "Timestamp should be positive");

    // Verify array lengths match
    assertEquals(
        snapshot.bidPrices.length,
        snapshot.bidSizes.length,
        "Bid prices and sizes should have same length");
    assertEquals(
        snapshot.askPrices.length,
        snapshot.askSizes.length,
        "Ask prices and sizes should have same length");
    assertEquals(
        snapshot.bidPrices.length,
        snapshot.askPrices.length,
        "Bid and ask sides should have same depth");

    // Verify minimum clamping
    assertTrue(
        snapshot.bidPrices.length >= 10, "Depth should have at least 10 levels (minimum clamping)");

    // Verify data validity
    for (int i = 0; i < snapshot.bidPrices.length; i++) {
      assertTrue(snapshot.bidPrices[i] > 0, "Bid price should be positive");
      assertTrue(snapshot.bidSizes[i] > 0, "Bid size should be positive");
      assertTrue(snapshot.askPrices[i] > 0, "Ask price should be positive");
      assertTrue(snapshot.askSizes[i] > 0, "Ask size should be positive");
    }
  }

  @Test
  @DisplayName("Pattern name is MarketDepth")
  void testGetName() {
    assertEquals("MarketDepth", pattern.getName(), "Pattern name should be 'MarketDepth'");
  }

  @Test
  @DisplayName("Multiple allocations create different objects")
  void testMultipleAllocations() {
    Object obj1 = pattern.allocate(6000);
    Object obj2 = pattern.allocate(6000);

    assertNotSame(obj1, obj2, "Multiple allocations should create different object instances");
  }

  @Test
  @DisplayName("Depth levels respect clamping boundaries")
  void testSizeClamping() {
    // Small allocation should clamp to 10 minimum
    Object small = pattern.allocate(100);
    MarketDepthPattern.DepthSnapshot smallSnapshot = (MarketDepthPattern.DepthSnapshot) small;
    assertTrue(
        smallSnapshot.bidPrices.length >= 10, "Small allocation should clamp to minimum 10 levels");

    // Large allocation should clamp to 100 maximum
    Object large = pattern.allocate(100000);
    MarketDepthPattern.DepthSnapshot largeSnapshot = (MarketDepthPattern.DepthSnapshot) large;
    assertTrue(
        largeSnapshot.bidPrices.length <= 100,
        "Large allocation should clamp to maximum 100 levels");
  }

  @Test
  @DisplayName("Bid prices decrease with depth, ask prices increase")
  void testPriceLadderStructure() {
    Object result = pattern.allocate(6000);
    MarketDepthPattern.DepthSnapshot snapshot = (MarketDepthPattern.DepthSnapshot) result;

    // Bid prices should generally decrease (away from best bid)
    boolean bidDecreasing = true;
    for (int i = 1; i < snapshot.bidPrices.length; i++) {
      if (snapshot.bidPrices[i] > snapshot.bidPrices[i - 1]) {
        bidDecreasing = false;
        break;
      }
    }

    // Ask prices should generally increase (away from best ask)
    boolean askIncreasing = true;
    for (int i = 1; i < snapshot.askPrices.length; i++) {
      if (snapshot.askPrices[i] < snapshot.askPrices[i - 1]) {
        askIncreasing = false;
        break;
      }
    }

    assertTrue(bidDecreasing, "Bid prices should decrease with depth");
    assertTrue(askIncreasing, "Ask prices should increase with depth");
  }

  @Test
  @DisplayName("Bid prices are below ask prices (no crossed market)")
  void testNoCrossedMarket() {
    Object result = pattern.allocate(6000);
    MarketDepthPattern.DepthSnapshot snapshot = (MarketDepthPattern.DepthSnapshot) result;

    // Best bid (index 0) should be below best ask (index 0)
    assertTrue(
        snapshot.bidPrices[0] < snapshot.askPrices[0],
        "Best bid should be below best ask (no crossed market)");
  }
}
