package fish.payara.trader.portfolio.model;

/**
 * Computed performance metrics for the portfolio.
 */
public record PerformanceMetrics(double totalPnl, double realizedPnl, double unrealizedPnl, double sharpeRatio, double maxDrawdown,
                double maxDrawdownDurationSeconds, double winRate, long totalTrades, long winningTrades, long losingTrades) {
}
