package fish.payara.trader.matching.model;

public enum Side {

    BUY(0), SELL(1);

    private final int code;

    Side(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public Side opposite() {
        return switch (this) {
        case BUY -> SELL;
        case SELL -> BUY;
        };
    }
}
