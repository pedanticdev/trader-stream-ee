package fish.payara.trader;

import static org.junit.jupiter.api.Assertions.*;

import fish.payara.trader.pressure.AllocationMode;
import fish.payara.trader.pressure.MemoryPressureService;
import fish.payara.trader.utils.GCTestUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Basic functionality tests to ensure the test infrastructure works */
@DisplayName("Basic Functionality Tests")
class BasicFunctionalityTest {

  @Test
  @DisplayName("Should verify allocation modes work correctly")
  void shouldVerifyAllocationModesWorkCorrectly() {
    // Test AllocationMode enum
    assertEquals(AllocationMode.OFF, AllocationMode.valueOf("OFF"));
    assertEquals(AllocationMode.LOW, AllocationMode.valueOf("LOW"));
    assertEquals(AllocationMode.MEDIUM, AllocationMode.valueOf("MEDIUM"));
    assertEquals(AllocationMode.HIGH, AllocationMode.valueOf("HIGH"));
    assertEquals(AllocationMode.EXTREME, AllocationMode.valueOf("EXTREME"));

    // Test allocation rates
    assertEquals(0L, AllocationMode.OFF.getBytesPerSecond());
    assertTrue(AllocationMode.LOW.getBytesPerSecond() > 0);
    assertTrue(AllocationMode.EXTREME.getBytesPerSecond() > AllocationMode.LOW.getBytesPerSecond());
  }

  @Test
  @DisplayName("Should initialize memory pressure service without CDI")
  void shouldInitializeMemoryPressureServiceWithoutCDI() {
    MemoryPressureService service = new MemoryPressureService();
    service.init();

    // Can't test mode changes without CDI injection, but can verify initialization
    assertNotNull(service);
    assertEquals(AllocationMode.OFF, service.getCurrentMode()); // Default should be OFF
  }

  @Test
  @DisplayName("Should capture and calculate GC statistics")
  void shouldCaptureAndCalculateGCStatistics() {
    // Test GC utility functionality
    GCTestUtil.GCStatistics beforeStats = GCTestUtil.captureInitialStats();
    assertNotNull(beforeStats);
    assertTrue(beforeStats.collections >= 0);
    assertTrue(beforeStats.time >= 0);

    // Create some memory pressure
    GCTestUtil.allocateMemory(10); // 10MB

    GCTestUtil.GCStatistics afterStats = GCTestUtil.calculateDelta(beforeStats);
    assertNotNull(afterStats);
    assertTrue(afterStats.collections >= 0);
    assertTrue(afterStats.time >= 0);
  }

  @Test
  @DisplayName("Should measure operation time correctly")
  void shouldMeasureOperationTimeCorrectly() {
    long startTime = System.nanoTime();

    // Simulate some work
    try {
      Thread.sleep(10);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    long pauseTime =
        GCTestUtil.measurePauseTime(
            () -> {
              // Simulate GC pause
              try {
                Thread.sleep(5);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });

    assertTrue(pauseTime >= 0, "Pause time should be non-negative");
    // Note: This might not measure actual GC pause time in all cases, but tests the utility
  }
}
