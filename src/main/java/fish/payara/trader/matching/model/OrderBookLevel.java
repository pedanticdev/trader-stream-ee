package fish.payara.trader.matching.model;

import jakarta.json.bind.annotation.JsonbTransient;

public record OrderBookLevel(@JsonbTransient Price price, double priceDisplay, long quantity, int orderCount) {

    public OrderBookLevel(Price price, long quantity, int orderCount) {
        this(price, price.toDouble(), quantity, orderCount);
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder(128);
        sb.append('{');
        sb.append("\"price\":").append(price.ticks()).append(',');
        sb.append("\"priceDisplay\":").append(priceDisplay).append(',');
        sb.append("\"quantity\":").append(quantity).append(',');
        sb.append("\"orderCount\":").append(orderCount);
        sb.append('}');
        return sb.toString();
    }
}
