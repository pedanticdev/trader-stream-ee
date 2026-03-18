package fish.payara.trader.dto;

import fish.payara.trader.portfolio.model.PerformanceMetrics;
import fish.payara.trader.portfolio.model.PortfolioSnapshot;
import fish.payara.trader.portfolio.model.RebalancePlan;

/**
 * Aggregated portfolio response DTO.
 */
public record PortfolioResponse(PortfolioSnapshot snapshot, PerformanceMetrics metrics, RebalancePlan rebalancePlan) {
}
