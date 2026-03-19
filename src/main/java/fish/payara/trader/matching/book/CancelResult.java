package fish.payara.trader.matching.book;

import fish.payara.trader.matching.model.Order;

public record CancelResult(boolean canceled, String reason, Order canceledOrder) {

    public static CancelResult success(Order order) {
        return new CancelResult(true, "OK", order);
    }

    public static CancelResult notFound(long orderId) {
        return new CancelResult(false, "Order not found: " + orderId, null);
    }

    public static CancelResult alreadyFilled(long orderId) {
        return new CancelResult(false, "Order already filled: " + orderId, null);
    }

    public static CancelResult alreadyCanceled(long orderId) {
        return new CancelResult(false, "Order already canceled: " + orderId, null);
    }
}
