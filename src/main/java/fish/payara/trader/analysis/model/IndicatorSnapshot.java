package fish.payara.trader.analysis.model;

import java.util.Map;

/**
 * Snapshot of computed indicator values for a symbol at a point in time. Values are keyed by indicator label (e.g. "SMA(20)", "RSI(14)").
 */
public record IndicatorSnapshot(String symbol, long timestamp, double lastPrice, Map<String, Double> values) {

    /**
     * Returns the indicator value for the given name, or Double.NaN if absent.
     */
    public double getValue(String name) {
        return values.getOrDefault(name, Double.NaN);
    }
}
