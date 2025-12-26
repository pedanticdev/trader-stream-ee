package fish.payara.trader.monitoring;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

@ApplicationScoped
public class SLAMonitorService {

  private static final Logger LOGGER = Logger.getLogger(SLAMonitorService.class.getName());

  private static final long SLA_10MS = 10;
  private static final long SLA_50MS = 50;
  private static final long SLA_100MS = 100;

  private final AtomicLong violationsOver10ms = new AtomicLong(0);
  private final AtomicLong violationsOver50ms = new AtomicLong(0);
  private final AtomicLong violationsOver100ms = new AtomicLong(0);
  private final AtomicLong totalOperations = new AtomicLong(0);

  private final ConcurrentHashMap<Long, Long> violationsByMinute = new ConcurrentHashMap<>();

  public void recordOperation(long latencyMs) {
    totalOperations.incrementAndGet();

    if (latencyMs > SLA_100MS) {
      violationsOver100ms.incrementAndGet();
      violationsOver50ms.incrementAndGet();
      violationsOver10ms.incrementAndGet();
      recordViolation();
    } else if (latencyMs > SLA_50MS) {
      violationsOver50ms.incrementAndGet();
      violationsOver10ms.incrementAndGet();
      recordViolation();
    } else if (latencyMs > SLA_10MS) {
      violationsOver10ms.incrementAndGet();
      recordViolation();
    }
  }

  private void recordViolation() {
    long currentMinute = System.currentTimeMillis() / 60000;
    violationsByMinute.merge(currentMinute, 1L, Long::sum);

    long fiveMinutesAgo = currentMinute - 5;
    violationsByMinute.keySet().removeIf(minute -> minute < fiveMinutesAgo);
  }

  public SLAStats getStats() {
    long total = totalOperations.get();

    return new SLAStats(
        total,
        violationsOver10ms.get(),
        violationsOver50ms.get(),
        violationsOver100ms.get(),
        total > 0 ? (double) violationsOver10ms.get() / total * 100 : 0,
        violationsByMinute.values().stream().mapToLong(Long::longValue).sum());
  }

  public void reset() {
    violationsOver10ms.set(0);
    violationsOver50ms.set(0);
    violationsOver100ms.set(0);
    totalOperations.set(0);
    violationsByMinute.clear();
    LOGGER.info("SLA statistics reset");
  }

  public static class SLAStats {
    public final long totalOperations;
    public final long violationsOver10ms;
    public final long violationsOver50ms;
    public final long violationsOver100ms;
    public final double violationRate;
    public final long recentViolations; // Last 5 minutes

    public SLAStats(
        long totalOperations,
        long violationsOver10ms,
        long violationsOver50ms,
        long violationsOver100ms,
        double violationRate,
        long recentViolations) {
      this.totalOperations = totalOperations;
      this.violationsOver10ms = violationsOver10ms;
      this.violationsOver50ms = violationsOver50ms;
      this.violationsOver100ms = violationsOver100ms;
      this.violationRate = violationRate;
      this.recentViolations = recentViolations;
    }
  }
}
