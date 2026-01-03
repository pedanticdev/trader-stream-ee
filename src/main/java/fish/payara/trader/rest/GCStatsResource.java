package fish.payara.trader.rest;

import fish.payara.trader.aeron.MarketDataPublisher;
import fish.payara.trader.gc.GCStats;
import fish.payara.trader.gc.GCStatsService;
import fish.payara.trader.monitoring.GCPauseMonitor;
import fish.payara.trader.monitoring.SLAMonitorService;
import fish.payara.trader.pressure.MemoryPressureService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/** REST endpoint for GC statistics monitoring */
@Path("/gc")
public class GCStatsResource {

    private static final Logger LOGGER = Logger.getLogger(GCStatsResource.class.getName());

    @Inject
    private GCStatsService gcStatsService;

    @Inject
    private MemoryPressureService memoryPressureService;

    @Inject
    private MarketDataPublisher publisher;

    @Inject
    private SLAMonitorService slaMonitor;

    @Inject
    private GCPauseMonitor gcPauseMonitor;

    @GET
    @Path("/sla")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSLAStats() {
        return Response.ok(slaMonitor.getStats()).build();
    }

    @POST
    @Path("/sla/reset")
    public Response resetSLAStats() {
        slaMonitor.reset();
        return Response.ok(Map.of("status", "reset")).build();
    }

    @GET
    @Path("/pauses")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getGCPauseStats() {
        return Response.ok(gcPauseMonitor.getStats()).build();
    }

    @POST
    @Path("/pauses/reset")
    public Response resetGCPauseStats() {
        gcPauseMonitor.reset();
        return Response.ok(Map.of("status", "reset")).build();
    }

    @GET
    @Path("/comparison")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getComparison() {
        Map<String, Object> comparison = new HashMap<>();

        // Identify which instance is responding
        String instanceName = System.getenv("PAYARA_INSTANCE_NAME");
        if (instanceName == null) {
            instanceName = "standalone";
        }
        comparison.put("instanceName", instanceName);

        String jvmVendor = System.getProperty("java.vm.vendor");
        String jvmName = System.getProperty("java.vm.name");
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        String gcName = gcBeans.stream().map(GarbageCollectorMXBean::getName).collect(Collectors.joining(", "));

        boolean isAzulC4 = gcName.toLowerCase().contains("c4") || jvmName.toLowerCase().contains("zing");

        comparison.put("jvmVendor", jvmVendor);
        comparison.put("jvmName", jvmName);
        comparison.put("gcCollectors", gcName);
        comparison.put("isAzulC4", isAzulC4);
        comparison.put("heapSizeMB", Runtime.getRuntime().maxMemory() / (1024 * 1024));
        comparison.put("allocationMode", memoryPressureService.getCurrentMode());
        comparison.put("allocationRateMBps", memoryPressureService.getCurrentMode().getAllocationRateMBPerSec());
        comparison.put("messageRate", publisher.getMessagesPublished());

        List<GCStats> stats = gcStatsService.collectGCStats();
        comparison.put("gcStats", stats);

        fish.payara.trader.monitoring.GCPauseMonitor.GCPauseStats pauseStats = gcPauseMonitor.getStats();
        comparison.put("pauseP50Ms", pauseStats.p50Ms);
        comparison.put("pauseP95Ms", pauseStats.p95Ms);
        comparison.put("pauseP99Ms", pauseStats.p99Ms);
        comparison.put("pauseP999Ms", pauseStats.p999Ms);
        comparison.put("pauseMaxMs", pauseStats.maxMs); // All-time max
        comparison.put("pauseAvgMs", pauseStats.avgPauseMs);
        comparison.put("totalPauseCount", pauseStats.totalPauseCount);
        comparison.put("totalPauseTimeMs", pauseStats.totalPauseTimeMs);

        comparison.put("slaViolations10ms", pauseStats.violationsOver10ms);
        comparison.put("slaViolations50ms", pauseStats.violationsOver50ms);
        comparison.put("slaViolations100ms", pauseStats.violationsOver100ms);
        comparison.put("pauseSampleSize", pauseStats.sampleSize);

        return Response.ok(comparison).build();
    }

    @GET
    @Path("/stats")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getGCStats() {
        List<GCStats> stats = gcStatsService.collectGCStats();
        LOGGER.info(String.format("GET /api/gc/stats - Returned %d GC collector stats", stats.size()));
        return Response.ok(stats).build();
    }

    @POST
    @Path("/reset")
    @Produces(MediaType.APPLICATION_JSON)
    public Response resetStats() {
        LOGGER.info("POST /api/gc/reset - Resetting GC statistics");
        gcStatsService.resetStats();
        return Response.ok().entity("{\"status\":\"reset\"}").build();
    }
}
