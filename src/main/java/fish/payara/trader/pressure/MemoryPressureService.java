package fish.payara.trader.pressure;

import fish.payara.trader.concurrency.VirtualThreadExecutor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Service to generate controlled memory pressure for GC stress testing.
 * Based on 1BRC techniques - intentional allocation to demonstrate GC behavior.
 */
@ApplicationScoped
public class MemoryPressureService {

    private static final Logger LOGGER = Logger.getLogger(MemoryPressureService.class.getName());

    private volatile AllocationMode currentMode = AllocationMode.OFF;
    private volatile boolean running = false;
    private Future<?> pressureTask;

    private long totalBytesAllocated = 0;
    private long lastStatsTime = System.currentTimeMillis();

    @Inject
    @VirtualThreadExecutor
    private ManagedExecutorService executorService;

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

        pressureTask = executorService.submit(() -> {
            LOGGER.info("Memory pressure generator started with mode: " + currentMode);

            while (running) {
                try {
                    AllocationMode mode = currentMode;
                    if (mode == AllocationMode.OFF) {
                        break;
                    }

                    // Generate garbage for this iteration
                    generateGarbage(mode);

                    // Sleep 100ms between iterations (10 iterations/sec)
                    Thread.sleep(100);

                    // Log stats every 5 seconds
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
            // Mix different allocation patterns
            int pattern = i % 4;

            switch (pattern) {
                case 0:
                    // String allocations (most common in real apps)
                    generateStringGarbage(bytesPerAlloc);
                    break;
                case 1:
                    // Byte array allocations
                    generateByteArrayGarbage(bytesPerAlloc);
                    break;
                case 2:
                    // Object allocations
                    generateObjectGarbage(bytesPerAlloc / 64); // ~64 bytes per object
                    break;
                case 3:
                    // Collection allocations
                    generateCollectionGarbage(bytesPerAlloc / 100); // ~100 bytes per list item
                    break;
            }

            totalBytesAllocated += bytesPerAlloc;
        }
    }

    private void generateStringGarbage(int bytes) {
        // Create strings via concatenation (generates intermediate garbage)
        StringBuilder sb = new StringBuilder(bytes);
        for (int i = 0; i < bytes / 10; i++) {
            sb.append("GARBAGE");
        }
        String garbage = sb.toString();
        // String is now eligible for GC
    }

    private void generateByteArrayGarbage(int bytes) {
        byte[] garbage = new byte[bytes];
        // Fill with random data to prevent compiler optimization
        ThreadLocalRandom.current().nextBytes(garbage);
        // Array is now eligible for GC
    }

    private void generateObjectGarbage(int count) {
        List<DummyObject> garbage = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            garbage.add(new DummyObject(i, "data-" + i, System.nanoTime()));
        }
        // List and objects are now eligible for GC
    }

    private void generateCollectionGarbage(int count) {
        List<Integer> garbage = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            garbage.add(ThreadLocalRandom.current().nextInt());
        }
        // List is now eligible for GC
    }

    private void logStats() {
        long now = System.currentTimeMillis();
        if (now - lastStatsTime >= 5000) {
            double elapsedSeconds = (now - lastStatsTime) / 1000.0;
            double mbPerSec = (totalBytesAllocated / (1024.0 * 1024.0)) / elapsedSeconds;

            LOGGER.info(String.format(
                "Memory Pressure Stats - Mode: %s | Allocated: %.2f MB | Rate: %.2f MB/sec",
                currentMode, totalBytesAllocated / (1024.0 * 1024.0), mbPerSec
            ));

            totalBytesAllocated = 0;
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

    /**
     * Dummy object for allocation testing
     */
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
