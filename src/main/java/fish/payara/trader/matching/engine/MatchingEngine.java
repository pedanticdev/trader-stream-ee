package fish.payara.trader.matching.engine;

import fish.payara.trader.matching.book.CancelResult;
import fish.payara.trader.matching.book.OrderBook;
import fish.payara.trader.matching.book.OrderBookFactory;
import fish.payara.trader.matching.config.MatchingConfig;
import fish.payara.trader.matching.exception.OrderValidationException;
import fish.payara.trader.matching.history.ExecutionHistory;
import fish.payara.trader.matching.history.ExecutionHistoryQuery;
import fish.payara.trader.matching.jfr.MatchingEvents;
import fish.payara.trader.matching.model.*;
import fish.payara.trader.matching.position.PositionService;
import fish.payara.trader.matching.websocket.ExecutionBroadcaster;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class MatchingEngine {

    private static final Logger LOGGER = Logger.getLogger(MatchingEngine.class.getName());
    private static final Set<String> VALID_SYMBOLS = Set.of("AAPL", "GOOGL", "MSFT", "AMZN", "TSLA", "NVDA", "META", "NFLX");

    @Inject
    private OrderBookFactory orderBookFactory;

    @Inject
    private PriceTimePriorityMatcher matcher;

    @Inject
    private StopOrderTracker stopOrderTracker;

    @Inject
    private IcebergOrderHandler icebergOrderHandler;

    @Inject
    private PositionService positionService;

    @Inject
    private ExecutionBroadcaster executionBroadcaster;

    @Inject
    private ExecutionHistory executionHistory;

    @Inject
    private MatchingConfig matchingConfig;

    private final AtomicLong orderIdGenerator = new AtomicLong(System.currentTimeMillis() * 10000);

    public Order submitOrder(OrderRequest request) {
        validate(request);

        long orderId = orderIdGenerator.incrementAndGet();
        long now = System.currentTimeMillis();

        Price limitPrice = request.price() != null ? Price.fromDouble(request.price()) : Price.ZERO;
        Price stopPrice = request.stopPrice() != null ? Price.fromDouble(request.stopPrice()) : Price.ZERO;
        Price trailingOffset = request.trailingStopOffset() != null ? Price.fromDouble(request.trailingStopOffset()) : Price.ZERO;

        TimeInForce tif = request.timeInForce() != null ? request.timeInForce() : TimeInForce.GTC;
        long displayQty = request.displayQuantity() != null ? request.displayQuantity() : 0;

        boolean isStopOrder = request.type() == OrderType.STOP || request.type() == OrderType.STOP_LIMIT || request.type() == OrderType.TRAILING_STOP;

        OrderStatus initialStatus = isStopOrder ? OrderStatus.PENDING_TRIGGER : OrderStatus.NEW;

        Order order = new Order(orderId, orderId, request.symbol(), request.side(), request.type(), initialStatus, limitPrice, request.quantity(),
                        request.quantity(), 0, stopPrice, tif, displayQty, trailingOffset, Price.ZERO, now, now);

        emitOrderSubmitted(order);

        if (isStopOrder) {
            stopOrderTracker.register(order);
            LOGGER.info("Stop order registered: " + orderId + " " + request.symbol() + " " + request.side() + " type=" + request.type());
            return order;
        }

        if (request.type() == OrderType.ICEBERG && displayQty > 0) {
            icebergOrderHandler.track(order);
        }

        OrderBook book = orderBookFactory.getOrCreate(request.symbol());

        List<Execution> executions = matcher.match(order, book);
        long totalFilled = executions.stream().mapToLong(Execution::quantity).sum();

        if (totalFilled > 0) {
            Order filledOrder = order;
            for (Execution exec : executions) {
                executionHistory.append(exec);
                positionService.updatePosition(exec);
                filledOrder = filledOrder.filled(exec.quantity());
                executionBroadcaster.broadcast(exec.toJson());
            }
            emitOrderMatched(filledOrder, totalFilled);

            if (filledOrder.leavesQty() > 0 && !isImmediateOrFillAndKill(tif)) {
                Order resting = new Order(filledOrder.orderId(), filledOrder.clientOrderId(), filledOrder.symbol(), filledOrder.side(), filledOrder.type(),
                                OrderStatus.NEW, filledOrder.price(), filledOrder.quantity(), filledOrder.leavesQty(), filledOrder.cumQty(),
                                filledOrder.stopPrice(), filledOrder.timeInForce(), filledOrder.displayQuantity(), filledOrder.trailingStopOffset(),
                                filledOrder.trailingStopReference(), filledOrder.createdTimestamp(), System.currentTimeMillis());
                book.addOrder(resting);
                return resting;
            }
            return filledOrder;
        }

        book.addOrder(order);
        return order;
    }

    private boolean isImmediateOrFillAndKill(TimeInForce tif) {
        return tif == TimeInForce.IOC || tif == TimeInForce.FOK;
    }

    public CancelResult cancelOrder(long orderId) {
        boolean removedFromStops = stopOrderTracker.cancel(orderId);

        if (removedFromStops) {
            emitOrderCanceled(orderId, "Stop order canceled");
            return CancelResult.success(null);
        }

        CancelResult result = null;
        for (String symbol : VALID_SYMBOLS) {
            OrderBook book = orderBookFactory.get(symbol);
            if (book != null && book.hasOrders()) {
                CancelResult attempt = book.cancelOrder(orderId);
                if (attempt.canceled()) {
                    result = attempt;
                    break;
                }
                if (result == null && !attempt.canceled() && attempt.canceledOrder() == null) {
                    result = attempt;
                }
            }
        }

        if (result != null && result.canceled()) {
            emitOrderCanceled(orderId, result.reason());
        }

        if (result == null) {
            result = CancelResult.notFound(orderId);
        }
        return result;
    }

    public List<Execution> onMarketData(String symbol, Price bid, Price ask, long bidSize, long askSize) {
        List<Execution> allExecutions = new ArrayList<>();

        List<Order> triggeredStops = stopOrderTracker.onMarketData(symbol, bid, ask);
        for (Order triggered : triggeredStops) {
            emitStopTriggered(triggered);
            OrderRequest promotedRequest = new OrderRequest(triggered.symbol(), triggered.side(), triggered.type(), triggered.price().toDouble(),
                            triggered.leavesQty(), null, triggered.timeInForce(), triggered.displayQuantity(), triggered.trailingStopOffset().toDouble());
            try {
                Order promotedOrder = submitOrder(promotedRequest);
                LOGGER.info("Promoted stop order " + triggered.orderId() + " -> " + promotedOrder.orderId());
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to promote triggered stop order " + triggered.orderId(), e);
            }
        }

        OrderBook book = orderBookFactory.get(symbol);
        if (book == null) {
            return allExecutions;
        }

        List<Execution> sweepExecs = book.matchAgainstMarket(bid, ask, bidSize, askSize);
        for (Execution exec : sweepExecs) {
            executionHistory.append(exec);
            positionService.updatePosition(exec);
            executionBroadcaster.broadcast(exec.toJson());
            allExecutions.add(exec);
        }

        positionService.updateMarkPrice(symbol, bid);

        return allExecutions;
    }

    public Optional<OrderBookSnapshot> getBook(String symbol) {
        OrderBook book = orderBookFactory.get(symbol);
        if (book == null) {
            return Optional.empty();
        }
        return Optional.of(book.getSnapshot(matchingConfig.maxBookDepth()));
    }

    public Map<String, Position> getPositions() {
        return positionService.getAllPositions();
    }

    public Optional<Position> getPosition(String symbol) {
        return Optional.of(positionService.getPosition(symbol));
    }

    public List<Execution> getExecutions(ExecutionHistoryQuery query) {
        return executionHistory.query(query);
    }

    private void validate(OrderRequest request) {
        if (request.symbol() == null || !VALID_SYMBOLS.contains(request.symbol())) {
            throw new OrderValidationException("Invalid symbol: " + request.symbol());
        }
        if (request.side() == null) {
            throw new OrderValidationException("Side is required");
        }
        if (request.type() == null) {
            throw new OrderValidationException("Order type is required");
        }
        if (request.quantity() == null || request.quantity() <= 0) {
            throw new OrderValidationException("Quantity must be positive");
        }

        switch (request.type()) {
        case LIMIT, STOP_LIMIT -> {
            if (request.price() == null || request.price() <= 0) {
                throw new OrderValidationException("Price is required for " + request.type() + " orders");
            }
        }
        case STOP, TRAILING_STOP -> {
            if (request.stopPrice() == null || request.stopPrice() <= 0) {
                throw new OrderValidationException("Stop price is required for " + request.type() + " orders");
            }
        }
        case ICEBERG -> {
            if (request.displayQuantity() == null || request.displayQuantity() <= 0) {
                throw new OrderValidationException("Display quantity is required for ICEBERG orders");
            }
            if (request.price() == null || request.price() <= 0) {
                throw new OrderValidationException("Price is required for ICEBERG orders");
            }
        }
        }
    }

    private void emitOrderSubmitted(Order order) {
        var event = new MatchingEvents.OrderSubmitted();
        if (event.isEnabled()) {
            event.orderId = order.orderId();
            event.symbol = order.symbol();
            event.side = order.side().name();
            event.type = order.type().name();
            event.quantity = order.quantity();
            event.price = order.price().ticks();
            event.commit();
        }
    }

    private void emitOrderMatched(Order order, long totalFilled) {
        var event = new MatchingEvents.OrderMatched();
        if (event.isEnabled()) {
            event.orderId = order.orderId();
            event.symbol = order.symbol();
            event.side = order.side().name();
            event.filledQuantity = totalFilled;
            event.remainingQuantity = order.leavesQty();
            event.status = order.status().name();
            event.commit();
        }
    }

    private void emitStopTriggered(Order order) {
        var event = new MatchingEvents.StopTriggered();
        if (event.isEnabled()) {
            event.orderId = order.orderId();
            event.symbol = order.symbol();
            event.side = order.side().name();
            event.stopPrice = order.stopPrice().ticks();
            event.commit();
        }
    }

    private void emitOrderCanceled(long orderId, String reason) {
        var event = new MatchingEvents.OrderCanceled();
        if (event.isEnabled()) {
            event.orderId = orderId;
            event.reason = reason;
            event.commit();
        }
    }
}
