package fish.payara.trader.pressure;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Service to generate controlled memory pressure for GC stress testing. Updated to use
 * scenario-based testing targeting G1 vs C4 differences.
 */
@ApplicationScoped
public class MemoryPressureService {

  private static final Logger LOGGER = Logger.getLogger(MemoryPressureService.class.getName());

  private volatile AllocationMode currentMode = AllocationMode.OFF;
  private volatile boolean running = false;
  private Future<?> pressureTask;

  private final AtomicLong totalBytesAllocated = new AtomicLong(0);
  private long lastStatsTime = System.currentTimeMillis();

  // Live set management
  private final Deque<byte[]> liveSet = new LinkedList<>();
  private final AtomicLong liveSetBytesAllocated = new AtomicLong(0);

  // For Promotion Storm (thread-safe as multiple threads add to it)
  private final Deque<byte[]> promotableObjects = new ConcurrentLinkedDeque<>();

  // For Cross-Gen Refs scenario - holders in old gen that reference young objects
  private final Deque<RefHolder> crossRefHolders = new LinkedList<>();
  private final AtomicLong crossRefBytesAllocated = new AtomicLong(0);

  // Scenario state
  private final AtomicLong scenarioStartTime = new AtomicLong(0);

  /**
   * Holder object that lives in old generation and holds a reference to a young object. Every
   * update to youngRef triggers G1's write barrier and remembered set maintenance.
   */
  private static class RefHolder {
    volatile Object youngRef; // Reference to young gen object - updated frequently
    final byte[] padding; // Padding to make holder substantial (~1MB each)

    RefHolder(int paddingSize) {
      this.padding = new byte[paddingSize];
      ThreadLocalRandom.current().nextBytes(this.padding);
    }
  }

  @Resource private ManagedExecutorService executorService;

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
      // clear previous state
      liveSet.clear();
      liveSetBytesAllocated.set(0);
      promotableObjects.clear();
      crossRefHolders.clear();
      crossRefBytesAllocated.set(0);
      scenarioStartTime.set(System.currentTimeMillis());

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
              // Cleanup
              liveSet.clear();
              liveSetBytesAllocated.set(0);
              promotableObjects.clear();
              crossRefHolders.clear();
              crossRefBytesAllocated.set(0);
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
    switch (mode.getScenarioType()) {
      case STEADY:
        executeSteadyLoadScenario(mode);
        break;
      case GROWING:
        executeGrowingHeapScenario(mode);
        break;
      case PROMOTION:
        executePromotionStormScenario(mode);
        break;
      case FRAGMENTATION:
        executeFragmentationScenario(mode);
        break;
      case CROSS_REF:
        executeCrossRefsScenario(mode);
        break;
      default:
        // Do nothing for NONE
        break;
    }
  }

  private void executeSteadyLoadScenario(AllocationMode mode) {
    int targetMB = mode.getLiveSetSizeMB();
    int rateMBPerSec = mode.getAllocationRateMBPerSec();

    // Maintain stable live set (single-threaded)
    maintainLiveSet(targetMB);

    // Allocate transient garbage at specified rate across 4 threads
    // (100ms iteration = 10 iterations/sec)
    int bytesPerIteration = (rateMBPerSec * 1024 * 1024) / 10;

    allocateTransientGarbageMultiThreaded(bytesPerIteration, 4);
  }

  private void executeGrowingHeapScenario(AllocationMode mode) {
    long startTime = scenarioStartTime.get();
    long elapsed = (System.currentTimeMillis() - startTime) / 1000; // seconds

    int startMB = 100;
    int targetMB = mode.getLiveSetSizeMB();
    int duration = mode.getGrowthDurationSeconds();

    // Calculate current target based on linear growth
    int currentTargetMB =
        elapsed >= duration
            ? targetMB
            : startMB + (int) ((targetMB - startMB) * elapsed / duration);

    maintainLiveSet(currentTargetMB);

    // Allocate transient garbage
    int rateMBPerSec = mode.getAllocationRateMBPerSec();
    int bytesPerIteration = (rateMBPerSec * 1024 * 1024) / 10;
    allocateTransientGarbageMultiThreaded(bytesPerIteration, 4);
  }

  private void executePromotionStormScenario(AllocationMode mode) {
    int rateMBPerSec = mode.getAllocationRateMBPerSec();
    int bytesPerIteration = (rateMBPerSec * 1024 * 1024) / 10;

    // 50% of allocations should survive to old generation
    // Allocate half as transient, half as medium-lived
    int transientBytes = bytesPerIteration / 2;
    int promotableBytes = bytesPerIteration / 2;

    // Multi-threaded allocation for both transient and promotable
    allocateTransientGarbageMultiThreaded(transientBytes, 4);
    allocatePromotableGarbageMultiThreaded(promotableBytes, 4);

    // Move from thread-safe queue to local live set management
    byte[] promoted;
    while ((promoted = promotableObjects.poll()) != null) {
      liveSet.add(promoted);
      liveSetBytesAllocated.addAndGet(promoted.length);
    }

    // Now trim live set if needed (remove oldest)
    long targetBytes = mode.getLiveSetSizeMB() * 1024L * 1024L;
    while (liveSetBytesAllocated.get() > targetBytes && !liveSet.isEmpty()) {
      byte[] removed = liveSet.removeFirst();
      liveSetBytesAllocated.addAndGet(-removed.length);
    }
  }

  private void executeFragmentationScenario(AllocationMode mode) {
    int targetMB = mode.getLiveSetSizeMB();
    maintainLiveSet(targetMB);

    int rateMBPerSec = mode.getAllocationRateMBPerSec();
    int bytesPerIteration = (rateMBPerSec * 1024 * 1024) / 10;

    allocateFragmentationGarbageMultiThreaded(bytesPerIteration, 4);
  }

  private void executeCrossRefsScenario(AllocationMode mode) {
    // Maintain old gen holders (these will be promoted after surviving GCs)
    maintainCrossRefHolders(mode.getLiveSetSizeMB());

    int rateMBPerSec = mode.getAllocationRateMBPerSec();
    int bytesPerIteration = (rateMBPerSec * 1024 * 1024) / 10;

    // Create young objects and link them from old gen holders
    // This triggers G1's write barriers and remembered set updates
    createCrossGenerationalRefsMultiThreaded(bytesPerIteration, 4);
  }

  /**
   * Maintain RefHolder objects that will live in old generation. Each holder has ~1MB padding and a
   * reference slot for young objects.
   */
  private void maintainCrossRefHolders(int targetMB) {
    long targetBytes = targetMB * 1024L * 1024L;
    long currentBytes = crossRefBytesAllocated.get();

    // Add holders if below target
    while (currentBytes < targetBytes) {
      RefHolder holder = new RefHolder(1024 * 1024); // ~1MB each
      crossRefHolders.add(holder);
      currentBytes += 1024 * 1024;
      crossRefBytesAllocated.set(currentBytes);
    }

    // Remove holders if above target
    while (currentBytes > targetBytes && !crossRefHolders.isEmpty()) {
      crossRefHolders.removeFirst();
      currentBytes -= 1024 * 1024;
      crossRefBytesAllocated.set(currentBytes);
    }
  }

  /**
   * Create young objects and update old→young references. Each reference update triggers G1's write
   * barrier, which marks the card table and adds to remembered sets. This is the overhead we're
   * testing - C4 has no remembered sets.
   */
  private void createCrossGenerationalRefsMultiThreaded(int totalBytes, int numThreads) {
    if (crossRefHolders.isEmpty()) {
      return;
    }

    int bytesPerThread = totalBytes / numThreads;
    CompletableFuture<?>[] futures = new CompletableFuture[numThreads];

    // Convert to array for random access from multiple threads
    RefHolder[] holders = crossRefHolders.toArray(new RefHolder[0]);

    for (int t = 0; t < numThreads; t++) {
      futures[t] =
          CompletableFuture.runAsync(
              () -> {
                int remaining = bytesPerThread;
                while (remaining > 0) {
                  // Create young object (1-4KB each for variety)
                  int size = ThreadLocalRandom.current().nextInt(1024, 4097);
                  if (size > remaining) size = remaining;
                  byte[] youngObj = new byte[size];
                  ThreadLocalRandom.current().nextBytes(youngObj);

                  // Update random old gen holder to point to this young object
                  // This write triggers G1's post-write barrier:
                  // 1. Marks card table entry as dirty
                  // 2. Adds to remembered set for later scanning
                  int idx = ThreadLocalRandom.current().nextInt(holders.length);
                  holders[idx].youngRef = youngObj;

                  remaining -= size;
                }
                totalBytesAllocated.addAndGet(bytesPerThread);
              },
              executorService);
    }

    CompletableFuture.allOf(futures).join();
  }

  private void allocateTransientGarbageMultiThreaded(int totalBytes, int numThreads) {
    int bytesPerThread = totalBytes / numThreads;
    CompletableFuture<?>[] futures = new CompletableFuture[numThreads];

    for (int t = 0; t < numThreads; t++) {
      futures[t] =
          CompletableFuture.runAsync(
              () -> {
                // Each thread allocates its share
                byte[] garbage = new byte[bytesPerThread];
                ThreadLocalRandom.current().nextBytes(garbage);
                // Object is now eligible for GC
                totalBytesAllocated.addAndGet(bytesPerThread);
              },
              executorService);
    }

    // Wait for all threads to complete
    CompletableFuture.allOf(futures).join();
  }

  private void allocatePromotableGarbageMultiThreaded(int totalBytes, int numThreads) {
    int bytesPerThread = totalBytes / numThreads;
    CompletableFuture<?>[] futures = new CompletableFuture[numThreads];

    for (int t = 0; t < numThreads; t++) {
      futures[t] =
          CompletableFuture.runAsync(
              () -> {
                // Create objects that live long enough to be promoted
                byte[] obj = new byte[bytesPerThread];
                ThreadLocalRandom.current().nextBytes(obj);
                promotableObjects.add(obj);
                totalBytesAllocated.addAndGet(bytesPerThread);
              },
              executorService);
    }

    CompletableFuture.allOf(futures).join();
  }

  private void allocateFragmentationGarbageMultiThreaded(int totalBytes, int numThreads) {
    int bytesPerThread = totalBytes / numThreads;
    CompletableFuture<?>[] futures = new CompletableFuture[numThreads];

    for (int t = 0; t < numThreads; t++) {
      futures[t] =
          CompletableFuture.runAsync(
              () -> {
                int remaining = bytesPerThread;
                while (remaining > 0) {
                  // Small objects 100-1000 bytes
                  int size = ThreadLocalRandom.current().nextInt(100, 1001);
                  if (size > remaining) size = remaining;
                  byte[] garbage = new byte[size];
                  ThreadLocalRandom.current().nextBytes(garbage);
                  remaining -= size;
                }
                totalBytesAllocated.addAndGet(bytesPerThread);
              },
              executorService);
    }

    CompletableFuture.allOf(futures).join();
  }

  private void maintainLiveSet(int targetMB) {
    long targetBytes = targetMB * 1024L * 1024L;
    long currentBytes = liveSetBytesAllocated.get();

    // Add objects if below target
    while (currentBytes < targetBytes) {
      byte[] obj = new byte[1024 * 1024]; // 1 MB object
      ThreadLocalRandom.current().nextBytes(obj);
      liveSet.add(obj);
      currentBytes += 1024 * 1024;
      liveSetBytesAllocated.set(currentBytes);
    }

    // Remove objects if above target
    while (currentBytes > targetBytes && !liveSet.isEmpty()) {
      liveSet.removeFirst();
      currentBytes -= 1024 * 1024;
      liveSetBytesAllocated.set(currentBytes);
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
              "Memory Pressure Stats - Mode: %s | Allocated: %.2f MB/sec | Live Set: %d MB",
              currentMode, mbPerSec, liveSetBytesAllocated.get() / (1024 * 1024)));

      lastStatsTime = now;
    }
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
