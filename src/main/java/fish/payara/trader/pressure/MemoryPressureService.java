package fish.payara.trader.pressure;

import fish.payara.trader.concurrency.VirtualThreadExecutor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
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

  private long totalBytesAllocated = 0;
  private long lastStatsTime = System.currentTimeMillis();

  private final List<byte[]> tenuredObjects = new CopyOnWriteArrayList<>();
  private static final int TENURED_TARGET_MB = 1024;
  private final AtomicLong tenuredBytesAllocated = new AtomicLong(0);

  @Inject @VirtualThreadExecutor private ManagedExecutorService executorService;

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
    totalBytesAllocated = 0;
    lastStatsTime = System.currentTimeMillis();

    pressureTask =
        executorService.submit(
            () -> {
              LOGGER.info("Memory pressure generator started with mode: " + currentMode);

              while (running) {
                try {
                  AllocationMode mode = currentMode;
                  if (mode == AllocationMode.OFF) {
                    break;
                  }

                  generateGarbage(mode);

                  Thread.sleep(100);

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
    int allocations = mode.getAllocationsPerIteration();
    int bytesPerAlloc = mode.getBytesPerAllocation();

    for (int i = 0; i < allocations; i++) {
      int pattern = i % 4;

      switch (pattern) {
        case 0:
          generateStringGarbage(bytesPerAlloc);
          break;
        case 1:
          generateByteArrayGarbage(bytesPerAlloc);
          break;
        case 2:
          generateObjectGarbage(bytesPerAlloc / 64);
          break;
        case 3:
          generateCollectionGarbage(bytesPerAlloc / 100);
          break;
      }

      if (mode == AllocationMode.HIGH || mode == AllocationMode.EXTREME) {
        int chance = (mode == AllocationMode.EXTREME) ? 10 : 2;
        if (ThreadLocalRandom.current().nextInt(10000) < chance) {
          byte[] longLived = new byte[1024 * 1024];
          ThreadLocalRandom.current().nextBytes(longLived);
          tenuredObjects.add(longLived);
          tenuredBytesAllocated.addAndGet(1024 * 1024);
        }
      }

      totalBytesAllocated += bytesPerAlloc;
    }

    if (mode == AllocationMode.HIGH || mode == AllocationMode.EXTREME) {
      while (tenuredBytesAllocated.get() > TENURED_TARGET_MB * 1024L * 1024L) {
        if (!tenuredObjects.isEmpty()) {
          tenuredObjects.remove(0);
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

  private void generateStringGarbage(int bytes) {
    StringBuilder sb = new StringBuilder(bytes);
    for (int i = 0; i < bytes / 10; i++) {
      sb.append("GARBAGE");
    }
    String garbage = sb.toString();
  }

  private void generateByteArrayGarbage(int bytes) {
    byte[] garbage = new byte[bytes];
    ThreadLocalRandom.current().nextBytes(garbage);
  }

  private void generateObjectGarbage(int count) {
    List<DummyObject> garbage = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      garbage.add(new DummyObject(i, "data-" + i, System.nanoTime()));
    }
  }

  private void generateCollectionGarbage(int count) {
    List<Integer> garbage = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      garbage.add(ThreadLocalRandom.current().nextInt());
    }
  }

  private void logStats() {
    long now = System.currentTimeMillis();
    if (now - lastStatsTime >= 5000) {
      double elapsedSeconds = (now - lastStatsTime) / 1000.0;
      double mbPerSec = (totalBytesAllocated / (1024.0 * 1024.0)) / elapsedSeconds;

      LOGGER.info(
          String.format(
              "Memory Pressure Stats - Mode: %s | Allocated: %.2f MB/sec | Tenured: %d MB (%d objects)",
              currentMode, mbPerSec, getTenuredObjectsMB(), getTenuredObjectCount()));

      totalBytesAllocated = 0;
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

  private static class DummyObject {
    private final int id;
    private final String data;
    private final long timestamp;

    DummyObject(int id, String data, long timestamp) {
      this.id = id;
      this.data = data;
      this.timestamp = timestamp;
    }
  }
}
