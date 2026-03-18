package fish.payara.trader.risk.model;

/**
 * Sealed interface for stress test scenarios. Each variant applies a different shock to positions and returns the resulting P&L impact.
 */
public sealed interface StressScenario permits StressScenario.FlashCrash, StressScenario.VolatilitySpike, StressScenario.LiquidityFreeze,
                StressScenario.CorrelationBreakdown, StressScenario.InterestRateShock {

    String name();

    /**
     * Apply the stress scenario to a position at the given price. Returns the P&L impact (negative means loss).
     */
    double apply(Position position, double price);

    record FlashCrash(double crashPercent) implements StressScenario {
        @Override
        public String name() {
            return "Flash Crash (%.1f%%)".formatted(crashPercent);
        }

        @Override
        public double apply(Position position, double price) {
            double shockedPrice = price * (1.0 - crashPercent / 100.0);
            return position.unrealizedPnl(shockedPrice) - position.unrealizedPnl(price);
        }
    }

    record VolatilitySpike(double volMultiplier) implements StressScenario {
        @Override
        public String name() {
            return "Volatility Spike (%.1fx)".formatted(volMultiplier);
        }

        @Override
        public double apply(Position position, double price) {
            double shockPercent = (volMultiplier - 1.0) * 10.0;
            double shockedPrice = price * (1.0 - shockPercent / 100.0);
            return position.unrealizedPnl(shockedPrice) - position.unrealizedPnl(price);
        }
    }

    record LiquidityFreeze(double bidAskWidenPercent) implements StressScenario {
        @Override
        public String name() {
            return "Liquidity Freeze (spread +%.1f%%)".formatted(bidAskWidenPercent);
        }

        @Override
        public double apply(Position position, double price) {
            double halfSpread = price * (bidAskWidenPercent / 200.0);
            double liquidationPrice = position.isLong() ? price - halfSpread : price + halfSpread;
            return position.unrealizedPnl(liquidationPrice) - position.unrealizedPnl(price);
        }
    }

    record CorrelationBreakdown(double decorrelationPercent) implements StressScenario {
        @Override
        public String name() {
            return "Correlation Breakdown (%.1f%%)".formatted(decorrelationPercent);
        }

        @Override
        public double apply(Position position, double price) {
            double shockPercent = decorrelationPercent / 100.0 * 5.0;
            double shockedPrice = price * (1.0 - shockPercent);
            return position.unrealizedPnl(shockedPrice) - position.unrealizedPnl(price);
        }
    }

    record InterestRateShock(double basisPoints) implements StressScenario {
        @Override
        public String name() {
            return "Interest Rate Shock (%.0f bps)".formatted(basisPoints);
        }

        @Override
        public double apply(Position position, double price) {
            double impact = position.notionalValue(price) * (basisPoints / 10_000.0) * 0.1;
            return -impact;
        }
    }
}
