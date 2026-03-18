package fish.payara.trader.matching.history;

import fish.payara.trader.matching.model.Execution;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import fish.payara.trader.matching.config.MatchingConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Fixed-capacity ring buffer for execution history. Pre-allocates storage to avoid GC pressure.
 */
@ApplicationScoped
public class ExecutionHistory {

    private static final Logger LOGGER = Logger.getLogger(ExecutionHistory.class.getName());

    private Execution[] buffer;
    private int maxSize;
    private int head = 0;
    private int count = 0;
    private ReentrantLock lock = new ReentrantLock();

    protected ExecutionHistory() {
        this.maxSize = 10000;
        this.buffer = new Execution[maxSize];
    }

    @Inject
    public ExecutionHistory(MatchingConfig config) {
        this.maxSize = config.maxHistorySize();
        this.buffer = new Execution[maxSize];
        LOGGER.info("Execution history initialized with capacity: " + maxSize);
    }

    public void append(Execution execution) {
        lock.lock();
        try {
            buffer[head] = execution;
            head = (head + 1) % maxSize;
            if (count < maxSize) {
                count++;
            }
        } finally {
            lock.unlock();
        }
    }

    public List<Execution> query(ExecutionHistoryQuery query) {
        lock.lock();
        try {
            List<Execution> results = new ArrayList<>();

            int effectiveLimit = Math.min(query.limit(), count);

            for (int i = 0; i < count && results.size() < effectiveLimit; i++) {
                int idx = (head - 1 - i + maxSize) % maxSize;
                Execution exec = buffer[idx];
                if (exec == null) {
                    continue;
                }

                if (query.symbol() != null && !query.symbol().equals(exec.symbol())) {
                    continue;
                }
                if (query.fromTimestamp() != null && exec.timestamp() < query.fromTimestamp()) {
                    continue;
                }
                if (query.toTimestamp() != null && exec.timestamp() > query.toTimestamp()) {
                    continue;
                }

                results.add(exec);
            }

            return results;
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return count;
        } finally {
            lock.unlock();
        }
    }

    public void clear() {
        lock.lock();
        try {
            for (int i = 0; i < maxSize; i++) {
                buffer[i] = null;
            }
            head = 0;
            count = 0;
            LOGGER.info("Execution history cleared");
        } finally {
            lock.unlock();
        }
    }
}
