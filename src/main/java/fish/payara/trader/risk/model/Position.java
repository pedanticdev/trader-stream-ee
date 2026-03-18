package fish.payara.trader.risk.model;

/**
 * Tracks an open position for a single symbol. Quantity is positive for longs, negative for shorts.
 */
public record Position(String symbol, long quantity, double averageEntryPrice, double realizedPnl, long tradeCount) {

    public Position(String symbol) {
        this(symbol, 0, 0.0, 0.0, 0);
    }

    public double notionalValue(double currentPrice) {
        return Math.abs(quantity) * currentPrice;
    }

    public double unrealizedPnl(double currentPrice) {
        if (quantity == 0 || averageEntryPrice == 0.0) {
            return 0.0;
        }
        return quantity > 0 ? (currentPrice - averageEntryPrice) * quantity : (averageEntryPrice - currentPrice) * Math.abs(quantity);
    }

    public double delta() {
        return quantity;
    }

    public boolean isFlat() {
        return quantity == 0;
    }

    public boolean isLong() {
        return quantity > 0;
    }

    public boolean isShort() {
        return quantity < 0;
    }
}
