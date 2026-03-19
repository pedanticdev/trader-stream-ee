package fish.payara.trader.matching.model;

import jakarta.json.bind.annotation.JsonbTransient;

public record Position(String symbol, long netQuantity, @JsonbTransient Price averageEntryPrice, long realizedPnlTicks, @JsonbTransient Price markPrice,
                long unrealizedPnlTicks) {

    public double getAverageEntryPrice() {
        return averageEntryPrice.toDouble();
    }

    public double getMarkPrice() {
        return markPrice.toDouble();
    }

    @JsonbTransient
    public double notionalValue(double markPrice) {
        return netQuantity * markPrice;
    }

    @JsonbTransient
    public boolean isFlat() {
        return netQuantity == 0;
    }
}
