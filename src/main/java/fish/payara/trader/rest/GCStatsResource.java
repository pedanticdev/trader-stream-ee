package fish.payara.trader.rest;

import fish.payara.trader.gc.GCStats;
import fish.payara.trader.gc.GCStatsService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.logging.Logger;

/**
 * REST endpoint for GC statistics monitoring
 */
@Path("/gc")
public class GCStatsResource {

    private static final Logger LOGGER = Logger.getLogger(GCStatsResource.class.getName());

    @Inject
    private GCStatsService gcStatsService;

    @GET
    @Path("/stats")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getGCStats() {
        LOGGER.fine("GET /api/gc/stats - Collecting GC statistics");
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
