package fish.payara.trader.dto;

/**
 * DTO for the trading desk frontend risk metrics display.
 */
public record TradingRiskMetrics(double valueAtRisk95, double totalExposure, double netDelta, double maxDrawdown, StressTestMetrics stressTests) {

    public record StressTestMetrics(double flashCrash, double volatilitySpike) {
    }

    public static TradingRiskMetrics empty() {
        return new TradingRiskMetrics(0, 0, 0, 0, new StressTestMetrics(0, 0));
    }
}
