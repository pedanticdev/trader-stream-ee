package fish.payara.trader.rest;

import fish.payara.trader.matching.engine.MatchingEngine;
import fish.payara.trader.matching.history.ExecutionHistoryQuery;
import fish.payara.trader.matching.model.Execution;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.logging.Logger;

@Path("/matching/executions")
public class ExecutionResource {

    private static final Logger LOGGER = Logger.getLogger(ExecutionResource.class.getName());

    @Inject
    private MatchingEngine matchingEngine;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getExecutions(@QueryParam("symbol") String symbol, @QueryParam("limit") @DefaultValue("100") int limit) {
        LOGGER.info("GET /api/matching/executions - symbol=" + symbol + ", limit=" + limit);
        ExecutionHistoryQuery query = new ExecutionHistoryQuery(symbol, null, null, limit);
        List<Execution> executions = matchingEngine.getExecutions(query);
        return Response.ok(executions).build();
    }
}
