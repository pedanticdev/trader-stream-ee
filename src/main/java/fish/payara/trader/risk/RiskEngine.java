package fish.payara.trader.risk;

import fish.payara.trader.analysis.BarAggregator;
import fish.payara.trader.risk.model.ExposureSummary;
import fish.payara.trader.risk.model.Position;
import fish.payara.trader.risk.model.RiskSnapshot;
import fish.payara.trader.risk.model.StressResult;
import fish.payara.trader.risk.model.StressScenario;
import fish.payara.trader.risk.model.StressScenario.CorrelationBreakdown;
import fish.payara.trader.risk.model.StressScenario.FlashCrash;
import fish.payara.trader.risk.model.StressScenario.InterestRateShock;
import fish.payara.trader.risk.model.StressScenario.LiquidityFreeze;
import fish.payara.trader.risk.model.StressScenario.VolatilitySpike;
import fish.payara.trader.risk.model.VarResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.ta4j.core.BarSeries;

/**
 * Central risk engine computing per-symbol risk snapshots, portfolio-level exposure, Value at Risk (historical and parametric), and stress tests.
 */
@ApplicationScoped
public class RiskEngine {

    private static final Logger LOGGER = Logger.getLogger(RiskEngine.class.getName());

    @Inject
    private PositionTracker positionTracker;

    @Inject
    private BarAggregator barAggregator;

    @Inject
    private RiskConfig config;

    /**
     * Computes a per-symbol risk snapshot.
     */
    public RiskSnapshot getRiskSnapshot(String symbol) {
        Position position = positionTracker.getPosition(symbol);
        double currentPrice = getLastPrice(symbol);
        double notional = position.notionalValue(currentPrice);
        double unrealizedPnl = position.unrealizedPnl(currentPrice);
        double limit = positionTracker.getPositionLimit(symbol);
        double utilization = limit > 0 ? (notional / limit) * 100.0 : 0.0;

        return new RiskSnapshot(symbol, position, currentPrice, notional, unrealizedPnl, limit, utilization);
    }

    /**
     * Aggregates portfolio-level exposure across all positions.
     */
    public ExposureSummary getExposureSummary() {
        Map<String, Position> allPositions = positionTracker.getAllPositions();
        double totalNotional = 0.0;
        double longExposure = 0.0;
        double shortExposure = 0.0;
        double netDelta = 0.0;

        for (Map.Entry<String, Position> entry : allPositions.entrySet()) {
            Position pos = entry.getValue();
            double price = getLastPrice(entry.getKey());
            double notional = pos.notionalValue(price);

            totalNotional += notional;
            netDelta += pos.delta();

            if (pos.isLong()) {
                longExposure += notional;
            } else if (pos.isShort()) {
                shortExposure += notional;
            }
        }

        return new ExposureSummary(totalNotional, netDelta, longExposure + shortExposure, longExposure, shortExposure, allPositions.size());
    }

    /**
     * Historical VaR: sort portfolio returns, pick the percentile.
     */
    public VarResult calculateHistoricalVaR() {
        double[] returns = computePortfolioReturns();

        if (returns.length < 2) {
            return new VarResult("historical", 0.0, config.varConfidence(), 0, System.currentTimeMillis());
        }

        Arrays.sort(returns);

        int index = (int) Math.ceil((1.0 - config.varConfidence()) * returns.length) - 1;
        index = Math.max(0, Math.min(index, returns.length - 1));

        double varReturn = returns[index];
        double portfolioValue = getPortfolioValue();
        double varValue = Math.abs(varReturn * portfolioValue);

        return new VarResult("historical", varValue, config.varConfidence(), config.varLookbackBars(), System.currentTimeMillis());
    }

    /**
     * Parametric VaR: mean - z * sigma (normal distribution).
     */
    public VarResult calculateParametricVaR() {
        double[] returns = computePortfolioReturns();

        if (returns.length < 2) {
            return new VarResult("parametric", 0.0, config.varConfidence(), 0, System.currentTimeMillis());
        }

        double mean = 0.0;
        for (double r : returns) {
            mean += r;
        }
        mean /= returns.length;

        double variance = 0.0;
        for (double r : returns) {
            variance += (r - mean) * (r - mean);
        }
        variance /= (returns.length - 1);
        double stddev = Math.sqrt(variance);

        double z = inverseNormalCdf(config.varConfidence());
        double portfolioValue = getPortfolioValue();
        double varValue = portfolioValue * Math.abs(mean - z * stddev);

        return new VarResult("parametric", varValue, config.varConfidence(), config.varLookbackBars(), System.currentTimeMillis());
    }

    /**
     * Runs a single stress test scenario across all positions.
     */
    public StressResult runStressTest(StressScenario scenario) {
        Map<String, Position> allPositions = positionTracker.getAllPositions();
        double totalImpact = 0.0;
        int breached = 0;
        double maxLoss = 0.0;
        List<String> breachDetails = new ArrayList<>();

        for (Map.Entry<String, Position> entry : allPositions.entrySet()) {
            Position pos = entry.getValue();
            double price = getLastPrice(entry.getKey());
            double impact = scenario.apply(pos, price);

            totalImpact += impact;

            if (impact < 0) {
                double loss = Math.abs(impact);
                if (loss > maxLoss) {
                    maxLoss = loss;
                }
                double limit = positionTracker.getPositionLimit(entry.getKey());
                double utilization = limit > 0 ? (pos.notionalValue(price) / limit) * 100.0 : 0.0;
                if (utilization > 80.0) {
                    breached++;
                    breachDetails.add("%s: %.2f loss, %.1f%% utilized".formatted(entry.getKey(), loss, utilization));
                }
            }
        }

        return new StressResult(scenario.name(), totalImpact, breached, maxLoss, breachDetails);
    }

    /**
     * Runs all built-in stress scenarios.
     */
    public List<StressResult> runAllStressTests() {
        List<StressScenario> scenarios = List.of(new FlashCrash(config.stressCrashPercent()), new VolatilitySpike(config.stressVolSpike()),
                        new LiquidityFreeze(config.stressLiquidityWiden()), new CorrelationBreakdown(30.0), new InterestRateShock(50.0));

        List<StressResult> results = new ArrayList<>();
        for (StressScenario scenario : scenarios) {
            results.add(runStressTest(scenario));
        }
        return results;
    }

    /**
     * Checks whether the additional quantity at the given price would breach the limit.
     */
    public boolean wouldBreachLimit(String symbol, long additionalQty, double price) {
        Position pos = positionTracker.getPosition(symbol);
        double limit = positionTracker.getPositionLimit(symbol);
        double newNotional = (Math.abs(pos.quantity()) + Math.abs(additionalQty)) * price;
        return newNotional > limit;
    }

    /**
     * Delegates to PositionTracker for per-symbol limit updates.
     */
    public void updatePositionLimit(String symbol, double limit) {
        positionTracker.updatePositionLimit(symbol, limit);
    }

    private double getLastPrice(String symbol) {
        BarSeries series = barAggregator.getSeries(symbol);
        if (series.isEmpty()) {
            return 0.0;
        }
        return series.getLastBar().getClosePrice().doubleValue();
    }

    private double getPortfolioValue() {
        double value = 0.0;
        Map<String, Position> allPositions = positionTracker.getAllPositions();
        for (Map.Entry<String, Position> entry : allPositions.entrySet()) {
            value += entry.getValue().notionalValue(getLastPrice(entry.getKey()));
        }
        return value;
    }

    /**
     * Computes equal-weighted portfolio returns from bar data. Returns a single array of portfolio returns over time.
     */
    private double[] computePortfolioReturns() {
        Map<String, Position> allPositions = positionTracker.getAllPositions();
        List<String> symbols = new ArrayList<>(allPositions.keySet());

        if (symbols.isEmpty()) {
            return new double[0];
        }

        int lookback = config.varLookbackBars();
        int minBars = Integer.MAX_VALUE;

        for (String symbol : symbols) {
            BarSeries series = barAggregator.getSeries(symbol);
            int bars = Math.min(series.getBarCount(), lookback);
            minBars = Math.min(minBars, bars);
        }

        if (minBars < 2) {
            return new double[0];
        }

        int numReturns = minBars - 1;
        double[] portfolioReturns = new double[numReturns];

        for (int t = 0; t < numReturns; t++) {
            double portfolioReturn = 0.0;
            int count = 0;

            for (String symbol : symbols) {
                BarSeries series = barAggregator.getSeries(symbol);
                int baseIndex = series.getEndIndex() - minBars + 1;

                double prevPrice = series.getBar(baseIndex + t).getClosePrice().doubleValue();
                double currPrice = series.getBar(baseIndex + t + 1).getClosePrice().doubleValue();

                if (prevPrice > 0) {
                    portfolioReturn += (currPrice - prevPrice) / prevPrice;
                    count++;
                }
            }

            if (count > 0) {
                portfolioReturns[t] = portfolioReturn / count;
            }
        }

        return portfolioReturns;
    }

    /**
     * Approximate inverse of the standard normal CDF using the rational approximation. Used for parametric VaR z-score computation.
     */
    private static double inverseNormalCdf(double p) {
        if (p <= 0.0 || p >= 1.0) {
            throw new IllegalArgumentException("p must be in (0, 1), got: " + p);
        }
        if (p < 0.5) {
            return -inverseNormalCdf(1.0 - p);
        }
        double[] a = {0.0, 0.254829592, -0.284496736, 1.421413741, -1.453152027, 1.061405429, 0.3275911};
        double t = 1.0 / (1.0 + a[1] * Math.sqrt(-2.0 * Math.log(1.0 - p)));
        double y = 1.0 - (((((a[5] * t + a[4]) * t) + a[3]) * t + a[2]) * t + a[1]) * t * Math.exp(-t * t / 2.0);
        return y;
    }
}
