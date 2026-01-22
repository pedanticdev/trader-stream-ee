package fish.payara.trader.rest;

import com.hazelcast.core.HazelcastInstance;
import fish.payara.trader.aeron.MarketDataPublisher;
import fish.payara.trader.monitoring.GCPauseMonitor;
import fish.payara.trader.util.InstanceUtils;
import fish.payara.trader.util.InstanceUtils.JvmMetadata;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.util.HashMap;
import java.util.Map;

/**
 * REST endpoint for health checks and pre-demo validation. Ensures all components are operational before starting a live demo.
 */
@Path("/health")
public class HealthResource {

    @Inject
    private MarketDataPublisher publisher;

    @Inject
    private GCPauseMonitor gcPauseMonitor;

    @Inject
    private HazelcastInstance hazelcastInstance;

    @GET
    @Path("/check")
    @Produces(MediaType.APPLICATION_JSON)
    public Response healthCheck() {
        Map<String, Object> health = new HashMap<>();
        boolean allHealthy = true;

        boolean publisherHealthy = publisher != null && publisher.isRunning();
        health.put("publisher", publisherHealthy ? "healthy" : "unhealthy");
        if (!publisherHealthy) {
            allHealthy = false;
        }

        boolean gcMonitorHealthy = gcPauseMonitor != null;
        health.put("gcMonitor", gcMonitorHealthy ? "healthy" : "unhealthy");
        if (!gcMonitorHealthy) {
            allHealthy = false;
        }

        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
        long heapMax = memoryBean.getHeapMemoryUsage().getMax();
        double heapPercent = (heapUsed * 100.0) / heapMax;
        boolean memoryHealthy = heapPercent < 90;
        health.put("memory", memoryHealthy ? "healthy" : "warning");
        health.put("memoryUsedPercent", String.format("%.1f", heapPercent));
        if (!memoryHealthy) {
            allHealthy = false;
        }

        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        health.put("availableProcessors", osBean.getAvailableProcessors());

        JvmMetadata jvm = InstanceUtils.getJvmMetadata();
        health.put("jvmVendor", jvm.vendor());
        health.put("jvmName", jvm.name());
        health.put("gcCollectors", jvm.gcCollectors());
        health.put("javaVersion", System.getProperty("java.version"));

        boolean clusterMode = hazelcastInstance != null && hazelcastInstance.getCluster().getMembers().size() > 1;
        health.put("clusterMode", clusterMode);

        health.put("status", allHealthy ? "healthy" : "unhealthy");
        health.put("readyForDemo", allHealthy);

        int status = allHealthy ? Response.Status.OK.getStatusCode() : Response.Status.SERVICE_UNAVAILABLE.getStatusCode();
        return Response.status(status).entity(health).build();
    }

    @GET
    @Path("/ready")
    @Produces(MediaType.APPLICATION_JSON)
    public Response readiness() {
        Map<String, Object> ready = new HashMap<>();

        boolean publisherRunning = publisher != null && publisher.isRunning();
        ready.put("publisher", publisherRunning);
        ready.put("ready", publisherRunning);

        return Response.ok(ready).build();
    }

    @GET
    @Path("/live")
    @Produces(MediaType.APPLICATION_JSON)
    public Response liveness() {
        Map<String, Object> live = new HashMap<>();
        live.put("status", "alive");
        live.put("uptime", ManagementFactory.getRuntimeMXBean().getUptime());
        return Response.ok(live).build();
    }
}
