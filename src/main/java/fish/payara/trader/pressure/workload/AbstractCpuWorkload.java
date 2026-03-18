package fish.payara.trader.pressure.workload;

import fish.payara.trader.concurrency.VirtualThreadExecutor;
import fish.payara.trader.pressure.Workload;
import fish.payara.trader.pressure.WorkloadConfig;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.inject.Inject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public abstract class AbstractCpuWorkload implements Workload {

    protected final AtomicLong bytesAllocated = new AtomicLong(0);
    protected final AtomicLong operationsCompleted = new AtomicLong(0);
    protected WorkloadConfig config = new WorkloadConfig();

    @Inject
    @VirtualThreadExecutor
    protected ManagedExecutorService executorService;

    protected void executeMultiThreaded(int totalWork, int numThreads, java.util.function.Consumer<ThreadLocalRandom> work) {
        int workPerThread = totalWork / numThreads;
        CompletableFuture<?>[] futures = new CompletableFuture[numThreads];

        for (int t = 0; t < numThreads; t++) {
            futures[t] = CompletableFuture.runAsync(() -> {
                ThreadLocalRandom rng = ThreadLocalRandom.current();
                int remaining = workPerThread;
                while (remaining-- > 0) {
                    work.accept(rng);
                }
            }, executorService);
        }

        CompletableFuture.allOf(futures).join();
    }

    @Override
    public long bytesAllocated() {
        return bytesAllocated.get();
    }

    @Override
    public long operationsCompleted() {
        return operationsCompleted.get();
    }

    @Override
    public void reset() {
        bytesAllocated.set(0);
        operationsCompleted.set(0);
    }

    public void setConfig(WorkloadConfig config) {
        this.config = config;
    }
}
