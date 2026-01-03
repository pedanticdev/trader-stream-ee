package fish.payara.trader.monitoring;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SLAMonitorService Tests")
class SLAMonitorServiceTest {

    private SLAMonitorService slaMonitorService;

    @BeforeEach
    void setUp() {
        slaMonitorService = new SLAMonitorService();
    }

    @Test
    @DisplayName("Should initialize with zeroed stats")
    void shouldInitializeWithZeroedStats() {
        SLAMonitorService.SLAStats stats = slaMonitorService.getStats();

        assertEquals(0, stats.totalOperations);
        assertEquals(0, stats.violationsOver10ms);
        assertEquals(0, stats.violationsOver50ms);
        assertEquals(0, stats.violationsOver100ms);
        assertEquals(0.0, stats.violationRate);
        assertEquals(0, stats.recentViolations);
    }

    @Test
    @DisplayName("Should record normal operation below thresholds")
    void shouldRecordNormalOperationBelowThresholds() {
        slaMonitorService.recordOperation(5);

        SLAMonitorService.SLAStats stats = slaMonitorService.getStats();
        assertEquals(1, stats.totalOperations);
        assertEquals(0, stats.violationsOver10ms);
        assertEquals(0.0, stats.violationRate);
    }

    @Test
    @DisplayName("Should record violation over 10ms")
    void shouldRecordViolationOver10ms() {
        slaMonitorService.recordOperation(15);

        SLAMonitorService.SLAStats stats = slaMonitorService.getStats();
        assertEquals(1, stats.totalOperations);
        assertEquals(1, stats.violationsOver10ms);
        assertEquals(0, stats.violationsOver50ms);
        assertEquals(0, stats.violationsOver100ms);
        assertEquals(100.0, stats.violationRate);
        assertEquals(1, stats.recentViolations);
    }

    @Test
    @DisplayName("Should record violation over 50ms")
    void shouldRecordViolationOver50ms() {
        slaMonitorService.recordOperation(55);

        SLAMonitorService.SLAStats stats = slaMonitorService.getStats();
        assertEquals(1, stats.totalOperations);
        assertEquals(1, stats.violationsOver10ms);
        assertEquals(1, stats.violationsOver50ms);
        assertEquals(0, stats.violationsOver100ms);
        assertEquals(1, stats.recentViolations);
    }

    @Test
    @DisplayName("Should record violation over 100ms")
    void shouldRecordViolationOver100ms() {
        slaMonitorService.recordOperation(105);

        SLAMonitorService.SLAStats stats = slaMonitorService.getStats();
        assertEquals(1, stats.totalOperations);
        assertEquals(1, stats.violationsOver10ms);
        assertEquals(1, stats.violationsOver50ms);
        assertEquals(1, stats.violationsOver100ms);
        assertEquals(1, stats.recentViolations);
    }

    @Test
    @DisplayName("Should calculate violation rate correctly")
    void shouldCalculateViolationRateCorrectly() {
        slaMonitorService.recordOperation(5); // No violation
        slaMonitorService.recordOperation(15); // Violation > 10ms
        slaMonitorService.recordOperation(5); // No violation
        slaMonitorService.recordOperation(20); // Violation > 10ms

        SLAMonitorService.SLAStats stats = slaMonitorService.getStats();
        assertEquals(4, stats.totalOperations);
        assertEquals(2, stats.violationsOver10ms);
        assertEquals(50.0, stats.violationRate);
    }

    @Test
    @DisplayName("Should reset statistics")
    void shouldResetStatistics() {
        slaMonitorService.recordOperation(105);
        slaMonitorService.reset();

        SLAMonitorService.SLAStats stats = slaMonitorService.getStats();
        assertEquals(0, stats.totalOperations);
        assertEquals(0, stats.violationsOver10ms);
        assertEquals(0, stats.recentViolations);
    }

    @Test
    @DisplayName("Should handle multiple violations in a window")
    void shouldHandleMultipleViolationsInWindow() {
        for (int i = 0; i < 10; i++) {
            slaMonitorService.recordOperation(15);
        }

        SLAMonitorService.SLAStats stats = slaMonitorService.getStats();
        assertEquals(10, stats.recentViolations);
    }
}
