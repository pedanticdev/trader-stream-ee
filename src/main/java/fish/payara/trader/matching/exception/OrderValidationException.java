package fish.payara.trader.matching.exception;

public class OrderValidationException extends RuntimeException {

    private final String reason;

    public OrderValidationException(String reason) {
        super(reason);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
