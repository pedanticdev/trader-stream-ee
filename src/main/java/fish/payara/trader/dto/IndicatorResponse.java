package fish.payara.trader.dto;

import java.util.Map;

/**
 * DTO for indicator snapshot responses. Includes manual JSON serialization.
 */
public record IndicatorResponse(String symbol, long timestamp, Map<String, Double> indicators) {

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"symbol\":\"").append(symbol).append('"');
        sb.append(",\"timestamp\":").append(timestamp);
        sb.append(",\"indicators\":{");

        boolean first = true;
        for (Map.Entry<String, Double> entry : indicators.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(entry.getKey()).append('"');
            sb.append(':').append(entry.getValue());
        }

        sb.append("}}");
        return sb.toString();
    }
}
