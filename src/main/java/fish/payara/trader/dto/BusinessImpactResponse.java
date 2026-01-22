package fish.payara.trader.dto;

/**
 * Response DTO for business impact calculations. Shows the business cost of SLA violations in terms of missed trades and revenue.
 */
public record BusinessImpactResponse(
                // Configuration
                int tradeValue, String currency, long messageRate,

                // Violation impact
                long slaViolations10ms, long missedTrades, long revenueAtRisk,

                // SLA compliance
                double slaCompliancePercent,

                // Infrastructure comparison
                int instancesNeededC4, int instancesNeededG1, int infrastructureSavingsPercent,

                // Time window
                long windowSeconds) {
    /**
     * Factory method for creating impact response with all computed values. Business logic is handled by BusinessImpactCalculator.
     */
    public static BusinessImpactResponse of(int tradeValue, String currency, long messageRate, long slaViolations10ms, long missedTrades, long revenueAtRisk,
                    double slaCompliancePercent, int instancesNeededC4, int instancesNeededG1, int infrastructureSavingsPercent, long windowSeconds) {
        return new BusinessImpactResponse(tradeValue, currency, messageRate, slaViolations10ms, missedTrades, revenueAtRisk, slaCompliancePercent,
                        instancesNeededC4, instancesNeededG1, infrastructureSavingsPercent, windowSeconds);
    }
}
