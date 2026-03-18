package fish.payara.trader.matching.engine;

import fish.payara.trader.matching.model.Order;
import fish.payara.trader.matching.model.OrderStatus;
import fish.payara.trader.matching.model.OrderType;
import fish.payara.trader.matching.model.Price;
import fish.payara.trader.matching.model.Side;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@ApplicationScoped
public class StopOrderTracker {

    private static final Logger LOGGER = Logger.getLogger(StopOrderTracker.class.getName());

    private final ConcurrentHashMap<String, List<Order>> buyStops = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Order>> sellStops = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Order> allStops = new ConcurrentHashMap<>();

    public void register(Order order) {
        if (order.status() != OrderStatus.PENDING_TRIGGER) {
            return;
        }

        allStops.put(order.orderId(), order);

        String symbol = order.symbol();
        switch (order.side()) {
        case BUY -> buyStops.computeIfAbsent(symbol, k -> new ArrayList<>()).add(order);
        case SELL -> sellStops.computeIfAbsent(symbol, k -> new ArrayList<>()).add(order);
        }

        LOGGER.fine(() -> "Registered stop order: " + order.orderId() + " " + order.symbol() + " " + order.side() + " @ " + order.stopPrice().toDouble());
    }

    public List<Order> onMarketData(String symbol, Price bid, Price ask) {
        List<Order> triggered = new ArrayList<>();

        List<Order> symbolBuyStops = buyStops.get(symbol);
        if (symbolBuyStops != null) {
            Iterator<Order> iter = symbolBuyStops.iterator();
            while (iter.hasNext()) {
                Order stop = iter.next();
                if (ask.compareTo(stop.stopPrice()) >= 0) {
                    triggered.add(promote(stop, bid));
                    allStops.remove(stop.orderId());
                    iter.remove();
                }
            }
        }

        List<Order> symbolSellStops = sellStops.get(symbol);
        if (symbolSellStops != null) {
            Iterator<Order> iter = symbolSellStops.iterator();
            while (iter.hasNext()) {
                Order stop = iter.next();
                if (bid.compareTo(stop.stopPrice()) <= 0) {
                    triggered.add(promote(stop, ask));
                    allStops.remove(stop.orderId());
                    iter.remove();
                }
            }
        }

        if (!triggered.isEmpty()) {
            LOGGER.info("Triggered " + triggered.size() + " stop orders for " + symbol);
        }

        return triggered;
    }

    private Order promote(Order stop, Price triggerPrice) {
        OrderStatus newStatus = switch (stop.type()) {
        case STOP -> OrderStatus.NEW;
        case STOP_LIMIT -> OrderStatus.NEW;
        case TRAILING_STOP -> OrderStatus.NEW;
        default -> OrderStatus.NEW;
        };

        return new Order(stop.orderId(), stop.clientOrderId(), stop.symbol(), stop.side(),
                        stop.type() == OrderType.STOP_LIMIT ? OrderType.LIMIT : OrderType.MARKET, newStatus,
                        stop.type() == OrderType.STOP_LIMIT ? stop.price() : triggerPrice, stop.quantity(), stop.leavesQty(), stop.cumQty(), Price.ZERO,
                        stop.timeInForce(), stop.displayQuantity(), stop.trailingStopOffset(), triggerPrice, stop.createdTimestamp(),
                        System.currentTimeMillis());
    }

    public boolean cancel(long orderId) {
        Order removed = allStops.remove(orderId);
        if (removed == null) {
            return false;
        }

        String symbol = removed.symbol();
        List<Order> stops = removed.side() == Side.BUY ? buyStops.get(symbol) : sellStops.get(symbol);

        if (stops != null) {
            stops.removeIf(o -> o.orderId() == orderId);
        }

        LOGGER.fine("Canceled stop order: " + orderId);
        return true;
    }

    public int pendingStopCount(String symbol) {
        int buyCount = buyStops.getOrDefault(symbol, List.of()).size();
        int sellCount = sellStops.getOrDefault(symbol, List.of()).size();
        return buyCount + sellCount;
    }

    public int totalPendingStops() {
        return allStops.size();
    }
}
