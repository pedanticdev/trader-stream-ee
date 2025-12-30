package fish.payara.trader.gc;

import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;

@ApplicationScoped
public class GCStatsService implements NotificationListener {

    private static final Logger LOGGER = Logger.getLogger(GCStatsService.class.getName());
    private static final int MAX_PAUSE_HISTORY = 1000;

    private final Map<String, ConcurrentLinkedDeque<Long>> pauseHistory = new HashMap<>();
    private final Map<String, ConcurrentLinkedDeque<GCPhaseBreakdown>> phaseHistory = new HashMap<>();

    /**
     * Breakdown of GC pause into constituent phases (e.g., Mark, Relocate, Evacuate).
     *
     * <p>
     * Not all JVMs expose detailed phase information via GcInfo. When unavailable, the breakdown will contain only a "Total" phase with the full pause
     * duration. This is a best-effort extraction that varies by GC implementation (G1, C4, ZGC, etc.).
     */
    public static class GCPhaseBreakdown {
        public final long totalDurationMs;
        public final Map<String, Long> phaseDurationsMs;
        public final long timestamp;

        public GCPhaseBreakdown(long totalDurationMs, Map<String, Long> phaseDurationsMs, long timestamp) {
            this.totalDurationMs = totalDurationMs;
            this.phaseDurationsMs = Map.copyOf(phaseDurationsMs); // Immutable copy
            this.timestamp = timestamp;
        }
    }

    /**
     * Statistics for a single GC phase across multiple collections.
     *
     * <p>
     * Provides percentile distribution of phase durations to identify which phases dominate pause times.
     */
    public static class PhaseStats {
        public final String phaseName;
        public final long count;
        public final long p50Ms;
        public final long p95Ms;
        public final long p99Ms;
        public final long maxMs;

        public PhaseStats(String phaseName, long count, long p50Ms, long p95Ms, long p99Ms, long maxMs) {
            this.phaseName = phaseName;
            this.count = count;
            this.p50Ms = p50Ms;
            this.p95Ms = p95Ms;
            this.p99Ms = p99Ms;
            this.maxMs = maxMs;
        }
    }

    @PostConstruct
    public void init() {
        LOGGER.info("Initializing GC Notification Listener...");
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            LOGGER.info("Registering listener for GC Bean: " + gcBean.getName());
            if (gcBean instanceof NotificationEmitter) {
                ((NotificationEmitter) gcBean).addNotificationListener(this, null, null);
            }
        }
    }

    @Override
    public void handleNotification(Notification notification, Object handback) {
        if (notification.getType().equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION)) {
            GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from((CompositeData) notification.getUserData());

            String gcName = info.getGcName();
            String gcAction = info.getGcAction();
            String gcCause = info.getGcCause();
            GcInfo gcInfo = info.getGcInfo();
            long duration = gcInfo.getDuration();

            if ("GPGC".equals(gcName)) {
                return;
            }

            if (gcName.contains("Cycles") && !gcName.contains("Pauses")) {
                return;
            }

            ConcurrentLinkedDeque<Long> history = pauseHistory.computeIfAbsent(gcName, k -> new ConcurrentLinkedDeque<>());
            history.addLast(duration);
            while (history.size() > MAX_PAUSE_HISTORY) {
                history.removeFirst();
            }

            Map<String, Long> phaseTimes = extractPhaseTimes(gcInfo, gcName, gcAction);
            GCPhaseBreakdown breakdown = new GCPhaseBreakdown(duration, phaseTimes, System.currentTimeMillis());

            ConcurrentLinkedDeque<GCPhaseBreakdown> phaseHist = phaseHistory.computeIfAbsent(gcName, k -> new ConcurrentLinkedDeque<>());
            phaseHist.addLast(breakdown);
            while (phaseHist.size() > MAX_PAUSE_HISTORY) {
                phaseHist.removeFirst();
            }

            if (duration > 10) {
                LOGGER.info(String.format("GC Pause detected: %s | Action: %s | Cause: %s | Duration: %d ms", gcName, gcAction, gcCause, duration));
            }
        }
    }

    /**
     * Extracts GC phase timings from GcInfo metadata.
     *
     * <p>
     * This is JVM and collector-specific - detailed phase data may not be available on all platforms. The standard GcInfo API does not expose phase-level
     * timing; vendors may provide this via custom MBeans or extended attributes.
     *
     * <p>
     * For production-grade phase analysis, consider parsing GC logs with -Xlog:gc*=debug or using vendor-specific monitoring tools.
     *
     * @param gcInfo
     *            GC information from notification
     * @param gcName
     *            Name of garbage collector (e.g., "G1 Young Generation", "GPGC Pauses")
     * @param gcAction
     *            GC action string (e.g., "end of minor GC", "end of major GC")
     * @return Map of phase names to durations in milliseconds. Contains at minimum a "Total" entry with the full pause duration.
     */
    private Map<String, Long> extractPhaseTimes(GcInfo gcInfo, String gcName, String gcAction) {
        Map<String, Long> phases = new HashMap<>();

        try {
            // Fallback: Always include total duration as baseline
            phases.put("Total", gcInfo.getDuration());

            // Vendor-specific extraction would go here
            // GcInfo doesn't expose phases in the standard API
            //
            // For G1GC: Would need to parse GC logs or use JFR events
            // For C4: Would need Azul-specific MBeans
            // For ZGC/Shenandoah: Would need to parse logs or use vendor tools
            //
            // Current implementation provides "Total" phase as a foundation.
            // Future enhancement: Add vendor-specific extraction logic.

        } catch (Exception e) {
            LOGGER.fine("Could not extract GC phase breakdown: " + e.getMessage());
            phases.clear();
            phases.put("Total", gcInfo.getDuration());
        }

        return phases;
    }

    public List<GCStats> collectGCStats() {
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();

        List<GCStats> statsList = new ArrayList<>();

        for (GarbageCollectorMXBean gcBean : gcBeans) {
            String gcName = gcBean.getName();

            // Exclude concurrent cycle collectors from the public stats to avoid confusion
            if ("GPGC".equals(gcName) || (gcName.contains("Cycles") && !gcName.contains("Pauses"))) {
                continue;
            }

            GCStats stats = new GCStats();
            stats.setGcName(gcName);
            stats.setCollectionCount(gcBean.getCollectionCount());
            stats.setCollectionTime(gcBean.getCollectionTime());

            ConcurrentLinkedDeque<Long> history = pauseHistory.get(gcName);
            if (history != null && !history.isEmpty()) {
                List<Long> pauses = new ArrayList<>(history);
                stats.setLastPauseDuration(pauses.get(pauses.size() - 1));

                stats.setRecentPauses(pauses.subList(Math.max(0, pauses.size() - 100), pauses.size()));

                List<Long> sortedPauses = pauses.stream().sorted().collect(Collectors.toList());

                stats.setPercentiles(calculatePercentiles(sortedPauses));
            } else {
                stats.setLastPauseDuration(0);
                stats.setRecentPauses(Collections.emptyList());
                stats.setPercentiles(new GCStats.PausePercentiles(0, 0, 0, 0, 0));
            }

            stats.setTotalMemory(heapUsage.getMax());
            stats.setUsedMemory(heapUsage.getUsed());
            stats.setFreeMemory(heapUsage.getMax() - heapUsage.getUsed());

            // Add phase breakdown if available
            ConcurrentLinkedDeque<GCPhaseBreakdown> phaseHist = phaseHistory.get(gcName);
            if (phaseHist != null && !phaseHist.isEmpty()) {
                stats.setPhaseBreakdown(calculatePhaseStats(new ArrayList<>(phaseHist)));
            }

            statsList.add(stats);
        }

        return statsList;
    }

    private GCStats.PausePercentiles calculatePercentiles(List<Long> sortedPauses) {
        if (sortedPauses.isEmpty()) {
            return new GCStats.PausePercentiles(0, 0, 0, 0, 0);
        }

        int size = sortedPauses.size();
        return new GCStats.PausePercentiles(percentile(sortedPauses, 0.50), percentile(sortedPauses, 0.95), percentile(sortedPauses, 0.99),
                        percentile(sortedPauses, 0.999), sortedPauses.get(size - 1));
    }

    private long percentile(List<Long> sortedValues, double percentile) {
        int index = (int) Math.ceil(percentile * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(index);
    }

    /**
     * Calculates per-phase statistics from breakdown history.
     *
     * <p>
     * Groups phase durations by phase name and computes percentiles for each phase independently. This allows identifying which phases contribute most to pause
     * times (e.g., "Mark" vs "Evacuate" in G1GC).
     *
     * @param breakdowns
     *            List of GC phase breakdowns from notification history
     * @return Map of phase names to statistics (count, percentiles)
     */
    private Map<String, PhaseStats> calculatePhaseStats(List<GCPhaseBreakdown> breakdowns) {
        Map<String, PhaseStats> phaseStatsMap = new HashMap<>();

        if (breakdowns.isEmpty()) {
            return phaseStatsMap;
        }

        // Group durations by phase name
        Map<String, List<Long>> phaseGroups = new HashMap<>();
        for (GCPhaseBreakdown breakdown : breakdowns) {
            for (Map.Entry<String, Long> entry : breakdown.phaseDurationsMs.entrySet()) {
                phaseGroups.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(entry.getValue());
            }
        }

        // Calculate percentiles for each phase
        for (Map.Entry<String, List<Long>> entry : phaseGroups.entrySet()) {
            String phaseName = entry.getKey();
            List<Long> durations = entry.getValue();
            Collections.sort(durations);

            PhaseStats stats = new PhaseStats(phaseName, durations.size(), percentile(durations, 0.50), percentile(durations, 0.95),
                            percentile(durations, 0.99), durations.get(durations.size() - 1) // max
            );

            phaseStatsMap.put(phaseName, stats);
        }

        return phaseStatsMap;
    }

    public void resetStats() {
        pauseHistory.clear();
        phaseHistory.clear();
        LOGGER.info("GC statistics reset");
    }
}
