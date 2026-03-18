package fish.payara.trader.matching.model;

public record OrderBookEntry(Order order, long entryTimestamp) {

    public OrderBookEntry filledBy(long fillQty) {
        long remaining = Math.max(0, order.leavesQty() - fillQty);
        Order updatedOrder = order.filled(fillQty);
        return new OrderBookEntry(updatedOrder, entryTimestamp);
    }

    public long remainingQty() {
        return order.leavesQty();
    }
}
