package fish.payara.trader.risk.model;

/**
 * Value at Risk result from either historical or parametric computation.
 */
public record VarResult(String method, double varValue, double confidenceLevel, int lookbackBars, long timestamp) {
}
