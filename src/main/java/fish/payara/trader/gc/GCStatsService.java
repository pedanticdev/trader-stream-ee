package fish.payara.trader.gc;

import com.sun.management.GarbageCollectionNotificationInfo;
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
    if (notification
        .getType()
        .equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION)) {
      GarbageCollectionNotificationInfo info =
          GarbageCollectionNotificationInfo.from((CompositeData) notification.getUserData());

      String gcName = info.getGcName();
      String gcAction = info.getGcAction();
      String gcCause = info.getGcCause();
      long duration = info.getGcInfo().getDuration();

      // FILTERING LOGIC:
      // Azul C4 exposes "GPGC" (Concurrent Cycle) and "GPGC Pauses" (STW Pauses).
      // We MUST ignore "GPGC" because it reports cycle time (hundreds of ms) which is NOT a pause.
      if ("GPGC".equals(gcName)) {
        return;
      }

      // Also ignore other known concurrent cycle beans if they appear
      if (gcName.contains("Cycles") && !gcName.contains("Pauses")) {
        return;
      }

      // Only record if duration > 0 (sub-millisecond pauses might show as 0 or 1)
      // Storing all for fidelity.

      ConcurrentLinkedDeque<Long> history =
          pauseHistory.computeIfAbsent(gcName, k -> new ConcurrentLinkedDeque<>());

      history.addLast(duration);
      while (history.size() > MAX_PAUSE_HISTORY) {
        history.removeFirst();
      }

      if (duration > 10) {
        LOGGER.info(
            String.format(
                "GC Pause detected: %s | Action: %s | Cause: %s | Duration: %d ms",
                gcName, gcAction, gcCause, duration));
      }
    }
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
        sortedPauses.get(size - 1));
  }

  private long percentile(List<Long> sortedValues, double percentile) {
    int index = (int) Math.ceil(percentile * sortedValues.size()) - 1;
    index = Math.max(0, Math.min(index, sortedValues.size() - 1));
    return sortedValues.get(index);
  }

  public void resetStats() {
    pauseHistory.clear();
    LOGGER.info("GC statistics reset");
  }
}
