package fish.payara.trader.matching.model;

public record OrderRequest(String symbol, Side side, OrderType type, Double price, Long quantity, Double stopPrice, TimeInForce timeInForce,
                Long displayQuantity, Double trailingStopOffset) {
}
