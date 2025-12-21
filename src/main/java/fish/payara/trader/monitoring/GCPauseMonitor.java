package fish.payara.trader.monitoring;

import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;

/**
 * Real-time GC pause monitoring using JMX notifications. Captures individual GC events with exact
 * pause times (not averaged).
 */
@ApplicationScoped
public class GCPauseMonitor implements NotificationListener {

  private static final Logger LOGGER = Logger.getLogger(GCPauseMonitor.class.getName());

  // Keep last N pauses for percentile calculations (reactive window)
  private static final int MAX_PAUSE_HISTORY = 500;

  // Pause history (milliseconds)
  private final ConcurrentLinkedDeque<Long> pauseHistory = new ConcurrentLinkedDeque<>();

  // All-time statistics
  private final AtomicLong totalPauseCount = new AtomicLong(0);
  private final AtomicLong totalPauseTimeMs = new AtomicLong(0);
  private volatile long maxPauseMs = 0;

  // SLA violation counters (all-time)
  private final AtomicLong violationsOver10ms = new AtomicLong(0);
  private final AtomicLong violationsOver50ms = new AtomicLong(0);
  private final AtomicLong violationsOver100ms = new AtomicLong(0);

  private final List<NotificationEmitter> emitters = new ArrayList<>();

  @PostConstruct
  public void init() {
    LOGGER.info("Initializing GC Pause Monitor with JMX notifications");

    List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();

    for (GarbageCollectorMXBean gcBean : gcBeans) {
      if (gcBean instanceof NotificationEmitter) {
        NotificationEmitter emitter = (NotificationEmitter) gcBean;
        emitter.addNotificationListener(this, null, null);
        emitters.add(emitter);
        LOGGER.info("Registered GC notification listener for: " + gcBean.getName());
      }
    }

    if (emitters.isEmpty()) {
      LOGGER.warning("No GC notification emitters found - pause monitoring may be limited");
    }
  }

  @PreDestroy
  public void cleanup() {
    for (NotificationEmitter emitter : emitters) {
      try {
        emitter.removeNotificationListener(this);
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Failed to remove GC notification listener", e);
      }
    }
    emitters.clear();
  }

  @Override
  public void handleNotification(Notification notification, Object handback) {
    if (!notification
        .getType()
        .equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION)) {
      return;
    }

    try {
      CompositeData cd = (CompositeData) notification.getUserData();
      GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from(cd);
      String gcName = info.getGcName();

      // FILTERING LOGIC:
      // Azul C4 exposes "GPGC" (Concurrent Cycle) and "GPGC Pauses" (STW Pauses).
      // We MUST ignore "GPGC" because it reports cycle time (hundreds of ms) which is NOT a pause.
      if ("GPGC".equals(gcName) || (gcName.contains("Cycles") && !gcName.contains("Pauses"))) {
        return;
      }

      GcInfo gcInfo = info.getGcInfo();
      long pauseMs = gcInfo.getDuration();

      // Record pause
      recordPause(pauseMs, gcName, info.getGcAction());

    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Error processing GC notification", e);
    }
  }

  private void recordPause(long pauseMs, String gcName, String gcAction) {
    // Add to history
    pauseHistory.addLast(pauseMs);
    if (pauseHistory.size() > MAX_PAUSE_HISTORY) {
      pauseHistory.removeFirst();
    }

    // Update statistics
    totalPauseCount.incrementAndGet();
    totalPauseTimeMs.addAndGet(pauseMs);

    // Update max (thread-safe but may miss true max in race condition - acceptable for monitoring)
    if (pauseMs > maxPauseMs) {
      synchronized (this) {
        if (pauseMs > maxPauseMs) {
          maxPauseMs = pauseMs;
        }
      }
    }

    // Track SLA violations
    if (pauseMs > 100) {
      violationsOver100ms.incrementAndGet();
      violationsOver50ms.incrementAndGet();
      violationsOver10ms.incrementAndGet();
    } else if (pauseMs > 50) {
      violationsOver50ms.incrementAndGet();
      violationsOver10ms.incrementAndGet();
    } else if (pauseMs > 10) {
      violationsOver10ms.incrementAndGet();
    }

    // Log significant pauses
    if (pauseMs > 100) {
      LOGGER.warning(
          String.format("Large GC pause detected: %d ms [%s - %s]", pauseMs, gcName, gcAction));
    } else if (pauseMs > 50) {
      LOGGER.info(String.format("Notable GC pause: %d ms [%s - %s]", pauseMs, gcName, gcAction));
    }
  }

  public GCPauseStats getStats() {
    List<Long> pauses = new ArrayList<>(pauseHistory);

    if (pauses.isEmpty()) {
      return new GCPauseStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    Collections.sort(pauses);

    long p50 = percentile(pauses, 0.50);
    long p95 = percentile(pauses, 0.95);
    long p99 = percentile(pauses, 0.99);
    long p999 = percentile(pauses, 0.999);
    long max = pauses.get(pauses.size() - 1);

    long count = totalPauseCount.get();
    long totalTime = totalPauseTimeMs.get();
    double avgPause = count > 0 ? (double) totalTime / count : 0;

    return new GCPauseStats(
        count,
        totalTime,
        avgPause,
        p50,
        p95,
        p99,
        p999,
        maxPauseMs, // All-time max
        violationsOver10ms.get(),
        violationsOver50ms.get(),
        violationsOver100ms.get(),
        pauses.size() // Sample size for percentiles
        );
  }

  private long percentile(List<Long> sortedValues, double percentile) {
    int index = (int) Math.ceil(percentile * sortedValues.size()) - 1;
    index = Math.max(0, Math.min(index, sortedValues.size() - 1));
    return sortedValues.get(index);
  }

  public void reset() {
    pauseHistory.clear();
    totalPauseCount.set(0);
    totalPauseTimeMs.set(0);
    maxPauseMs = 0;
    violationsOver10ms.set(0);
    violationsOver50ms.set(0);
    violationsOver100ms.set(0);
    LOGGER.info("GC pause statistics reset");
  }

  public static class GCPauseStats {
    public final long totalPauseCount;
    public final long totalPauseTimeMs;
    public final double avgPauseMs;
    public final long p50Ms;
    public final long p95Ms;
    public final long p99Ms;
    public final long p999Ms;
    public final long maxMs; // All-time max since startup/reset
    public final long violationsOver10ms;
    public final long violationsOver50ms;
    public final long violationsOver100ms;
    public final int sampleSize;

    public GCPauseStats(
        long totalPauseCount,
        long totalPauseTimeMs,
        double avgPauseMs,
        long p50Ms,
        long p95Ms,
        long p99Ms,
        long p999Ms,
        long maxMs,
        long violationsOver10ms,
        long violationsOver50ms,
        long violationsOver100ms,
        int sampleSize) {
      this.totalPauseCount = totalPauseCount;
      this.totalPauseTimeMs = totalPauseTimeMs;
      this.avgPauseMs = avgPauseMs;
      this.p50Ms = p50Ms;
      this.p95Ms = p95Ms;
      this.p99Ms = p99Ms;
      this.p999Ms = p999Ms;
      this.maxMs = maxMs;
      this.violationsOver10ms = violationsOver10ms;
      this.violationsOver50ms = violationsOver50ms;
      this.violationsOver100ms = violationsOver100ms;
      this.sampleSize = sampleSize;
    }
  }
}
