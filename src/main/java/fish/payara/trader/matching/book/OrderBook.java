package fish.payara.trader.matching.book;

import fish.payara.trader.matching.model.Execution;
import fish.payara.trader.matching.model.Order;
import fish.payara.trader.matching.model.OrderBookSnapshot;
import fish.payara.trader.matching.model.Price;

import java.util.List;
import java.util.Optional;

public interface OrderBook {

    String getSymbol();

    void addOrder(Order order);

    CancelResult cancelOrder(long orderId);

    List<Execution> matchOrder(Order incomingOrder);

    List<Execution> matchAgainstMarket(Price bidPrice, Price askPrice, long bidSize, long askSize);

    OrderBookSnapshot getSnapshot(int maxDepth);

    int bidDepth();

    int askDepth();

    Optional<Price> bestBid();

    Optional<Price> bestAsk();

    boolean hasOrders();

    void clear();
}
