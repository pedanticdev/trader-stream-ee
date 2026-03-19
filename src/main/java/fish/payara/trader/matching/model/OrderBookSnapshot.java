package fish.payara.trader.matching.model;

import java.util.List;

public record OrderBookSnapshot(String symbol, long timestamp, List<OrderBookLevel> bids, List<OrderBookLevel> asks, int totalBidDepth, int totalAskDepth) {

    public String toJson() {
        StringBuilder sb = new StringBuilder(2048);
        sb.append('{');
        sb.append("\"symbol\":\"").append(symbol).append("\",");
        sb.append("\"timestamp\":").append(timestamp).append(',');
        sb.append("\"totalBidDepth\":").append(totalBidDepth).append(',');
        sb.append("\"totalAskDepth\":").append(totalAskDepth).append(',');

        sb.append("\"bids\":[");
        for (int i = 0; i < bids.size(); i++) {
            if (i > 0)
                sb.append(',');
            sb.append(bids.get(i).toJson());
        }
        sb.append("],");

        sb.append("\"asks\":[");
        for (int i = 0; i < asks.size(); i++) {
            if (i > 0)
                sb.append(',');
            sb.append(asks.get(i).toJson());
        }
        sb.append("]");

        sb.append('}');
        return sb.toString();
    }
}
