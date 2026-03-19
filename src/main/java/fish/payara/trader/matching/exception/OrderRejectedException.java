package fish.payara.trader.matching.exception;

public class OrderRejectedException extends RuntimeException {

    private final String reason;

    public OrderRejectedException(String reason) {
        super(reason);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
