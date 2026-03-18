package fish.payara.trader.rest;

import fish.payara.trader.matching.engine.MatchingEngine;
import fish.payara.trader.matching.model.Position;
import fish.payara.trader.matching.model.Price;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

@Path("/matching/positions")
public class MatchingPositionResource {

    private static final Logger LOGGER = Logger.getLogger(MatchingPositionResource.class.getName());

    @Inject
    private MatchingEngine matchingEngine;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllPositions() {
        LOGGER.info("GET /api/matching/positions");
        Map<String, Position> positions = matchingEngine.getPositions();
        return Response.ok(positions).build();
    }

    @GET
    @Path("/{symbol}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPosition(@PathParam("symbol") String symbol) {
        LOGGER.info("GET /api/matching/positions/" + symbol);
        Optional<Position> position = matchingEngine.getPosition(symbol);
        return Response.ok(position.orElse(new Position(symbol, 0, Price.ZERO, 0, Price.ZERO, 0))).build();
    }
}
