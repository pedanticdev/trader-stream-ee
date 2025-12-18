package fish.payara.trader.gc;

import jakarta.enterprise.context.ApplicationScoped;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@ApplicationScoped
public class GCStatsService {

    private static final Logger LOGGER = Logger.getLogger(GCStatsService.class.getName());
    private static final int MAX_PAUSE_HISTORY = 1000;

    private final Map<String, ConcurrentLinkedDeque<Long>> pauseHistory = new HashMap<>();
    private final Map<String, Long> lastCollectionCount = new HashMap<>();
    private final Map<String, Long> lastCollectionTime = new HashMap<>();

    public List<GCStats> collectGCStats() {
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();

        List<GCStats> statsList = new ArrayList<>();

        for (GarbageCollectorMXBean gcBean : gcBeans) {
            String gcName = gcBean.getName();
            long currentCount = gcBean.getCollectionCount();
            long currentTime = gcBean.getCollectionTime();

            GCStats stats = new GCStats();
            stats.setGcName(gcName);
            stats.setCollectionCount(currentCount);
            stats.setCollectionTime(currentTime);

            // Calculate pause duration since last check
            Long prevCount = lastCollectionCount.get(gcName);
            Long prevTime = lastCollectionTime.get(gcName);

            if (prevCount != null && prevTime != null) {
                long countDelta = currentCount - prevCount;
                long timeDelta = currentTime - prevTime;

                if (countDelta > 0) {
                    long avgPauseDuration = timeDelta / countDelta;
                    stats.setLastPauseDuration(avgPauseDuration);

                    // Track pause history
                    ConcurrentLinkedDeque<Long> history = pauseHistory.computeIfAbsent(
                        gcName, k -> new ConcurrentLinkedDeque<>()
                    );

                    // Add new pauses (simplified - one entry per collection)
                    for (int i = 0; i < countDelta; i++) {
                        history.addLast(avgPauseDuration);
                        if (history.size() > MAX_PAUSE_HISTORY) {
                            history.removeFirst();
                        }
                    }
                }
            }

            // Update tracking
            lastCollectionCount.put(gcName, currentCount);
            lastCollectionTime.put(gcName, currentTime);

            // Get recent pauses
            ConcurrentLinkedDeque<Long> history = pauseHistory.get(gcName);
            if (history != null && !history.isEmpty()) {
                stats.setRecentPauses(new ArrayList<>(history).subList(
                    Math.max(0, history.size() - 100), history.size()
                ));

                // Calculate percentiles
                List<Long> sortedPauses = history.stream()
                    .sorted()
                    .collect(Collectors.toList());

                stats.setPercentiles(calculatePercentiles(sortedPauses));
            } else {
                stats.setRecentPauses(Collections.emptyList());
                stats.setPercentiles(new GCStats.PausePercentiles(0, 0, 0, 0, 0));
            }

            // Memory stats
            stats.setTotalMemory(heapUsage.getMax());
            stats.setUsedMemory(heapUsage.getUsed());
            stats.setFreeMemory(heapUsage.getMax() - heapUsage.getUsed());

            statsList.add(stats);
        }

        return statsList;
    }

    private GCStats.PausePercentiles calculatePercentiles(List<Long> sortedPauses) {
        if (sortedPauses.isEmpty()) {
            return new GCStats.PausePercentiles(0, 0, 0, 0, 0);
        }

        int size = sortedPauses.size();
        return new GCStats.PausePercentiles(
            percentile(sortedPauses, 0.50),
            percentile(sortedPauses, 0.95),
            percentile(sortedPauses, 0.99),
            percentile(sortedPauses, 0.999),
            sortedPauses.get(size - 1)
        );
    }

    private long percentile(List<Long> sortedValues, double percentile) {
        int index = (int) Math.ceil(percentile * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(index);
    }

    public void resetStats() {
        pauseHistory.clear();
        lastCollectionCount.clear();
        lastCollectionTime.clear();
        LOGGER.info("GC statistics reset");
    }
}
