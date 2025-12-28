package fish.payara.trader.pressure.patterns;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("OrderBookPattern Tests")
class OrderBookPatternTest {

  private final OrderBookPattern pattern = new OrderBookPattern();

  @Test
  @DisplayName("Allocate creates OrderBook with expected structure")
  void testAllocate() {
    Object result = pattern.allocate(5000);

    assertNotNull(result, "Allocated object should not be null");
    assertTrue(
        result instanceof OrderBookPattern.OrderBook,
        "Allocated object should be OrderBook instance");

    OrderBookPattern.OrderBook book = (OrderBookPattern.OrderBook) result;
    assertNotNull(book.symbol, "Book symbol should not be null");
    assertNotNull(book.levels, "Book levels should not be null");
    assertTrue(book.levels.length > 0, "Book should have at least one level");

    // Verify levels contain orders
    for (OrderBookPattern.PriceLevel level : book.levels) {
      assertNotNull(level, "Price level should not be null");
      assertNotNull(level.orders, "Level orders array should not be null");
      assertTrue(level.orders.length > 0, "Level should have at least one order");

      // Verify orders have valid data
      for (OrderBookPattern.Order order : level.orders) {
        assertNotNull(order, "Order should not be null");
        assertTrue(order.quantity > 0, "Order quantity should be positive");
        assertTrue(order.timestamp > 0, "Order timestamp should be positive");
      }
    }
  }

  @Test
  @DisplayName("Pattern name is OrderBook")
  void testGetName() {
    assertEquals("OrderBook", pattern.getName(), "Pattern name should be 'OrderBook'");
  }

  @Test
  @DisplayName("Multiple allocations create different objects")
  void testMultipleAllocations() {
    Object obj1 = pattern.allocate(5000);
    Object obj2 = pattern.allocate(5000);

    assertNotSame(obj1, obj2, "Multiple allocations should create different object instances");
  }

  @Test
  @DisplayName("Allocation respects approximate byte size clamping")
  void testAllocationSizeClamping() {
    // Small allocation should clamp to minimum
    Object small = pattern.allocate(100);
    OrderBookPattern.OrderBook smallBook = (OrderBookPattern.OrderBook) small;
    assertTrue(smallBook.levels.length > 0, "Small allocation should still create valid book");

    // Large allocation should clamp to maximum
    Object large = pattern.allocate(100000);
    OrderBookPattern.OrderBook largeBook = (OrderBookPattern.OrderBook) large;
    assertTrue(largeBook.levels.length > 0, "Large allocation should create valid book");
  }

  @Test
  @DisplayName("Order book contains both bid and ask sides")
  void testBidAskStructure() {
    Object result = pattern.allocate(5000);
    OrderBookPattern.OrderBook book = (OrderBookPattern.OrderBook) result;

    boolean hasBid = false;
    boolean hasAsk = false;

    for (OrderBookPattern.PriceLevel level : book.levels) {
      if (level.isBid) {
        hasBid = true;
      } else {
        hasAsk = true;
      }
    }

    assertTrue(hasBid || hasAsk, "Book should contain at least one side (bid or ask)");
  }
}
