package fish.payara.trader.pressure.patterns;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("HFTPatternRegistry Tests")
class HFTPatternRegistryTest {

  private final HFTPatternRegistry registry = new HFTPatternRegistry();

  @Test
  @DisplayName("Registry contains expected patterns")
  void testGetAllPatterns() {
    var patterns = registry.getAllPatterns();

    assertEquals(3, patterns.size(), "Registry should contain exactly 3 patterns");

    assertTrue(
        patterns.stream().anyMatch(p -> p instanceof OrderBookPattern),
        "Registry should contain OrderBookPattern");
    assertTrue(
        patterns.stream().anyMatch(p -> p instanceof MarketTickPattern),
        "Registry should contain MarketTickPattern");
    assertTrue(
        patterns.stream().anyMatch(p -> p instanceof MarketDepthPattern),
        "Registry should contain MarketDepthPattern");
  }

  @Test
  @DisplayName("Pattern count returns correct value")
  void testGetPatternCount() {
    assertEquals(3, registry.getPatternCount(), "Pattern count should be 3");
  }

  @Test
  @DisplayName("Round-robin distribution is even")
  void testRoundRobinDistribution() {
    Map<String, Integer> counts = new HashMap<>();

    // Select 300 patterns (100 rounds of 3 patterns)
    for (int i = 0; i < 300; i++) {
      AllocationPattern pattern = registry.selectPattern();
      counts.merge(pattern.getName(), 1, Integer::sum);
    }

    // Each pattern should appear exactly 100 times
    assertEquals(100, counts.get("OrderBook"), "OrderBook should appear 100 times");
    assertEquals(100, counts.get("MarketTick"), "MarketTick should appear 100 times");
    assertEquals(100, counts.get("MarketDepth"), "MarketDepth should appear 100 times");
  }

  @Test
  @DisplayName("Pattern selection never returns null")
  void testSelectionNeverNull() {
    for (int i = 0; i < 1000; i++) {
      assertNotNull(registry.selectPattern(), "Pattern selection should never return null");
    }
  }

  @Test
  @DisplayName("Pattern selection cycles correctly")
  void testPatternCycling() {
    // First 3 selections should be in order: OrderBook, Tick, Depth
    AllocationPattern p1 = registry.selectPattern();
    AllocationPattern p2 = registry.selectPattern();
    AllocationPattern p3 = registry.selectPattern();
    AllocationPattern p4 = registry.selectPattern(); // Should cycle back to OrderBook

    assertEquals("OrderBook", p1.getName(), "First pattern should be OrderBook");
    assertEquals("MarketTick", p2.getName(), "Second pattern should be MarketTick");
    assertEquals("MarketDepth", p3.getName(), "Third pattern should be MarketDepth");
    assertEquals("OrderBook", p4.getName(), "Fourth pattern should cycle back to OrderBook");
  }

  @Test
  @DisplayName("Registry is thread-safe for concurrent access")
  void testThreadSafety() throws InterruptedException {
    final int THREADS = 10;
    final int ITERATIONS = 100;
    Thread[] threads = new Thread[THREADS];
    Map<String, Integer> totalCounts = new HashMap<>();

    for (int i = 0; i < THREADS; i++) {
      threads[i] =
          new Thread(
              () -> {
                for (int j = 0; j < ITERATIONS; j++) {
                  AllocationPattern pattern = registry.selectPattern();
                  synchronized (totalCounts) {
                    totalCounts.merge(pattern.getName(), 1, Integer::sum);
                  }
                }
              });
      threads[i].start();
    }

    for (Thread thread : threads) {
      thread.join();
    }

    // Total selections: 10 threads × 100 iterations = 1000
    int totalSelections = totalCounts.values().stream().mapToInt(Integer::intValue).sum();
    assertEquals(1000, totalSelections, "Total selections should be 1000");

    // Each pattern should get approximately 333 selections (±tolerance for thread timing)
    int expectedPerPattern = 1000 / 3;
    int tolerance = 50; // Allow some variance due to concurrent access

    for (String patternName : new String[] {"OrderBook", "MarketTick", "MarketDepth"}) {
      int count = totalCounts.getOrDefault(patternName, 0);
      assertTrue(
          Math.abs(count - expectedPerPattern) <= tolerance,
          String.format(
              "Pattern %s should have ~%d selections, got %d",
              patternName, expectedPerPattern, count));
    }
  }
}
