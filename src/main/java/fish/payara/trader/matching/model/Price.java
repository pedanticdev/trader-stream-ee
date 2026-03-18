package fish.payara.trader.matching.model;

import jakarta.json.bind.annotation.JsonbTransient;

public record Price(long ticks) implements Comparable<Price> {

    public static final int SCALE = 10_000;

    public double toDouble() {
        return ticks / (double) SCALE;
    }

    public static Price fromDouble(double value) {
        return new Price(Math.round(value * SCALE));
    }

    public static final Price ZERO = new Price(0);

    public Price add(Price other) {
        return new Price(ticks + other.ticks);
    }

    public Price subtract(Price other) {
        return new Price(ticks - other.ticks);
    }

    public Price multiply(long factor) {
        return new Price(ticks * factor);
    }

    @Override
    public int compareTo(Price other) {
        return Long.compare(ticks, other.ticks);
    }

    @JsonbTransient
    public boolean isZero() {
        return ticks == 0;
    }

    @JsonbTransient
    public boolean isPositive() {
        return ticks > 0;
    }

    @JsonbTransient
    public boolean isNegative() {
        return ticks < 0;
    }
}
