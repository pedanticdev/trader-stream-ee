package fish.payara.trader.rest;

import fish.payara.trader.matching.engine.MatchingEngine;
import fish.payara.trader.matching.model.Order;
import fish.payara.trader.matching.model.OrderRequest;
import fish.payara.trader.matching.exception.OrderValidationException;
import fish.payara.trader.matching.exception.OrderRejectedException;
import fish.payara.trader.matching.book.CancelResult;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@Path("/matching/orders")
public class OrderResource {

    private static final Logger LOGGER = Logger.getLogger(OrderResource.class.getName());

    @Inject
    private MatchingEngine matchingEngine;

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response submitOrder(OrderRequest request) {
        try {
            LOGGER.info("POST /api/matching/orders - Submitting " + request.type() + " " + request.side() + " order for " + request.symbol());
            Order order = matchingEngine.submitOrder(request);
            return Response.ok(Map.of("status", "ACCEPTED", "orderId", order.orderId())).build();
        } catch (OrderValidationException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("status", "REJECTED", "reason", e.reason())).build();
        } catch (OrderRejectedException e) {
            return Response.status(422).entity(Map.of("status", "REJECTED", "reason", e.reason())).build();
        }
    }

    @DELETE
    @Path("/{orderId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response cancelOrder(@PathParam("orderId") long orderId) {
        LOGGER.info("DELETE /api/matching/orders/" + orderId);
        CancelResult result = matchingEngine.cancelOrder(orderId);
        if (!result.canceled()) {
            return Response.status(Response.Status.NOT_FOUND).entity(Map.of("status", "NOT_FOUND", "orderId", orderId, "reason", result.reason())).build();
        }
        return Response.ok(Map.of("status", "CANCELED", "orderId", orderId)).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getOrders(@QueryParam("symbol") String symbol) {
        LOGGER.info("GET /api/matching/orders - symbol=" + symbol);
        var book = matchingEngine.getBook(symbol);
        if (book.isEmpty()) {
            return Response.ok(Map.of("symbol", symbol, "levels", List.of())).build();
        }
        return Response.ok(Map.of("symbol", symbol, "snapshot", book.get())).build();
    }

    @GET
    @Path("/book/{symbol}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getOrderBook(@PathParam("symbol") String symbol) {
        LOGGER.info("GET /api/matching/orders/book/" + symbol);
        var snapshot = matchingEngine.getBook(symbol);
        return Response.ok(snapshot.orElse(null)).build();
    }
}
