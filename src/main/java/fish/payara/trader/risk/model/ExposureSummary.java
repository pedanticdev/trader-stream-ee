package fish.payara.trader.risk.model;

/**
 * Portfolio-level exposure aggregation across all positions.
 */
public record ExposureSummary(double totalNotional, double netDelta, double grossExposure, double longExposure, double shortExposure, int symbolCount) {
}
