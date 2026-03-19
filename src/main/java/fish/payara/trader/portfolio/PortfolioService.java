package fish.payara.trader.portfolio;

import fish.payara.trader.analysis.BarAggregator;
import fish.payara.trader.portfolio.model.PerformanceMetrics;
import fish.payara.trader.portfolio.model.PortfolioSnapshot;
import fish.payara.trader.portfolio.model.RebalancePlan;
import fish.payara.trader.risk.PositionTracker;
import fish.payara.trader.risk.model.Position;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.logging.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.ta4j.core.BarSeries;

/**
 * Portfolio management service. Tracks NAV history, computes performance metrics (Sharpe ratio, max drawdown, win rate), and generates rebalance plans.
 */
@ApplicationScoped
public class PortfolioService {

    private static final Logger LOGGER = Logger.getLogger(PortfolioService.class.getName());

    @Inject
    private PositionTracker positionTracker;

    @Inject
    private BarAggregator barAggregator;

    @Inject
    @ConfigProperty(name = "portfolio.initial.capital", defaultValue = "1000000")
    private double initialCapital;

    @Inject
    @ConfigProperty(name = "portfolio.risk.free.rate", defaultValue = "0.02")
    private double riskFreeRate;

    private final LinkedList<Double> navHistory = new LinkedList<>();
    private double cashBalance;
    private double lastNav;

    @jakarta.annotation.PostConstruct
    public void init() {
        cashBalance = initialCapital;
        lastNav = initialCapital;
        navHistory.add(initialCapital);
        LOGGER.info("PortfolioService initialized with capital: %.2f".formatted(initialCapital));
    }

    /**
     * Computes performance metrics from position history and NAV series.
     */
    public PerformanceMetrics calculateMetrics() {
        Map<String, Position> allPositions = positionTracker.getAllPositions();

        double totalPnl = 0.0;
        double realizedPnl = 0.0;
        double unrealizedPnl = 0.0;
        long totalTrades = 0;
        long winningTrades = 0;
        long losingTrades = 0;

        for (Map.Entry<String, Position> entry : allPositions.entrySet()) {
            Position pos = entry.getValue();
            realizedPnl += pos.realizedPnl();
            double price = getLastPrice(entry.getKey());
            unrealizedPnl += pos.unrealizedPnl(price);
            totalTrades += pos.tradeCount();

            if (pos.realizedPnl() > 0) {
                winningTrades += pos.tradeCount();
            } else if (pos.realizedPnl() < 0) {
                losingTrades += pos.tradeCount();
            }
        }

        totalPnl = realizedPnl + unrealizedPnl;
        double winRate = totalTrades > 0 ? (double) winningTrades / totalTrades : 0.0;

        double sharpe = computeSharpeRatio();
        double[] drawdown = computeMaxDrawdown();

        return new PerformanceMetrics(totalPnl, realizedPnl, unrealizedPnl, sharpe, drawdown[0], drawdown[1], winRate, totalTrades, winningTrades,
                        losingTrades);
    }

    /**
     * Returns a snapshot of the current portfolio state.
     */
    public PortfolioSnapshot getSnapshot() {
        double positionsValue = computePositionsValue();
        double totalValue = cashBalance + positionsValue;
        double marginUsed = positionsValue * 0.5;
        double marginAvailable = Math.max(0, totalValue - marginUsed);

        int numPositions = positionTracker.getAllPositions().size();

        // Record NAV for performance metrics (Sharpe ratio, max drawdown)
        if (navHistory.isEmpty() || Math.abs(totalValue - navHistory.getLast()) > 0.01) {
            navHistory.add(totalValue);
            if (navHistory.size() > 5000) {
                navHistory.removeFirst();
            }
            lastNav = totalValue;
        }

        double dailyPnl = 0.0;
        if (navHistory.size() >= 2) {
            double prevNav = navHistory.get(navHistory.size() - 2);
            dailyPnl = totalValue - prevNav;
        }

        double totalPnl = totalValue - initialCapital;

        return new PortfolioSnapshot(System.currentTimeMillis(), totalValue, cashBalance, marginAvailable, marginUsed, numPositions, dailyPnl, totalPnl);
    }

    /**
     * Generates a rebalance plan comparing current position weights to target weights. Actions indicate BUY, SELL, or HOLD for each symbol.
     */
    public RebalancePlan generateRebalancePlan(Map<String, Double> targetWeights) {
        Map<String, Double> currentWeights = computeCurrentWeights();
        Map<String, String> actions = new LinkedHashMap<>();
        double totalValue = computePositionsValue() + cashBalance;

        if (totalValue <= 0) {
            for (String symbol : targetWeights.keySet()) {
                actions.put(symbol, "HOLD");
            }
            return new RebalancePlan(System.currentTimeMillis(), targetWeights, actions);
        }

        for (Map.Entry<String, Double> entry : targetWeights.entrySet()) {
            String symbol = entry.getKey();
            double targetWeight = entry.getValue();
            double currentWeight = currentWeights.getOrDefault(symbol, 0.0);
            double diff = targetWeight - currentWeight;

            if (Math.abs(diff) < 0.01) {
                actions.put(symbol, "HOLD");
            } else if (diff > 0) {
                actions.put(symbol, "BUY %.1f%%".formatted(diff * 100));
            } else {
                actions.put(symbol, "SELL %.1f%%".formatted(Math.abs(diff) * 100));
            }
        }

        for (String symbol : currentWeights.keySet()) {
            if (!targetWeights.containsKey(symbol) && currentWeights.get(symbol) > 0.01) {
                actions.put(symbol, "CLOSE");
            }
        }

        return new RebalancePlan(System.currentTimeMillis(), targetWeights, actions);
    }

    /**
     * Clears all portfolio state.
     */
    public void reset() {
        navHistory.clear();
        cashBalance = initialCapital;
        lastNav = initialCapital;
        navHistory.add(initialCapital);
        positionTracker.reset();
        LOGGER.info("PortfolioService reset");
    }

    private double computePositionsValue() {
        double value = 0.0;
        Map<String, Position> allPositions = positionTracker.getAllPositions();
        for (Map.Entry<String, Position> entry : allPositions.entrySet()) {
            value += entry.getValue().notionalValue(getLastPrice(entry.getKey()));
        }
        return value;
    }

    private Map<String, Double> computeCurrentWeights() {
        Map<String, Double> weights = new LinkedHashMap<>();
        Map<String, Position> allPositions = positionTracker.getAllPositions();
        double totalValue = computePositionsValue();

        if (totalValue <= 0) {
            return weights;
        }

        for (Map.Entry<String, Position> entry : allPositions.entrySet()) {
            double notional = entry.getValue().notionalValue(getLastPrice(entry.getKey()));
            weights.put(entry.getKey(), notional / totalValue);
        }

        return weights;
    }

    private double computeSharpeRatio() {
        if (navHistory.size() < 3) {
            return 0.0;
        }

        LinkedList<Double> returns = new LinkedList<>();
        for (int i = 1; i < navHistory.size(); i++) {
            double prev = navHistory.get(i - 1);
            if (prev > 0) {
                returns.add((navHistory.get(i) - prev) / prev);
            }
        }

        if (returns.isEmpty()) {
            return 0.0;
        }

        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = returns.stream().mapToDouble(r -> (r - mean) * (r - mean)).average().orElse(0.0);
        double stddev = Math.sqrt(variance);

        if (stddev == 0.0) {
            return 0.0;
        }

        double dailyRiskFree = riskFreeRate / 252.0;
        double annualizedReturn = (mean - dailyRiskFree) / stddev * Math.sqrt(252.0);

        return annualizedReturn;
    }

    /**
     * Returns [maxDrawdown, maxDrawdownDurationSeconds].
     */
    private double[] computeMaxDrawdown() {
        if (navHistory.size() < 2) {
            return new double[]{0.0, 0.0};
        }

        double peak = navHistory.getFirst();
        double maxDrawdown = 0.0;
        int drawdownStart = 0;
        int maxDuration = 0;

        for (int i = 1; i < navHistory.size(); i++) {
            double nav = navHistory.get(i);
            if (nav > peak) {
                peak = nav;
                drawdownStart = i;
            }
            double drawdown = (peak - nav) / peak;
            if (drawdown > maxDrawdown) {
                maxDrawdown = drawdown;
            }
            int duration = i - drawdownStart;
            if (duration > maxDuration) {
                maxDuration = duration;
            }
        }

        double durationSeconds = maxDuration * 60.0;
        return new double[]{maxDrawdown, durationSeconds};
    }

    private double getLastPrice(String symbol) {
        BarSeries series = barAggregator.getSeries(symbol);
        if (series.isEmpty()) {
            return 0.0;
        }
        return series.getLastBar().getClosePrice().doubleValue();
    }
}
