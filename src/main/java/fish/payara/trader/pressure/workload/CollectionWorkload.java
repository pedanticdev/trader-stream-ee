package fish.payara.trader.pressure.workload;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

@ApplicationScoped
public class CollectionWorkload extends AbstractCpuWorkload {

    private static final int ENTRY_COUNT = 10_000;
    private static final int VALUE_SIZE = 256;
    private static final long ESTIMATED_NODE_BYTES = 64L;

    @Override
    public String name() {
        return "COLLECTION";
    }

    @Override
    public void execute(int iterations) {
        int numThreads = config.threadsPerWorkload();
        executeMultiThreaded(iterations, numThreads, this::executeSingleIteration);
    }

    private void executeSingleIteration(ThreadLocalRandom rng) {
        executeHashMapOps(ENTRY_COUNT, rng);
        executeTreeMapOps(ENTRY_COUNT, rng);
    }

    void executeHashMapOps(int count, ThreadLocalRandom rng) {
        Map<Integer, byte[]> map = new HashMap<>(count * 2);

        for (Map.Entry<Integer, byte[]> entry : generateRandomEntries(count, rng)) {
            map.put(entry.getKey(), entry.getValue());
        }
        bytesAllocated.addAndGet((long) count * ESTIMATED_NODE_BYTES);

        for (int i = 0; i < count; i++) {
            map.get(rng.nextInt(count));
        }

        for (int i = 0; i < count / 2; i++) {
            map.remove(rng.nextInt(count));
        }

        long sum = 0;
        for (Map.Entry<Integer, byte[]> entry : map.entrySet()) {
            sum += entry.getKey();
        }

        operationsCompleted.incrementAndGet();
    }

    void executeTreeMapOps(int count, ThreadLocalRandom rng) {
        Map<Integer, byte[]> map = new TreeMap<>();

        for (Map.Entry<Integer, byte[]> entry : generateRandomEntries(count, rng)) {
            map.put(entry.getKey(), entry.getValue());
        }
        bytesAllocated.addAndGet((long) count * (ESTIMATED_NODE_BYTES + 16L));

        for (int i = 0; i < count; i++) {
            map.get(rng.nextInt(count));
        }

        for (int i = 0; i < count / 2; i++) {
            map.remove(rng.nextInt(count));
        }

        long sum = 0;
        for (Map.Entry<Integer, byte[]> entry : map.entrySet()) {
            sum += entry.getKey();
        }

        operationsCompleted.incrementAndGet();
    }

    Map.Entry<Integer, byte[]>[] generateRandomEntries(int count, ThreadLocalRandom rng) {
        @SuppressWarnings("unchecked")
        Map.Entry<Integer, byte[]>[] entries = new Map.Entry[count];
        for (int i = 0; i < count; i++) {
            byte[] value = new byte[VALUE_SIZE];
            rng.nextBytes(value);
            final int key = rng.nextInt();
            entries[i] = Map.entry(key, value);
            bytesAllocated.addAndGet(value.length);
        }
        return entries;
    }
}
