package fish.payara.trader.portfolio.model;

import java.util.Map;

/**
 * Rebalance plan comparing current weights to target weights with actions.
 */
public record RebalancePlan(long timestamp, Map<String, Double> targetWeights, Map<String, String> actions) {
}
