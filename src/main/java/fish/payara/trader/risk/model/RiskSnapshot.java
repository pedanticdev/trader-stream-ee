package fish.payara.trader.risk.model;

/**
 * Per-symbol risk snapshot combining position, price, and limit utilization.
 */
public record RiskSnapshot(String symbol, Position position, double currentPrice, double notionalValue, double unrealizedPnl, double maxPositionLimit,
                double utilizationPercent) {
}
