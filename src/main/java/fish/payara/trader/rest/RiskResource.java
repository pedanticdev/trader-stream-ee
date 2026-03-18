package fish.payara.trader.rest;

import fish.payara.trader.dto.RiskResponse;
import fish.payara.trader.dto.TradingRiskMetrics;
import fish.payara.trader.risk.RiskEngine;
import fish.payara.trader.risk.model.ExposureSummary;
import fish.payara.trader.risk.model.RiskSnapshot;
import fish.payara.trader.risk.model.StressResult;
import fish.payara.trader.risk.model.VarResult;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@Path("/risk")
public class RiskResource {

    private static final Logger LOGGER = Logger.getLogger(RiskResource.class.getName());

    private static final String[] SYMBOLS = {"AAPL", "GOOGL", "MSFT", "AMZN", "TSLA", "NVDA", "META", "NFLX"};

    @Inject
    private RiskEngine riskEngine;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getFullRiskSnapshot() {
        LOGGER.info("GET /api/risk");
        List<RiskSnapshot> positions = new ArrayList<>();
        for (String symbol : SYMBOLS) {
            positions.add(riskEngine.getRiskSnapshot(symbol));
        }
        ExposureSummary exposure = riskEngine.getExposureSummary();
        VarResult historicalVar = riskEngine.calculateHistoricalVaR();
        VarResult parametricVar = riskEngine.calculateParametricVaR();
        List<StressResult> stressResults = riskEngine.runAllStressTests();

        RiskResponse response = new RiskResponse(positions, exposure, historicalVar, parametricVar, stressResults);
        return Response.ok(response).build();
    }

    @GET
    @Path("/metrics")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTradingMetrics() {
        LOGGER.info("GET /api/risk/metrics");

        try {
            ExposureSummary exposure = riskEngine.getExposureSummary();
            VarResult varResult = riskEngine.calculateHistoricalVaR();
            List<StressResult> stressResults = riskEngine.runAllStressTests();

            double var95 = varResult != null ? varResult.varValue() : 0;
            double totalExposure = exposure != null ? exposure.grossExposure() : 0;
            double netDelta = exposure != null ? exposure.netDelta() : 0;

            // Calculate max drawdown (simplified - use VaR as proxy)
            double maxDrawdown = var95 > 0 ? var95 / Math.max(totalExposure, 1) : 0;

            // Extract stress test impacts
            double flashCrash = 0;
            double volSpike = 0;
            if (stressResults != null) {
                for (StressResult sr : stressResults) {
                    if (sr.scenarioName() != null) {
                        if (sr.scenarioName().toLowerCase().contains("flash") || sr.scenarioName().toLowerCase().contains("crash")) {
                            flashCrash = sr.portfolioImpact();
                        }
                        if (sr.scenarioName().toLowerCase().contains("vol") || sr.scenarioName().toLowerCase().contains("spike")) {
                            volSpike = sr.portfolioImpact();
                        }
                    }
                }
            }

            TradingRiskMetrics metrics = new TradingRiskMetrics(var95, totalExposure, netDelta, maxDrawdown,
                            new TradingRiskMetrics.StressTestMetrics(flashCrash, volSpike));

            return Response.ok(metrics).build();
        } catch (Exception e) {
            LOGGER.warning("Error calculating risk metrics: " + e.getMessage());
            return Response.ok(TradingRiskMetrics.empty()).build();
        }
    }

    @GET
    @Path("/var")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getVaR() {
        LOGGER.info("GET /api/risk/var");
        VarResult historical = riskEngine.calculateHistoricalVaR();
        VarResult parametric = riskEngine.calculateParametricVaR();
        return Response.ok(Map.of("historical", historical, "parametric", parametric)).build();
    }

    @GET
    @Path("/stress")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStressTestResults() {
        LOGGER.info("GET /api/risk/stress");
        List<StressResult> results = riskEngine.runAllStressTests();
        return Response.ok(results).build();
    }

    @GET
    @Path("/exposure")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getExposure() {
        LOGGER.info("GET /api/risk/exposure");
        ExposureSummary exposure = riskEngine.getExposureSummary();
        return Response.ok(exposure).build();
    }

    @GET
    @Path("/{symbol}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRiskForSymbol(@PathParam("symbol") String symbol) {
        LOGGER.info("GET /api/risk/" + symbol);
        RiskSnapshot snapshot = riskEngine.getRiskSnapshot(symbol);
        return Response.ok(snapshot).build();
    }

    @POST
    @Path("/limits/{symbol}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updatePositionLimit(@PathParam("symbol") String symbol, Map<String, Double> body) {
        double limit = body.getOrDefault("limit", 10000.0);
        LOGGER.info("POST /api/risk/limits/" + symbol + " - Setting limit to " + limit);
        riskEngine.updatePositionLimit(symbol, limit);
        return Response.ok(Map.of("symbol", symbol, "limit", limit)).build();
    }
}
