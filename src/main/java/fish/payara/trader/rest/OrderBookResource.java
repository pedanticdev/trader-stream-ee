package fish.payara.trader.rest;

import fish.payara.trader.matching.engine.MatchingEngine;
import fish.payara.trader.matching.model.OrderBookSnapshot;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Collections;
import java.util.Optional;
import java.util.logging.Logger;

@Path("/matching/order-book")
public class OrderBookResource {

    private static final Logger LOGGER = Logger.getLogger(OrderBookResource.class.getName());

    @Inject
    private MatchingEngine matchingEngine;

    @GET
    @Path("/{symbol}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getOrderBook(@PathParam("symbol") String symbol, @QueryParam("depth") @DefaultValue("10") int depth) {
        LOGGER.info("GET /api/matching/order-book/" + symbol + "?depth=" + depth);
        Optional<OrderBookSnapshot> snapshot = matchingEngine.getBook(symbol);
        if (snapshot.isEmpty()) {
            OrderBookSnapshot empty = new OrderBookSnapshot(symbol, System.currentTimeMillis(), Collections.emptyList(), Collections.emptyList(), 0, 0);
            return Response.ok(empty).build();
        }
        return Response.ok(snapshot.get()).build();
    }
}
