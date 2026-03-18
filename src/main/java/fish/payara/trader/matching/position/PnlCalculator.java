package fish.payara.trader.matching.position;

import fish.payara.trader.matching.model.Position;
import fish.payara.trader.matching.model.Price;

/**
 * Static utility methods for P&amp;L and average entry price calculations. All prices in fixed-point ticks (multiply by 10000 for decimal).
 */
public final class PnlCalculator {

    private PnlCalculator() {
    }

    /**
     * Calculates realized P&amp;L when closing a position. Positive return = profit, negative = loss.
     */
    public static long calculateRealizedPnl(Position position, Price closePrice, long closeQty) {
        long currentNetQty = position.netQuantity();
        Price entryPrice = position.averageEntryPrice();

        if (entryPrice.isZero() || closeQty == 0) {
            return 0;
        }

        if (currentNetQty > 0) {
            return (closePrice.ticks() - entryPrice.ticks()) * closeQty;
        } else {
            return (entryPrice.ticks() - closePrice.ticks()) * closeQty;
        }
    }

    /**
     * Calculates unrealized P&amp;L at the given mark price.
     */
    public static long calculateUnrealizedPnl(Position position, Price markPrice) {
        long netQty = position.netQuantity();

        if (netQty == 0 || position.averageEntryPrice().isZero()) {
            return 0;
        }

        if (netQty > 0) {
            return (markPrice.ticks() - position.averageEntryPrice().ticks()) * netQty;
        } else {
            return (position.averageEntryPrice().ticks() - markPrice.ticks()) * Math.abs(netQty);
        }
    }

    /**
     * Recalculates the volume-weighted average entry price after a new fill.
     */
    public static Price calculateAverageEntryPrice(Position position, Price newFillPrice, long newFillQty, long currentNetQty) {
        if (newFillQty <= 0 || newFillPrice.isZero()) {
            return position.averageEntryPrice();
        }

        long absCurrent = Math.abs(currentNetQty);
        long totalQty = absCurrent + newFillQty;

        if (absCurrent == 0) {
            return newFillPrice;
        }

        long weightedSum = position.averageEntryPrice().ticks() * absCurrent + newFillPrice.ticks() * newFillQty;

        return new Price(weightedSum / totalQty);
    }
}
