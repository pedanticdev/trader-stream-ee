package fish.payara.trader.matching.book;

import fish.payara.trader.matching.model.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Price-time priority order book. Bids sorted descending (best = highest), asks sorted ascending (best = lowest).
 */
public class PriceTimeOrderBook implements OrderBook {

    private static final Logger LOGGER = Logger.getLogger(PriceTimeOrderBook.class.getName());

    private final String symbol;
    private final NavigableMap<Price, LinkedList<OrderBookEntry>> bids = new TreeMap<>(Comparator.reverseOrder());
    private final NavigableMap<Price, LinkedList<OrderBookEntry>> asks = new TreeMap<>();
    private final Map<Long, OrderBookEntry> orderIndex = new HashMap<>();

    private final AtomicLong executionIdGenerator = new AtomicLong(System.currentTimeMillis() * 10000);

    public PriceTimeOrderBook(String symbol) {
        this.symbol = symbol;
    }

    @Override
    public String getSymbol() {
        return symbol;
    }

    @Override
    public void addOrder(Order order) {
        synchronized (this) {
            OrderBookEntry entry = new OrderBookEntry(order, System.currentTimeMillis());
            orderIndex.put(order.orderId(), entry);

            var book = order.side() == Side.BUY ? bids : asks;
            book.computeIfAbsent(order.price(), k -> new LinkedList<>()).addLast(entry);
        }
    }

    @Override
    public CancelResult cancelOrder(long orderId) {
        synchronized (this) {
            OrderBookEntry entry = orderIndex.remove(orderId);
            if (entry == null) {
                return CancelResult.notFound(orderId);
            }
            Order order = entry.order();
            if (order.isTerminal()) {
                return order.status() == OrderStatus.FILLED ? CancelResult.alreadyFilled(orderId) : CancelResult.alreadyCanceled(orderId);
            }

            var book = order.side() == Side.BUY ? bids : asks;
            var level = book.get(order.price());
            if (level != null) {
                level.removeIf(e -> e.order().orderId() == orderId);
                if (level.isEmpty()) {
                    book.remove(order.price());
                }
            }
            return CancelResult.success(order.withStatus(OrderStatus.CANCELED));
        }
    }

    @Override
    public List<Execution> matchOrder(Order incomingOrder) {
        List<Execution> executions = new ArrayList<>();

        if (incomingOrder.leavesQty() <= 0) {
            return executions;
        }

        long remaining = incomingOrder.leavesQty();

        synchronized (this) {
            if (incomingOrder.side() == Side.BUY) {
                Iterator<Map.Entry<Price, LinkedList<OrderBookEntry>>> levelIter = asks.entrySet().iterator();

                while (levelIter.hasNext() && remaining > 0) {
                    Map.Entry<Price, LinkedList<OrderBookEntry>> level = levelIter.next();
                    Price askPrice = level.getKey();

                    if (incomingOrder.type() == OrderType.LIMIT && askPrice.compareTo(incomingOrder.price()) > 0) {
                        break;
                    }

                    Iterator<OrderBookEntry> entryIter = level.getValue().iterator();
                    while (entryIter.hasNext() && remaining > 0) {
                        OrderBookEntry entry = entryIter.next();
                        Order resting = entry.order();
                        long fillQty = Math.min(remaining, resting.leavesQty());

                        Execution exec = new Execution(executionIdGenerator.incrementAndGet(), resting.orderId(), resting.clientOrderId(), resting.symbol(),
                                        Side.BUY, askPrice, fillQty, System.currentTimeMillis());
                        executions.add(exec);
                        remaining -= fillQty;
                        entryIter.remove();
                    }

                    if (level.getValue().isEmpty()) {
                        levelIter.remove();
                    }
                }
            } else {
                Iterator<Map.Entry<Price, LinkedList<OrderBookEntry>>> levelIter = bids.entrySet().iterator();

                while (levelIter.hasNext() && remaining > 0) {
                    Map.Entry<Price, LinkedList<OrderBookEntry>> level = levelIter.next();
                    Price bidPrice = level.getKey();

                    if (incomingOrder.type() == OrderType.LIMIT && bidPrice.compareTo(incomingOrder.price()) < 0) {
                        break;
                    }

                    Iterator<OrderBookEntry> entryIter = level.getValue().iterator();
                    while (entryIter.hasNext() && remaining > 0) {
                        OrderBookEntry entry = entryIter.next();
                        Order resting = entry.order();
                        long fillQty = Math.min(remaining, resting.leavesQty());

                        Execution exec = new Execution(executionIdGenerator.incrementAndGet(), resting.orderId(), resting.clientOrderId(), resting.symbol(),
                                        Side.SELL, bidPrice, fillQty, System.currentTimeMillis());
                        executions.add(exec);
                        remaining -= fillQty;
                        entryIter.remove();
                    }

                    if (level.getValue().isEmpty()) {
                        levelIter.remove();
                    }
                }
            }
        }

        if (!executions.isEmpty()) {
            long totalFilled = executions.stream().mapToLong(Execution::quantity).sum();
            LOGGER.fine(() -> "Matched " + totalFilled + " qty for order " + incomingOrder.orderId() + " across " + executions.size() + " executions");
        }

        return executions;
    }

    @Override
    public List<Execution> matchAgainstMarket(Price bidPrice, Price askPrice, long bidSize, long askSize) {
        List<Execution> executions = new ArrayList<>();
        synchronized (this) {
            sweepAsks(bidPrice, bidSize, Side.BUY, executions);
            sweepBids(askPrice, askSize, Side.SELL, executions);
        }
        return executions;
    }

    private void sweepAsks(Price bidPrice, long bidSize, Side aggressiveSide, List<Execution> executions) {
        Iterator<Map.Entry<Price, LinkedList<OrderBookEntry>>> levelIter = asks.entrySet().iterator();
        long remainingBid = bidSize;

        while (levelIter.hasNext() && remainingBid > 0) {
            Map.Entry<Price, LinkedList<OrderBookEntry>> level = levelIter.next();
            Price askPrice = level.getKey();

            if (askPrice.compareTo(bidPrice) > 0) {
                break;
            }

            Iterator<OrderBookEntry> entryIter = level.getValue().iterator();
            while (entryIter.hasNext() && remainingBid > 0) {
                OrderBookEntry entry = entryIter.next();
                Order resting = entry.order();
                long fillQty = Math.min(remainingBid, resting.leavesQty());

                Execution exec = new Execution(executionIdGenerator.incrementAndGet(), resting.orderId(), resting.clientOrderId(), resting.symbol(),
                                aggressiveSide, askPrice, fillQty, System.currentTimeMillis());
                executions.add(exec);

                remainingBid -= fillQty;
                Order updatedResting = resting.filled(fillQty);
                entry = entry.filledBy(fillQty);

                if (entry.remainingQty() <= 0) {
                    orderIndex.remove(resting.orderId());
                    entryIter.remove();
                } else {
                    entryIter.remove();
                    level.getValue().addFirst(new OrderBookEntry(updatedResting, entry.entryTimestamp()));
                }
            }

            if (level.getValue().isEmpty()) {
                levelIter.remove();
            }
        }
    }

    private void sweepBids(Price askPrice, long askSize, Side aggressiveSide, List<Execution> executions) {
        Iterator<Map.Entry<Price, LinkedList<OrderBookEntry>>> levelIter = bids.entrySet().iterator();
        long remainingAsk = askSize;

        while (levelIter.hasNext() && remainingAsk > 0) {
            Map.Entry<Price, LinkedList<OrderBookEntry>> level = levelIter.next();
            Price bidPrice = level.getKey();

            if (bidPrice.compareTo(askPrice) < 0) {
                break;
            }

            Iterator<OrderBookEntry> entryIter = level.getValue().iterator();
            while (entryIter.hasNext() && remainingAsk > 0) {
                OrderBookEntry entry = entryIter.next();
                Order resting = entry.order();
                long fillQty = Math.min(remainingAsk, resting.leavesQty());

                Execution exec = new Execution(executionIdGenerator.incrementAndGet(), resting.orderId(), resting.clientOrderId(), resting.symbol(),
                                aggressiveSide, bidPrice, fillQty, System.currentTimeMillis());
                executions.add(exec);

                remainingAsk -= fillQty;
                Order updatedResting = resting.filled(fillQty);
                entry = entry.filledBy(fillQty);

                if (entry.remainingQty() <= 0) {
                    orderIndex.remove(resting.orderId());
                    entryIter.remove();
                } else {
                    entryIter.remove();
                    level.getValue().addFirst(new OrderBookEntry(updatedResting, entry.entryTimestamp()));
                }
            }

            if (level.getValue().isEmpty()) {
                levelIter.remove();
            }
        }
    }

    @Override
    public OrderBookSnapshot getSnapshot(int maxDepth) {
        synchronized (this) {
            List<OrderBookLevel> bidLevels = buildLevels(bids, maxDepth);
            List<OrderBookLevel> askLevels = buildLevels(asks, maxDepth);
            int totalBidDepth = bids.values().stream().mapToInt(List::size).sum();
            int totalAskDepth = asks.values().stream().mapToInt(List::size).sum();
            return new OrderBookSnapshot(symbol, System.currentTimeMillis(), bidLevels, askLevels, totalBidDepth, totalAskDepth);
        }
    }

    private List<OrderBookLevel> buildLevels(NavigableMap<Price, LinkedList<OrderBookEntry>> book, int maxDepth) {
        List<OrderBookLevel> levels = new ArrayList<>();
        int count = 0;
        for (Map.Entry<Price, LinkedList<OrderBookEntry>> entry : book.entrySet()) {
            if (count >= maxDepth)
                break;
            long totalQty = entry.getValue().stream().mapToLong(e -> e.order().leavesQty()).sum();
            levels.add(new OrderBookLevel(entry.getKey(), totalQty, entry.getValue().size()));
            count++;
        }
        return levels;
    }

    @Override
    public int bidDepth() {
        synchronized (this) {
            return bids.values().stream().mapToInt(List::size).sum();
        }
    }

    @Override
    public int askDepth() {
        synchronized (this) {
            return asks.values().stream().mapToInt(List::size).sum();
        }
    }

    @Override
    public Optional<Price> bestBid() {
        synchronized (this) {
            var first = bids.firstEntry();
            return first == null ? Optional.empty() : Optional.of(first.getKey());
        }
    }

    @Override
    public Optional<Price> bestAsk() {
        synchronized (this) {
            var first = asks.firstEntry();
            return first == null ? Optional.empty() : Optional.of(first.getKey());
        }
    }

    @Override
    public boolean hasOrders() {
        synchronized (this) {
            return !bids.isEmpty() || !asks.isEmpty();
        }
    }

    @Override
    public void clear() {
        synchronized (this) {
            bids.clear();
            asks.clear();
            orderIndex.clear();
            LOGGER.info("Order book cleared");
        }
    }

}
