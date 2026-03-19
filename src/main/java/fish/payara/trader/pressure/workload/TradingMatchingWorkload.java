package fish.payara.trader.pressure.workload;

import fish.payara.trader.matching.engine.MatchingEngine;
import fish.payara.trader.matching.model.OrderRequest;
import fish.payara.trader.matching.model.OrderType;
import fish.payara.trader.matching.model.Side;
import fish.payara.trader.matching.model.TimeInForce;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Workload that exercises the matching engine to generate GC pressure from Order, Execution, and OrderBookEntry object allocations.
 */
@ApplicationScoped
public class TradingMatchingWorkload extends AbstractCpuWorkload {

    private static final String[] SYMBOLS = {"AAPL", "GOOGL", "MSFT", "AMZN", "TSLA", "NVDA", "META", "NFLX"};

    private static final OrderType[] ORDER_TYPES = {OrderType.MARKET, OrderType.LIMIT, OrderType.LIMIT, OrderType.LIMIT, OrderType.STOP_LIMIT};

    @Inject
    private MatchingEngine matchingEngine;

    @Override
    public String name() {
        return "TRADING_MATCHING";
    }

    @Override
    public void execute(int iterations) {
        int numThreads = config.threadsPerWorkload();
        executeMultiThreaded(iterations, numThreads, this::executeSingleIteration);
    }

    private void executeSingleIteration(ThreadLocalRandom rng) {
        // Generate random order parameters
        String symbol = SYMBOLS[rng.nextInt(SYMBOLS.length)];
        Side side = rng.nextBoolean() ? Side.BUY : Side.SELL;
        OrderType type = ORDER_TYPES[rng.nextInt(ORDER_TYPES.length)];
        Long quantity = 100L + rng.nextInt(9901); // 100-10000
        double basePrice = 100 + rng.nextDouble() * 900; // 100-1000
        Double price = Math.round(basePrice * 100) / 100.0;
        TimeInForce tif = TimeInForce.values()[rng.nextInt(TimeInForce.values().length)];

        OrderRequest request;
        if (type == OrderType.STOP_LIMIT) {
            Double stopPrice = price * (1 + (rng.nextDouble() * 0.1 - 0.05)); // +/- 5%
            request = new OrderRequest(symbol, side, type, price, quantity, stopPrice, tif, null, 0.0);
        } else if (type == OrderType.LIMIT) {
            request = new OrderRequest(symbol, side, type, price, quantity, null, tif, null, 0.0);
        } else {
            request = new OrderRequest(symbol, side, type, null, quantity, null, tif, null, 0.0);
        }

        try {
            matchingEngine.submitOrder(request);

            // Estimate bytes allocated per order:
            // - Order record: ~200 bytes (fields + object header)
            // - OrderRequest: ~100 bytes
            // - OrderBookEntry (if resting): ~50 bytes
            // - Price objects: ~32 bytes each
            // - Execution objects (if matched): ~150 bytes each
            // Average ~400 bytes per order submission
            bytesAllocated.addAndGet(400);
            operationsCompleted.incrementAndGet();

            // Periodically cancel some orders to exercise that path
            if (rng.nextInt(100) < 10) {
                long randomOrderId = System.currentTimeMillis() * 10000 + rng.nextInt(10000);
                matchingEngine.cancelOrder(randomOrderId);
                bytesAllocated.addAndGet(100);
            }

        } catch (Exception e) {
            // Order validation errors are expected - still counts as work
            operationsCompleted.incrementAndGet();
        }
    }
}
