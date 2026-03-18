package fish.payara.trader.matching.engine;

import fish.payara.trader.matching.book.OrderBook;
import fish.payara.trader.matching.model.Execution;
import fish.payara.trader.matching.model.Order;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.logging.Logger;

@ApplicationScoped
public class PriceTimePriorityMatcher implements MatchingStrategy {

    private static final Logger LOGGER = Logger.getLogger(PriceTimePriorityMatcher.class.getName());

    @Override
    public List<Execution> match(Order incomingOrder, OrderBook book) {
        return book.matchOrder(incomingOrder);
    }
}
