package fish.payara.trader.rest;

import fish.payara.trader.dto.PortfolioResponse;
import fish.payara.trader.portfolio.PortfolioService;
import fish.payara.trader.portfolio.model.PerformanceMetrics;
import fish.payara.trader.portfolio.model.PortfolioSnapshot;
import fish.payara.trader.portfolio.model.RebalancePlan;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.logging.Logger;

@Path("/portfolio")
public class PortfolioResource {

    private static final Logger LOGGER = Logger.getLogger(PortfolioResource.class.getName());

    @Inject
    private PortfolioService portfolioService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPortfolio() {
        LOGGER.info("GET /api/portfolio");
        PortfolioSnapshot snapshot = portfolioService.getSnapshot();
        PerformanceMetrics metrics = portfolioService.calculateMetrics();
        RebalancePlan plan = portfolioService.generateRebalancePlan(Map.of());
        PortfolioResponse response = new PortfolioResponse(snapshot, metrics, plan);
        return Response.ok(response).build();
    }

    @GET
    @Path("/metrics")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMetrics() {
        LOGGER.info("GET /api/portfolio/metrics");
        PerformanceMetrics metrics = portfolioService.calculateMetrics();
        return Response.ok(metrics).build();
    }

    @GET
    @Path("/snapshot")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSnapshot() {
        LOGGER.info("GET /api/portfolio/snapshot");
        PortfolioSnapshot snapshot = portfolioService.getSnapshot();
        return Response.ok(snapshot).build();
    }

    @POST
    @Path("/rebalance")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getRebalancePlan(Map<String, Double> targetWeights) {
        LOGGER.info("POST /api/portfolio/rebalance");
        RebalancePlan plan = portfolioService.generateRebalancePlan(targetWeights);
        return Response.ok(plan).build();
    }

    @POST
    @Path("/reset")
    @Produces(MediaType.APPLICATION_JSON)
    public Response reset() {
        LOGGER.info("POST /api/portfolio/reset");
        portfolioService.reset();
        return Response.ok(Map.of("status", "reset")).build();
    }
}
