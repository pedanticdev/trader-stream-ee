package fish.payara.trader.matching.engine;

import fish.payara.trader.matching.model.Execution;
import fish.payara.trader.matching.model.Order;
import fish.payara.trader.matching.book.OrderBook;

import java.util.List;

public interface MatchingStrategy {

    List<Execution> match(Order incomingOrder, OrderBook book);
}
