package fish.payara.trader.rest;

import com.hazelcast.cluster.Member;
import com.hazelcast.core.HazelcastInstance;
import fish.payara.trader.aeron.AeronSubscriberBean;
import fish.payara.trader.aeron.MarketDataPublisher;
import fish.payara.trader.websocket.MarketDataBroadcaster;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST endpoint for monitoring system status
 */
@Path("/status")
public class StatusResource {

    @Inject
    private AeronSubscriberBean subscriber;

    @Inject
    private MarketDataPublisher publisher;

    @Inject
    private MarketDataBroadcaster broadcaster;

    @Inject
    private HazelcastInstance hazelcastInstance;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStatus() {
        Map<String, Object> status = new HashMap<>();

        // Get instance name from environment variable
        String instanceName = System.getenv("PAYARA_INSTANCE_NAME");
        if (instanceName == null) {
            instanceName = "standalone";
        }

        status.put("application", "TradeStreamEE");
        status.put("description", "High-frequency trading dashboard with Aeron and SBE");
        status.put("instance", instanceName);
        status.put("subscriber", subscriber.getStatus());

        // Include both local and cluster-wide message counts
        Map<String, Object> publisherStats = new HashMap<>();
        publisherStats.put("localMessagesPublished", publisher.getMessagesPublished());
        publisherStats.put("clusterMessagesPublished", publisher.getClusterMessagesPublished());
        status.put("publisher", publisherStats);

        status.put("websocket", Map.of(
            "activeSessions", broadcaster.getSessionCount()
        ));
        status.put("status", "UP");

        return Response.ok(status).build();
    }

    /**
     * Get cluster status and membership information
     */
    @GET
    @Path("/cluster")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getClusterStatus() {
        Map<String, Object> clusterInfo = new HashMap<>();

        try {
            if (hazelcastInstance == null) {
                clusterInfo.put("clustered", false);
                clusterInfo.put("message", "Running in standalone mode (Hazelcast not available)");
                return Response.ok(clusterInfo).build();
            }

            clusterInfo.put("clustered", true);
            clusterInfo.put("clusterSize", hazelcastInstance.getCluster().getMembers().size());
            clusterInfo.put("clusterTime", hazelcastInstance.getCluster().getClusterTime());
            clusterInfo.put("localMemberUuid", hazelcastInstance.getCluster().getLocalMember().getUuid().toString());

            clusterInfo.put("members", hazelcastInstance.getCluster().getMembers().stream()
                .map(member -> Map.of(
                    "address", member.getAddress().toString(),
                    "uuid", member.getUuid().toString(),
                    "localMember", member.localMember(),
                    "liteMember", member.isLiteMember()
                ))
                .collect(Collectors.toList())
            );

            return Response.ok(clusterInfo).build();

        } catch (Exception e) {
            clusterInfo.put("clustered", false);
            clusterInfo.put("error", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(clusterInfo)
                .build();
        }
    }
}
