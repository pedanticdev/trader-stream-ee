package fish.payara.trader.utils;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Utility class for GC testing and simulation */
public class GCTestUtil {

    private static final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private static final List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();

    /** Captures initial GC statistics */
    public static GCStatistics captureInitialStats() {
        long totalCollections = 0;
        long totalTime = 0;

        for (GarbageCollectorMXBean gcBean : gcBeans) {
            totalCollections += gcBean.getCollectionCount();
            totalTime += gcBean.getCollectionTime();
        }

        return new GCStatistics(totalCollections, totalTime, getHeapUsage());
    }

    /** Calculates GC statistics after a test */
    public static GCStatistics calculateDelta(GCStatistics initial) {
        long totalCollections = 0;
        long totalTime = 0;

        for (GarbageCollectorMXBean gcBean : gcBeans) {
            totalCollections += gcBean.getCollectionCount();
            totalTime += gcBean.getCollectionTime();
        }

        return new GCStatistics(totalCollections - initial.collections, totalTime - initial.time, getHeapUsage());
    }

    /** Gets current heap usage */
    public static MemoryUsage getHeapUsage() {
        return memoryBean.getHeapMemoryUsage();
    }

    /** Allocates memory to trigger GC */
    public static void allocateMemory(int sizeMB) {
        allocateMemory(sizeMB, 10);
    }

    /** Allocates memory with controlled pattern */
    public static void allocateMemory(int sizeMB, int chunks) {
        int chunkSize = (sizeMB * 1024 * 1024) / chunks;
        List<byte[]> allocations = new ArrayList<>();

        try {
            for (int i = 0; i < chunks; i++) {
                allocations.add(new byte[chunkSize]);

                // Small delay to simulate real usage pattern
                if (i % 10 == 0) {
                    Thread.yield();
                }
            }
        } finally {
            // Let allocations go out of scope to trigger GC
            allocations.clear();
        }
    }

    /** Creates memory pressure in background thread */
    public static MemoryPressureThread createMemoryPressure(int allocationRateMB, int durationSeconds) {
        MemoryPressureThread pressureThread = new MemoryPressureThread(allocationRateMB, durationSeconds);
        return pressureThread;
    }

    /** Forces a garbage collection and waits for completion */
    public static void forceGC() {
        System.gc();
        System.runFinalization();

        // Small delay to allow GC to complete
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Measures GC pause time for a specific operation */
    public static long measurePauseTime(Runnable operation) {
        GCStatistics before = captureInitialStats();

        long startTime = System.nanoTime();
        operation.run();
        long endTime = System.nanoTime();

        GCStatistics after = calculateDelta(before);

        return after.time;
    }

    /** GC Statistics holder */
    public static class GCStatistics {
        public final long collections;
        public final long time;
        public final MemoryUsage heapUsage;

        public GCStatistics(long collections, long time, MemoryUsage heapUsage) {
            this.collections = collections;
            this.time = time;
            this.heapUsage = heapUsage;
        }

        @Override
        public String toString() {
            return String.format("GC{collections=%d, time=%dms, heap=%s}", collections, time, heapUsage);
        }
    }

    /** Background thread for creating controlled memory pressure */
    public static class MemoryPressureThread extends Thread {
        private final int allocationRateMB;
        private final int durationSeconds;
        private final AtomicLong allocatedBytes = new AtomicLong(0);
        private volatile boolean running = true;

        public MemoryPressureThread(int allocationRateMB, int durationSeconds) {
            this.allocationRateMB = allocationRateMB;
            this.durationSeconds = durationSeconds;
            setDaemon(true);
            setName("MemoryPressureThread");
        }

        @Override
        public void run() {
            long startTime = System.currentTimeMillis();
            long endTime = startTime + (durationSeconds * 1000L);

            int chunkSize = 1024 * 1024; // 1MB chunks
            int allocationsPerSecond = allocationRateMB;
            long allocationInterval = 1000L / allocationsPerSecond; // milliseconds

            List<byte[]> allocations = new ArrayList<>();

            while (running && System.currentTimeMillis() < endTime) {
                try {
                    allocations.add(new byte[chunkSize]);
                    allocatedBytes.addAndGet(chunkSize);

                    // Control allocation rate
                    if (allocationInterval > 0) {
                        Thread.sleep(allocationInterval);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // Clear allocations to trigger GC
            allocations.clear();
            allocatedBytes.set(0);
        }

        public void stopPressure() {
            running = false;
            interrupt();
        }

        public long getAllocatedBytes() {
            return allocatedBytes.get();
        }
    }

    /** Wait for GC to occur within timeout */
    public static boolean waitForGC(long timeoutMillis) {
        GCStatistics initial = captureInitialStats();
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            GCStatistics current = calculateDelta(initial);
            if (current.collections > 0) {
                return true;
            }

            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return false;
    }
}
