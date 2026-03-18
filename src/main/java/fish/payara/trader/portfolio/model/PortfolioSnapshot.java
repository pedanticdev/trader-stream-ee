package fish.payara.trader.portfolio.model;

/**
 * Point-in-time snapshot of portfolio state.
 */
public record PortfolioSnapshot(long timestamp, double totalValue, double cashBalance, double marginAvailable, double marginUsed, int numPositions,
                double dailyPnl, double totalPnl) {
}
