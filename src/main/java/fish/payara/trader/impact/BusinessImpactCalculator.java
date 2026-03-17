package fish.payara.trader.impact;

import fish.payara.trader.aeron.MarketDataPublisher;
import fish.payara.trader.dto.BusinessImpactResponse;
import fish.payara.trader.monitoring.GCPauseMonitor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Calculates business impact of SLA violations. Shows missed trades and revenue at risk based on GC pause behavior.
 */
@ApplicationScoped
public class BusinessImpactCalculator {

    @Inject
    private BusinessImpactConfig config;

    @Inject
    private GCPauseMonitor gcPauseMonitor;

    @Inject
    private MarketDataPublisher marketDataPublisher;

    /**
     * Calculates current business impact based on SLA violations and message rate.
     */
    public BusinessImpactResponse calculateImpact() {
        GCPauseMonitor.GCPauseStats stats = gcPauseMonitor.getStats();
        long messageRate = marketDataPublisher.getMessageRatePerSecond();
        long slaViolations10ms = stats.violationsOver10ms;

        double slaCompliancePercent = calculateSLACompliance(stats.totalPauseCount, slaViolations10ms);
        long missedTrades = calculateMissedTrades(messageRate, slaViolations10ms);
        long revenueAtRisk = missedTrades * config.tradeValue();
        int savingsPercent = calculateSavingsPercent(config.instancesNeededC4(), config.instancesNeededG1());

        return BusinessImpactResponse.of(config.tradeValue(), config.currency(), messageRate, slaViolations10ms, missedTrades, revenueAtRisk,
                        slaCompliancePercent, config.instancesNeededC4(), config.instancesNeededG1(), savingsPercent, config.windowSeconds());
    }

    /**
     * Resets business impact calculations by resetting underlying SLA statistics.
     */
    public void reset() {
        gcPauseMonitor.reset();
    }

    /**
     * Calculates SLA compliance percentage. SLA is met if no pauses exceed 10ms.
     */
    private double calculateSLACompliance(long totalPauses, long violations) {
        if (totalPauses == 0) {
            return 100.0;
        }
        double complianceRate = (totalPauses - violations) / (double) totalPauses;
        return complianceRate * 100.0;
    }

    /**
     * Estimate missed trades based on SLA violations and message rate. Assumes each 10ms violation blocks approximately 1ms of message processing.
     */
    private long calculateMissedTrades(long messageRate, long violations) {
        double messagesPerMs = messageRate / 1000.0;
        return (long) (violations * messagesPerMs);
    }

    /**
     * Calculate infrastructure savings percentage between C4 and G1 instances.
     */
    private int calculateSavingsPercent(int instancesNeededC4, int instancesNeededG1) {
        if (instancesNeededG1 <= 0) {
            return 0;
        }
        return (instancesNeededG1 - instancesNeededC4) * 100 / instancesNeededG1;
    }
}
