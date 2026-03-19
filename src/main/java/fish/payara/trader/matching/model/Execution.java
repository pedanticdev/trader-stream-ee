package fish.payara.trader.matching.model;

public record Execution(long executionId, long orderId, long clientOrderId, String symbol, Side side, Price price, long quantity, long timestamp) {

    public String toJson() {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        sb.append("\"executionId\":").append(executionId).append(',');
        sb.append("\"orderId\":").append(orderId).append(',');
        sb.append("\"clientOrderId\":").append(clientOrderId).append(',');
        sb.append("\"symbol\":\"").append(symbol).append("\",");
        sb.append("\"side\":\"").append(side.name()).append("\",");
        sb.append("\"price\":").append(price.ticks()).append(',');
        sb.append("\"priceDisplay\":").append(price.toDouble()).append(',');
        sb.append("\"quantity\":").append(quantity).append(',');
        sb.append("\"timestamp\":").append(timestamp);
        sb.append('}');
        return sb.toString();
    }
}
