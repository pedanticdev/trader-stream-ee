package fish.payara.trader.pressure;

import fish.payara.trader.pressure.patterns.AllocationPattern;
import fish.payara.trader.pressure.patterns.HFTPatternRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Service to generate controlled memory pressure for GC stress testing. Based on 1BRC techniques -
 * intentional allocation to demonstrate GC behavior.
 */
@ApplicationScoped
public class MemoryPressureService {

  private static final Logger LOGGER = Logger.getLogger(MemoryPressureService.class.getName());

  private volatile AllocationMode currentMode = AllocationMode.OFF;
  private volatile boolean running = false;
  private Future<?> pressureTask;

  private final AtomicLong totalBytesAllocated = new AtomicLong(0);
  private long lastStatsTime = System.currentTimeMillis();

  private final List<byte[]> tenuredObjects = new CopyOnWriteArrayList<>();
  private static final int TENURED_TARGET_MB = 1024;
  private final AtomicLong tenuredBytesAllocated = new AtomicLong(0);

  private volatile long lastBurstTime = 0;
  private static final long BURST_INTERVAL_MS = 5000; // 5 seconds
  private static final int BURST_MULTIPLIER = 3;
  @Resource private ManagedExecutorService executorService;
  @Inject private HFTPatternRegistry patternRegistry;

  @PostConstruct
  public void init() {
    LOGGER.info("MemoryPressureService initialized");
  }

  public synchronized void setAllocationMode(AllocationMode mode) {
    if (mode == currentMode) {
      return;
    }

    LOGGER.info("Changing allocation mode from " + currentMode + " to " + mode);
    currentMode = mode;

    if (mode == AllocationMode.OFF) {
      stopPressure();
    } else {
      startPressure();
    }
  }

  private synchronized void startPressure() {
    if (running) {
      return;
    }

    running = true;
    totalBytesAllocated.set(0);
    lastStatsTime = System.currentTimeMillis();

    pressureTask =
        executorService.submit(
            () -> {
              LOGGER.info("Memory pressure generator started with mode: " + currentMode);

              while (running) {
                try {
                  long loopStartTime = System.currentTimeMillis();
                  AllocationMode mode = currentMode;
                  if (mode == AllocationMode.OFF) {
                    break;
                  }

                  generateGarbage(mode);

                  long executionTime = System.currentTimeMillis() - loopStartTime;
                  long sleepTime = 100 - executionTime;

                  if (sleepTime > 0) {
                    Thread.sleep(sleepTime);
                  }

                  logStats();

                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  break;
                } catch (Exception e) {
                  LOGGER.warning("Error in memory pressure generator: " + e.getMessage());
                }
              }

              LOGGER.info("Memory pressure generator stopped");
            });
  }

  private synchronized void stopPressure() {
    running = false;
    if (pressureTask != null && !pressureTask.isDone()) {
      pressureTask.cancel(true);
      pressureTask = null;
    }
  }

  private void generateGarbage(AllocationMode mode) {
    int baseAllocations = mode.getAllocationsPerIteration();
    int bytesPerAlloc = mode.getBytesPerAllocation();

    // Burst detection for HIGH and EXTREME modes (simulates market events)
    boolean inBurst = false;
    if (mode != AllocationMode.OFF) {
      long now = System.currentTimeMillis();
      if (now - lastBurstTime >= BURST_INTERVAL_MS) {
        inBurst = true;
        lastBurstTime = now;
        LOGGER.info(
            String.format(
                "Burst triggered - %dx allocation for %s mode (simulating market event)",
                BURST_MULTIPLIER, mode.name()));
      }
    }

    int totalAllocations = inBurst ? baseAllocations * BURST_MULTIPLIER : baseAllocations;

    // Parallelize allocation using virtual threads
    int numTasks = 4;
    int allocationsPerTask = totalAllocations / numTasks;
    CompletableFuture<?>[] futures = new CompletableFuture[numTasks];

    for (int t = 0; t < numTasks; t++) {
      final int taskAllocations =
          (t == numTasks - 1)
              ? totalAllocations - (allocationsPerTask * (numTasks - 1))
              : allocationsPerTask;

      futures[t] =
          CompletableFuture.runAsync(
              () -> {
                for (int i = 0; i < taskAllocations; i++) {
                  try {
                    AllocationPattern pattern = patternRegistry.selectPattern();
                    pattern.allocate(bytesPerAlloc);
                  } catch (Exception e) {
                    // Fail silently in parallel task to avoid flooding logs
                  }
                  totalBytesAllocated.addAndGet(bytesPerAlloc);
                }
              },
              executorService);
    }

    CompletableFuture.allOf(futures).join();

    if (mode == AllocationMode.HIGH || mode == AllocationMode.EXTREME) {
      int chance = (mode == AllocationMode.EXTREME) ? 100 : 20;
      if (ThreadLocalRandom.current().nextInt(10000) < chance) {
        byte[] longLived = new byte[1024 * 1024];
        ThreadLocalRandom.current().nextBytes(longLived);
        tenuredObjects.add(longLived);
        tenuredBytesAllocated.addAndGet(1024 * 1024);
      }
    }

    // Tenured cleanup logic (unchanged - maintains 1GB cap)
    if (mode == AllocationMode.HIGH || mode == AllocationMode.EXTREME) {
      while (tenuredBytesAllocated.get() > TENURED_TARGET_MB * 1024L * 1024L) {
        if (!tenuredObjects.isEmpty()) {
          tenuredObjects.removeFirst();
          tenuredBytesAllocated.addAndGet(-1024 * 1024);
        } else {
          break;
        }
      }
    } else if (mode == AllocationMode.OFF || mode == AllocationMode.LOW) {
      if (!tenuredObjects.isEmpty()) {
        tenuredObjects.clear();
        tenuredBytesAllocated.set(0);
      }
    }
  }

  private void logStats() {
    long now = System.currentTimeMillis();
    if (now - lastStatsTime >= 5000) {
      double elapsedSeconds = (now - lastStatsTime) / 1000.0;
      long allocated = totalBytesAllocated.getAndSet(0);
      double mbPerSec = (allocated / (1024.0 * 1024.0)) / elapsedSeconds;

      LOGGER.info(
          String.format(
              "Memory Pressure Stats - Mode: %s | Allocated: %.2f MB/sec | Tenured: %d MB (%d objects)",
              currentMode, mbPerSec, getTenuredObjectsMB(), getTenuredObjectCount()));

      lastStatsTime = now;
    }
  }

  public long getTenuredObjectsMB() {
    return tenuredBytesAllocated.get() / (1024 * 1024);
  }

  public int getTenuredObjectCount() {
    return tenuredObjects.size();
  }

  @PreDestroy
  public void shutdown() {
    LOGGER.info("Shutting down MemoryPressureService");
    stopPressure();
  }

  public AllocationMode getCurrentMode() {
    return currentMode;
  }

  public boolean isRunning() {
    return running;
  }
}
