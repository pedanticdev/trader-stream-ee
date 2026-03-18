package fish.payara.trader.risk.model;

import java.util.List;

/**
 * Result of running a stress test scenario across all positions.
 */
public record StressResult(String scenarioName, double portfolioImpact, int positionsBreached, double maxPositionLoss, List<String> breachDetails) {
}
