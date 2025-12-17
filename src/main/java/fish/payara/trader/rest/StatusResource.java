package fish.payara.trader.rest;

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

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStatus() {
        Map<String, Object> status = new HashMap<>();

        status.put("application", "TradeStreamEE");
        status.put("description", "High-frequency trading dashboard with Aeron and SBE");
        status.put("subscriber", subscriber.getStatus());
        status.put("publisher", Map.of(
            "messagesPublished", publisher.getMessagesPublished()
        ));
        status.put("websocket", Map.of(
            "activeSessions", broadcaster.getSessionCount()
        ));
        status.put("status", "UP");

        return Response.ok(status).build();
    }
}
