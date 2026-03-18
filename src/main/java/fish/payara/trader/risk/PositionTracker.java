package fish.payara.trader.risk;

import fish.payara.trader.risk.model.Position;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Tracks positions per symbol. Thread-safe via ConcurrentHashMap. Handles fills by updating average entry price and computing realized P&L on closing
 * (reducing) trades.
 */
@ApplicationScoped
public class PositionTracker {

    private static final Logger LOGGER = Logger.getLogger(PositionTracker.class.getName());

    private static final Position ZERO = new Position("");

    private final ConcurrentHashMap<String, Position> positions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> positionLimits = new ConcurrentHashMap<>();

    @Inject
    private RiskConfig config;

    /**
     * Represents trade side. Local definition since no shared Side enum exists.
     */
    public enum Side {
        BUY, SELL
    }

    /**
     * Process a fill and update the position.
     *
     * @param symbol
     *            the instrument
     * @param quantity
     *            absolute fill quantity
     * @param price
     *            fill price
     * @param side
     *            BUY or SELL
     */
    public void onFill(String symbol, long quantity, double price, Side side) {
        positions.compute(symbol, (key, existing) -> {
            Position pos = existing != null ? existing : new Position(symbol);

            long signedQty = side == Side.BUY ? quantity : -quantity;
            long oldQty = pos.quantity();
            long newQty = oldQty + signedQty;

            double realizedPnl = pos.realizedPnl();
            double avgEntry = pos.averageEntryPrice();

            if (oldQty > 0 && signedQty < 0) {
                long closingQty = Math.min(Math.abs(signedQty), oldQty);
                realizedPnl += closingQty * (price - avgEntry);
                long remaining = oldQty - closingQty;
                if (remaining == 0) {
                    avgEntry = 0.0;
                } else {
                    avgEntry = avgEntry;
                }
            } else if (oldQty < 0 && signedQty > 0) {
                long closingQty = Math.min(signedQty, Math.abs(oldQty));
                realizedPnl += closingQty * (avgEntry - price);
                long remaining = Math.abs(oldQty) - closingQty;
                if (remaining == 0) {
                    avgEntry = 0.0;
                }
            }

            if ((oldQty >= 0 && newQty > oldQty) || (oldQty <= 0 && newQty < oldQty)) {
                double totalCost = avgEntry * Math.abs(oldQty) + price * Math.abs(signedQty);
                long totalQty = Math.abs(newQty);
                avgEntry = totalQty > 0 ? totalCost / totalQty : 0.0;
            }

            long tradeCount = pos.tradeCount() + 1;
            return new Position(symbol, newQty, avgEntry, realizedPnl, tradeCount);
        });
    }

    /**
     * Returns the position for the given symbol, or a zero-initialized default.
     */
    public Position getPosition(String symbol) {
        return positions.getOrDefault(symbol, new Position(symbol));
    }

    /**
     * Returns all tracked positions.
     */
    public Map<String, Position> getAllPositions() {
        return Map.copyOf(positions);
    }

    /**
     * Checks whether adding the given quantity would breach the position limit.
     */
    public boolean wouldBreachLimit(String symbol, long additionalQty) {
        double limit = positionLimits.getOrDefault(symbol, config.defaultPositionLimit());
        Position pos = getPosition(symbol);
        double price = pos.averageEntryPrice();
        double newNotional = (Math.abs(pos.quantity()) + Math.abs(additionalQty)) * price;
        return newNotional > limit;
    }

    /**
     * Sets a per-symbol position limit.
     */
    public void updatePositionLimit(String symbol, double limit) {
        positionLimits.put(symbol, limit);
    }

    /**
     * Returns the effective position limit for the given symbol.
     */
    public double getPositionLimit(String symbol) {
        return positionLimits.getOrDefault(symbol, config.defaultPositionLimit());
    }

    /**
     * Clears all positions and limits.
     */
    public void reset() {
        positions.clear();
        positionLimits.clear();
        LOGGER.info("PositionTracker reset");
    }
}
