package fish.payara.trader.dto;

import fish.payara.trader.gc.GCStats;
import fish.payara.trader.monitoring.GCPauseMonitor.GCPauseStats;

import java.util.List;

/**
 * Response DTO for GC comparison data. Provides comprehensive GC metrics for C4 vs G1 comparison.
 */
public record GCComparisonResponse(
                // Instance identification
                String instanceName, String jvmVendor, String jvmName, String gcCollectors, boolean isAzulC4,

                // Memory configuration
                long heapSizeMB, String allocationMode, int allocationRateMBps, long messageRate,

                // GC statistics
                List<GCStats> gcStats,

                // Pause percentiles
                double pauseP50Ms, double pauseP95Ms, double pauseP99Ms, double pauseP999Ms, double pauseMaxMs, double pauseAvgMs,

                // Pause counts
                long totalPauseCount, long totalPauseTimeMs,

                // SLA violations
                long slaViolations10ms, long slaViolations50ms, long slaViolations100ms, int pauseSampleSize) {
    /**
     * Creates response from collected GC data.
     */
    public static GCComparisonResponse from(String instanceName, String jvmVendor, String jvmName, String gcCollectors, boolean isAzulC4, long heapSizeMB,
                    String allocationMode, int allocationRateMBps, long messageRate, List<GCStats> gcStats, GCPauseStats pauseStats) {
        return new GCComparisonResponse(instanceName, jvmVendor, jvmName, gcCollectors, isAzulC4, heapSizeMB, allocationMode, allocationRateMBps, messageRate,
                        gcStats, pauseStats.p50Ms, pauseStats.p95Ms, pauseStats.p99Ms, pauseStats.p999Ms, pauseStats.maxMs, pauseStats.avgPauseMs,
                        pauseStats.totalPauseCount, pauseStats.totalPauseTimeMs, pauseStats.violationsOver10ms, pauseStats.violationsOver50ms,
                        pauseStats.violationsOver100ms, pauseStats.sampleSize);
    }
}
