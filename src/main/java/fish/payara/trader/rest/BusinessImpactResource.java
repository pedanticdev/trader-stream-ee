package fish.payara.trader.rest;

import fish.payara.trader.dto.BusinessImpactResponse;
import fish.payara.trader.impact.BusinessImpactCalculator;
import fish.payara.trader.impact.BusinessImpactConfig;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;
import java.util.HashMap;
import java.util.logging.Logger;

/**
 * REST endpoints for business impact calculations. Provides missed trades and revenue at risk metrics.
 */
@Path("/business")
public class BusinessImpactResource {

    private static final Logger LOGGER = Logger.getLogger(BusinessImpactResource.class.getName());

    @Inject
    private BusinessImpactCalculator calculator;

    @Inject
    private BusinessImpactConfig config;

    @GET
    @Path("/impact")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getImpact() {
        LOGGER.info("GET /api/business/impact - Calculating business impact");
        BusinessImpactResponse impact = calculator.calculateImpact();
        return Response.ok(impact).build();
    }

    @POST
    @Path("/reset")
    @Produces(MediaType.APPLICATION_JSON)
    public Response resetImpact() {
        LOGGER.info("POST /api/business/reset - Resetting impact calculations");
        calculator.reset();
        return Response.ok(Map.of("status", "reset")).build();
    }

    @GET
    @Path("/config")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getConfig() {
        LOGGER.info("GET /api/business/config - Getting business impact configuration");
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("tradeValue", config.tradeValue());
        configMap.put("currency", config.currency());
        configMap.put("windowSeconds", config.windowSeconds());
        configMap.put("instancesNeededC4", config.instancesNeededC4());
        configMap.put("instancesNeededG1", config.instancesNeededG1());
        return Response.ok(configMap).build();
    }
}
