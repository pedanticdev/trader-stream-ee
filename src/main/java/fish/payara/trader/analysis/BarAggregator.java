package fish.payara.trader.analysis;

import fish.payara.trader.concurrency.VirtualThreadExecutor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.bars.TimeBarBuilder;

/**
 * Aggregates raw ticks (trades and quotes) into OHLCV bars for ta4j. One series per symbol, capped at maxBars. A background task closes completed bars and
 * opens new ones on the configured interval.
 */
@ApplicationScoped
public class BarAggregator {

    private static final Logger LOGGER = Logger.getLogger(BarAggregator.class.getName());

    private final ConcurrentHashMap<String, BarSeries> series = new ConcurrentHashMap<>();

    private record MutableBar(double open, double high, double low, double close, double volume, Instant endTime) {
    }

    private final ConcurrentHashMap<String, MutableBar> mutableBars = new ConcurrentHashMap<>();

    @Inject
    private IndicatorConfig config;

    @Inject
    @VirtualThreadExecutor
    private ManagedExecutorService executorService;

    private ScheduledExecutorService barScheduler;
    private Future<?> barTask;

    @PostConstruct
    public void init() {
        barScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "bar-aggregator");
            t.setDaemon(true);
            return t;
        });

        long intervalMs = config.barDurationSeconds() * 1000L;
        barTask = barScheduler.scheduleAtFixedRate(this::closeAllBars, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        LOGGER.info("BarAggregator initialized with %d-second bars, max %d bars".formatted(config.barDurationSeconds(), config.maxBars()));
    }

    @PreDestroy
    public void shutdown() {
        if (barTask != null) {
            barTask.cancel(false);
        }
        if (barScheduler != null) {
            barScheduler.shutdown();
        }
    }

    /**
     * Feed a trade into the current bar for the given symbol.
     */
    public void onTrade(String symbol, double price, long quantity, long timestamp) {
        executorService.submit(() -> updateBar(symbol, price, quantity, timestamp));
    }

    /**
     * Feed a quote (bid/ask) into the current bar using the mid-price.
     */
    public void onQuote(String symbol, double bidPrice, double askPrice, long timestamp) {
        executorService.submit(() -> {
            double midPrice = (bidPrice + askPrice) / 2.0;
            updateBar(symbol, midPrice, 0, timestamp);
        });
    }

    private void updateBar(String symbol, double price, long quantity, long timestamp) {
        Instant endTime = Instant.ofEpochMilli(timestamp);

        MutableBar current = mutableBars.compute(symbol, (key, existing) -> {
            if (existing == null) {
                getOrCreateSeries(symbol);
                Instant barEnd = alignToEndOfBar(endTime, config.barDurationSeconds());
                return new MutableBar(price, price, price, price, quantity, barEnd);
            }

            Instant barEnd = existing.endTime();
            Instant barStart = barEnd.minus(Duration.ofSeconds(config.barDurationSeconds()));

            if (!timestampIsInBar(endTime, barStart, barEnd)) {
                closeBar(symbol);
                Instant newBarEnd = alignToEndOfBar(endTime, config.barDurationSeconds());
                return new MutableBar(price, price, price, price, quantity, newBarEnd);
            }

            double newHigh = Math.max(existing.high(), price);
            double newLow = Math.min(existing.low(), price);
            double newVolume = existing.volume() + quantity;
            return new MutableBar(existing.open(), newHigh, newLow, price, newVolume, barEnd);
        });
    }

    private Instant alignToEndOfBar(Instant timestamp, int barDurationSeconds) {
        long epochSeconds = timestamp.getEpochSecond();
        long alignedEnd = ((epochSeconds / barDurationSeconds) + 1) * barDurationSeconds;
        return Instant.ofEpochSecond(alignedEnd);
    }

    private boolean timestampIsInBar(Instant timestamp, Instant barStart, Instant barEnd) {
        return !timestamp.isBefore(barStart) && timestamp.isBefore(barEnd);
    }

    private BarSeries getOrCreateSeries(String symbol) {
        return series.computeIfAbsent(symbol, k -> {
            var srs = new BaseBarSeriesBuilder().withName(k).build();
            srs.setMaximumBarCount(config.maxBars());
            return srs;
        });
    }

    private void closeBar(String symbol) {
        MutableBar mutable = mutableBars.get(symbol);
        if (mutable == null) {
            return;
        }

        BarSeries srs = getOrCreateSeries(symbol);
        try {
            var builder = new TimeBarBuilder().timePeriod(Duration.ofSeconds(config.barDurationSeconds()))
                            .endTime(mutable.endTime())
                            .openPrice(mutable.open())
                            .highPrice(mutable.high())
                            .lowPrice(mutable.low())
                            .closePrice(mutable.close())
                            .volume(mutable.volume());

            srs.addBar(builder.build());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to close bar for " + symbol, e);
        }
    }

    /**
     * Called by the scheduled task to close all open bars and start fresh ones.
     */
    private void closeAllBars() {
        for (String symbol : mutableBars.keySet()) {
            try {
                closeBar(symbol);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error closing bar for " + symbol, e);
            }
        }
        mutableBars.clear();
    }

    /**
     * Returns the BarSeries for the given symbol, or an empty series if none exists.
     */
    public BarSeries getSeries(String symbol) {
        return series.getOrDefault(symbol, new BaseBarSeriesBuilder().withName(symbol).build());
    }

    /**
     * Returns the current (in-progress) bar if one exists.
     */
    public Optional<Bar> getCurrentBar(String symbol) {
        MutableBar mutable = mutableBars.get(symbol);
        if (mutable == null) {
            return Optional.empty();
        }
        try {
            var builder = new TimeBarBuilder().timePeriod(Duration.ofSeconds(config.barDurationSeconds()))
                            .endTime(mutable.endTime())
                            .openPrice(mutable.open())
                            .highPrice(mutable.high())
                            .lowPrice(mutable.low())
                            .closePrice(mutable.close())
                            .volume(mutable.volume());
            return Optional.of(builder.build());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Returns the number of completed bars in the series.
     */
    public int getBarCount(String symbol) {
        BarSeries srs = series.get(symbol);
        return srs != null ? srs.getBarCount() : 0;
    }
}
