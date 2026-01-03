package fish.payara.trader.rest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import fish.payara.trader.aeron.MarketDataPublisher;
import fish.payara.trader.gc.GCStatsService;
import fish.payara.trader.monitoring.GCPauseMonitor;
import fish.payara.trader.monitoring.SLAMonitorService;
import fish.payara.trader.pressure.AllocationMode;
import fish.payara.trader.pressure.MemoryPressureService;
import jakarta.ws.rs.core.Response;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GCStatsResource Tests")
class GCStatsResourceTest {

    @Mock
    private GCStatsService gcStatsService;
    @Mock
    private MemoryPressureService memoryPressureService;
    @Mock
    private MarketDataPublisher publisher;
    @Mock
    private SLAMonitorService slaMonitor;
    @Mock
    private GCPauseMonitor gcPauseMonitor;

    @InjectMocks
    private GCStatsResource resource;

    @Test
    @DisplayName("Should return SLA stats")
    void shouldReturnSLAStats() {
        SLAMonitorService.SLAStats mockStats = new SLAMonitorService.SLAStats(100, 5, 2, 1, 5.0, 3);
        when(slaMonitor.getStats()).thenReturn(mockStats);

        Response response = resource.getSLAStats();

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(mockStats, response.getEntity());
    }

    @Test
    @DisplayName("Should reset SLA stats")
    void shouldResetSLAStats() {
        Response response = resource.resetSLAStats();

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        verify(slaMonitor).reset();
    }

    @Test
    @DisplayName("Should return GC pause stats")
    void shouldReturnGCPauseStats() {
        GCPauseMonitor.GCPauseStats mockStats = new GCPauseMonitor.GCPauseStats(10, 100, 10.0, 10, 20, 30, 40, 50, 5, 2, 1, 10);
        when(gcPauseMonitor.getStats()).thenReturn(mockStats);

        Response response = resource.getGCPauseStats();

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(mockStats, response.getEntity());
    }

    @Test
    @DisplayName("Should return accurate comparison data")
    void shouldReturnAccurateComparisonData() {
        when(memoryPressureService.getCurrentMode()).thenReturn(AllocationMode.STEADY_LOAD);
        when(publisher.getMessagesPublished()).thenReturn(5000L);
        when(gcStatsService.collectGCStats()).thenReturn(Collections.emptyList());

        GCPauseMonitor.GCPauseStats mockPauseStats = new GCPauseMonitor.GCPauseStats(10, 100, 10.0, 10, 20, 30, 40, 50, 5, 2, 1, 10);
        when(gcPauseMonitor.getStats()).thenReturn(mockPauseStats);

        Response response = resource.getComparison();

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> entity = (Map<String, Object>) response.getEntity();

        assertEquals(AllocationMode.STEADY_LOAD, entity.get("allocationMode"));
        assertEquals(5000L, entity.get("messageRate"));
        assertEquals(10.0, ((Number) entity.get("pauseP50Ms")).doubleValue());
        assertEquals(50L, entity.get("pauseMaxMs"));
        assertEquals(5L, entity.get("slaViolations10ms"));
    }

    @Test
    @DisplayName("Should reset GC stats")
    void shouldResetGCStats() {
        Response response = resource.resetStats();

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        verify(gcStatsService).resetStats();
    }
}
