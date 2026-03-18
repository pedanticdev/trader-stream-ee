package fish.payara.trader.pressure.workload;

import fish.payara.trader.pressure.WorkloadConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.bars.TimeBarBuilder;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.Num;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Workload that exercises ta4j technical analysis library to generate GC pressure from indicator object allocations. Creates synthetic bar series and computes
 * multiple indicator families repeatedly.
 */
@ApplicationScoped
public class TechnicalAnalysisWorkload extends AbstractCpuWorkload {

    private static final int[] SMA_PERIODS = {10, 20, 50, 100, 200};
    private static final int[] EMA_PERIODS = {12, 20, 26, 50};
    private static final int[] RSI_PERIODS = {7, 14, 21};
    private static final int BB_PERIOD = 20;
    private static final int ATR_PERIOD = 14;
    private static final int MACD_FAST = 12;
    private static final int MACD_SLOW = 26;

    @Inject
    private WorkloadConfig config;

    @Override
    public String name() {
        return "TECHNICAL_ANALYSIS";
    }

    @Override
    public void execute(int iterations) {
        int numThreads = config.threadsPerWorkload();
        executeMultiThreaded(iterations, numThreads, this::executeSingleIteration);
    }

    private void executeSingleIteration(ThreadLocalRandom rng) {
        BarSeries series = createSyntheticSeries(rng);
        computeAllIndicators(series);

        // Estimate bytes allocated:
        // - BarSeries with 500 bars: ~40KB (each bar ~80 bytes)
        // - Each indicator: ~200-500 bytes object allocation
        // - Computing 15+ indicators: ~5KB
        // - Num objects (DecimalNum): cached but creates wrapper objects
        // Total per iteration: ~50KB
        bytesAllocated.addAndGet(50_000);
        operationsCompleted.incrementAndGet();
    }

    private BarSeries createSyntheticSeries(ThreadLocalRandom rng) {
        int barCount = 200 + rng.nextInt(300); // 200-500 bars
        BarSeries series = new BaseBarSeriesBuilder().withName("SYNTH_" + rng.nextInt(1000)).build();

        Instant now = Instant.now();
        double price = 100 + rng.nextDouble() * 900; // Starting price 100-1000

        for (int i = 0; i < barCount; i++) {
            double volatility = 0.02 + rng.nextDouble() * 0.03; // 2-5% volatility
            double change = (rng.nextDouble() * 2 - 1) * volatility;
            double open = price;
            double close = price * (1 + change);
            double high = Math.max(open, close) * (1 + rng.nextDouble() * 0.01);
            double low = Math.min(open, close) * (1 - rng.nextDouble() * 0.01);
            double volume = 100_000 + rng.nextDouble() * 900_000;

            var bar = new TimeBarBuilder().timePeriod(Duration.ofMinutes(1))
                            .endTime(now.minusSeconds((long) (barCount - i) * 60))
                            .openPrice(open)
                            .highPrice(high)
                            .lowPrice(low)
                            .closePrice(close)
                            .volume(volume)
                            .build();

            series.addBar(bar);
            price = close;
        }

        return series;
    }

    private void computeAllIndicators(BarSeries series) {
        if (series.getEndIndex() < 200) {
            return;
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        int lastIndex = series.getEndIndex();

        // SMA indicators
        for (int period : SMA_PERIODS) {
            if (series.getEndIndex() >= period) {
                SMAIndicator sma = new SMAIndicator(closePrice, period);
                sma.getValue(lastIndex);
            }
        }

        // EMA indicators
        for (int period : EMA_PERIODS) {
            if (series.getEndIndex() >= period) {
                EMAIndicator ema = new EMAIndicator(closePrice, period);
                ema.getValue(lastIndex);
            }
        }

        // RSI indicators
        for (int period : RSI_PERIODS) {
            if (series.getEndIndex() >= period) {
                RSIIndicator rsi = new RSIIndicator(closePrice, period);
                rsi.getValue(lastIndex);
            }
        }

        // MACD
        if (series.getEndIndex() >= MACD_SLOW) {
            MACDIndicator macd = new MACDIndicator(closePrice, MACD_FAST, MACD_SLOW);
            macd.getValue(lastIndex);
        }

        // Bollinger Bands
        if (series.getEndIndex() >= BB_PERIOD) {
            SMAIndicator sma = new SMAIndicator(closePrice, BB_PERIOD);
            StandardDeviationIndicator sd = new StandardDeviationIndicator(closePrice, BB_PERIOD);
            BollingerBandsMiddleIndicator bbMiddle = new BollingerBandsMiddleIndicator(sma);
            Num k = series.numFactory().numOf(2.0);
            BollingerBandsLowerIndicator bbLower = new BollingerBandsLowerIndicator(bbMiddle, sd, k);
            BollingerBandsUpperIndicator bbUpper = new BollingerBandsUpperIndicator(bbMiddle, sd, k);

            bbMiddle.getValue(lastIndex);
            bbLower.getValue(lastIndex);
            bbUpper.getValue(lastIndex);
        }

        // ATR
        if (series.getEndIndex() >= ATR_PERIOD) {
            ATRIndicator atr = new ATRIndicator(series, ATR_PERIOD);
            atr.getValue(lastIndex);
        }
    }
}
