package fish.payara.trader.rest;

import fish.payara.trader.aeron.MarketDataPublisher;
import fish.payara.trader.dto.GCComparisonResponse;
import fish.payara.trader.gc.GCStats;
import fish.payara.trader.gc.GCStatsService;
import fish.payara.trader.monitoring.GCPauseMonitor;
import fish.payara.trader.monitoring.SLAMonitorService;
import fish.payara.trader.pressure.AllocationMode;
import fish.payara.trader.pressure.MemoryPressureService;
import fish.payara.trader.util.InstanceUtils;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
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
        String instanceName = InstanceUtils.getInstanceName();

        String jvmVendor = System.getProperty("java.vm.vendor");
        String jvmName = System.getProperty("java.vm.name");
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        String gcName = gcBeans.stream().map(GarbageCollectorMXBean::getName).collect(Collectors.joining(", "));

        boolean isAzulC4 = gcName.toLowerCase().contains("c4") || jvmName.toLowerCase().contains("zing");

        AllocationMode currentMode = memoryPressureService.getCurrentMode();
        List<GCStats> gcStats = gcStatsService.collectGCStats();
        GCPauseMonitor.GCPauseStats pauseStats = gcPauseMonitor.getStats();

        GCComparisonResponse response = GCComparisonResponse.from(instanceName, jvmVendor, jvmName, gcName, isAzulC4,
                        Runtime.getRuntime().maxMemory() / (1024 * 1024), currentMode.name(), currentMode.getAllocationRateMBPerSec(),
                        publisher.getMessagesPublished(), gcStats, pauseStats);

        return Response.ok(response).build();
    }

    @GET
    @Path("/stats")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getGCStats() {
        List<GCStats> stats = gcStatsService.collectGCStats();
        LOGGER.info("GET /api/gc/stats - Returned " + stats.size() + " GC collector stats");
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
