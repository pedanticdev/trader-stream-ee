package fish.payara.trader.matching.position;

import fish.payara.trader.matching.model.Execution;
import fish.payara.trader.matching.model.Position;
import fish.payara.trader.matching.model.Price;
import fish.payara.trader.matching.model.Side;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@ApplicationScoped
public class PositionService {

    private static final Logger LOGGER = Logger.getLogger(PositionService.class.getName());

    private final ConcurrentHashMap<String, Position> positions = new ConcurrentHashMap<>();

    public void updatePosition(Execution execution) {
        positions.compute(execution.symbol(), (symbol, current) -> {
            Position pos = current != null ? current : emptyPosition(symbol);

            long newNetQty;
            Price newAvgPrice;
            long newRealizedPnl;

            if (execution.side() == Side.BUY) {
                if (pos.netQuantity() >= 0) {
                    newAvgPrice = PnlCalculator.calculateAverageEntryPrice(pos, execution.price(), execution.quantity(), pos.netQuantity());
                    newNetQty = pos.netQuantity() + execution.quantity();
                    newRealizedPnl = pos.realizedPnlTicks();
                } else {
                    long closingQty = Math.min(execution.quantity(), Math.abs(pos.netQuantity()));
                    newRealizedPnl = PnlCalculator.calculateRealizedPnl(pos, execution.price(), closingQty);
                    long remainingClose = execution.quantity() - closingQty;
                    if (remainingClose > 0) {
                        newAvgPrice = execution.price();
                        newNetQty = remainingClose;
                    } else {
                        newAvgPrice = closingQty == Math.abs(pos.netQuantity()) ? Price.ZERO : pos.averageEntryPrice();
                        newNetQty = pos.netQuantity() + execution.quantity();
                    }
                    newRealizedPnl += pos.realizedPnlTicks();
                }
            } else {
                if (pos.netQuantity() <= 0) {
                    newAvgPrice = PnlCalculator.calculateAverageEntryPrice(pos, execution.price(), execution.quantity(), Math.abs(pos.netQuantity()));
                    newNetQty = pos.netQuantity() - execution.quantity();
                    newRealizedPnl = pos.realizedPnlTicks();
                } else {
                    long closingQty = Math.min(execution.quantity(), pos.netQuantity());
                    newRealizedPnl = PnlCalculator.calculateRealizedPnl(pos, execution.price(), closingQty);
                    long remainingClose = execution.quantity() - closingQty;
                    if (remainingClose > 0) {
                        newAvgPrice = execution.price();
                        newNetQty = -remainingClose;
                    } else {
                        newAvgPrice = closingQty == pos.netQuantity() ? Price.ZERO : pos.averageEntryPrice();
                        newNetQty = pos.netQuantity() - execution.quantity();
                    }
                    newRealizedPnl += pos.realizedPnlTicks();
                }
            }

            return new Position(symbol, newNetQty, newAvgPrice, newRealizedPnl, pos.markPrice(), 0);
        });
    }

    public void updateMarkPrice(String symbol, Price price) {
        positions.compute(symbol, (key, current) -> {
            Position pos = current != null ? current : emptyPosition(symbol);
            long unrealizedPnl = PnlCalculator.calculateUnrealizedPnl(pos, price);
            return new Position(symbol, pos.netQuantity(), pos.averageEntryPrice(), pos.realizedPnlTicks(), price, unrealizedPnl);
        });
    }

    public Position getPosition(String symbol) {
        return positions.getOrDefault(symbol, emptyPosition(symbol));
    }

    public Map<String, Position> getAllPositions() {
        return Map.copyOf(positions);
    }

    public void reset() {
        positions.clear();
        LOGGER.info("All positions reset");
    }

    private Position emptyPosition(String symbol) {
        return new Position(symbol, 0, Price.ZERO, 0, Price.ZERO, 0);
    }
}
