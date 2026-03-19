package fish.payara.trader.matching.exception;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException() {
        super();
    }

    public OrderNotFoundException(String message) {
        super(message);
    }

    public OrderNotFoundException(long orderId) {
        super("Order not found: " + orderId);
    }
}
