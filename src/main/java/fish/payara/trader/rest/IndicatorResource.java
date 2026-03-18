package fish.payara.trader.rest;

import fish.payara.trader.analysis.IndicatorService;
import fish.payara.trader.analysis.model.IndicatorSnapshot;
import fish.payara.trader.dto.IndicatorResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@Path("/analysis")
public class IndicatorResource {

    private static final Logger LOGGER = Logger.getLogger(IndicatorResource.class.getName());

    @Inject
    private IndicatorService indicatorService;

    @GET
    @Path("/{symbol}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getIndicators(@PathParam("symbol") String symbol) {
        LOGGER.info("GET /api/analysis/" + symbol);
        IndicatorSnapshot snapshot = indicatorService.getSnapshot(symbol);
        if (snapshot == null) {
            return Response.status(Response.Status.NO_CONTENT).entity(Map.of("symbol", symbol, "error", "Insufficient data")).build();
        }
        IndicatorResponse response = new IndicatorResponse(snapshot.symbol(), snapshot.timestamp(), snapshot.values());
        return Response.ok(response).build();
    }

    @GET
    @Path("/{symbol}/history")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getHistoricalIndicators(@PathParam("symbol") String symbol, @QueryParam("bars") @DefaultValue("50") int bars) {
        LOGGER.info("GET /api/analysis/" + symbol + "/history?bars=" + bars);
        List<IndicatorSnapshot> snapshots = indicatorService.getHistoricalSnapshots(symbol, bars);
        if (snapshots.isEmpty()) {
            return Response.status(Response.Status.NO_CONTENT).entity(Map.of("symbol", symbol, "error", "Insufficient data")).build();
        }
        return Response.ok(snapshots).build();
    }
}
