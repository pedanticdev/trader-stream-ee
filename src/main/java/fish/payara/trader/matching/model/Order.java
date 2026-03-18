package fish.payara.trader.matching.model;

public record Order(long orderId, long clientOrderId, String symbol, Side side, OrderType type, OrderStatus status, Price price, long quantity, long leavesQty,
                long cumQty, Price stopPrice, TimeInForce timeInForce, long displayQuantity, Price trailingStopOffset, Price trailingStopReference,
                long createdTimestamp, long updatedTimestamp) {

    public Order withStatus(OrderStatus newStatus) {
        return new Order(orderId, clientOrderId, symbol, side, type, newStatus, price, quantity, leavesQty, cumQty, stopPrice, timeInForce, displayQuantity,
                        trailingStopOffset, trailingStopReference, createdTimestamp, System.currentTimeMillis());
    }

    public Order filled(long fillQty) {
        long newCumQty = cumQty + fillQty;
        long newLeavesQty = leavesQty - fillQty;
        OrderStatus newStatus = newLeavesQty <= 0 ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
        return new Order(orderId, clientOrderId, symbol, side, type, newStatus, price, quantity, Math.max(0, newLeavesQty), newCumQty, stopPrice, timeInForce,
                        displayQuantity, trailingStopOffset, trailingStopReference, createdTimestamp, System.currentTimeMillis());
    }

    public boolean isTerminal() {
        return status == OrderStatus.FILLED || status == OrderStatus.CANCELED || status == OrderStatus.REJECTED;
    }
}
