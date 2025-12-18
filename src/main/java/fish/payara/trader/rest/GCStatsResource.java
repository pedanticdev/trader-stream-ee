package fish.payara.trader.rest;

import fish.payara.trader.gc.GCStats;
import fish.payara.trader.gc.GCStatsService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * REST endpoint for GC statistics monitoring
 */
@Path("/gc")
public class GCStatsResource {

    @Inject
    private GCStatsService gcStatsService;

    @GET
    @Path("/stats")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getGCStats() {
        List<GCStats> stats = gcStatsService.collectGCStats();
        return Response.ok(stats).build();
    }

    @POST
    @Path("/reset")
    @Produces(MediaType.APPLICATION_JSON)
    public Response resetStats() {
        gcStatsService.resetStats();
        return Response.ok().entity("{\"status\":\"reset\"}").build();
    }
}
