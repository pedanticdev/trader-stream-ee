package fish.payara.trader.analysis;

import fish.payara.trader.analysis.model.IndicatorSnapshot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.ta4j.core.BarSeries;
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

/**
 * Computes technical analysis indicators over aggregated bar data. All indicator families evaluated: SMA, EMA, RSI, MACD, Bollinger Bands (lower/middle/upper),
 * and ATR.
 */
@ApplicationScoped
public class IndicatorService {

    private static final Logger LOGGER = Logger.getLogger(IndicatorService.class.getName());

    @Inject
    private BarAggregator barAggregator;

    @Inject
    private IndicatorConfig config;

    /**
     * Returns a snapshot of all indicators for the given symbol. Returns null if the series has insufficient bars.
     */
    public IndicatorSnapshot getSnapshot(String symbol) {
        Map<String, Double> values = calculateIndicators(symbol);
        if (values.isEmpty()) {
            return null;
        }

        BarSeries series = barAggregator.getSeries(symbol);
        double lastPrice = 0.0;
        if (!series.isEmpty()) {
            lastPrice = series.getLastBar().getClosePrice().doubleValue();
        }

        return new IndicatorSnapshot(symbol, System.currentTimeMillis(), lastPrice, values);
    }

    /**
     * Walks the series backward computing indicators at each bar.
     */
    public List<IndicatorSnapshot> getHistoricalSnapshots(String symbol, int barCount) {
        BarSeries series = barAggregator.getSeries(symbol);
        if (series.isEmpty()) {
            return List.of();
        }

        int requiredBars = requiredBarCount();
        if (series.getEndIndex() < requiredBars) {
            return List.of();
        }

        List<IndicatorSnapshot> snapshots = new ArrayList<>();
        int endIndex = series.getEndIndex();
        int startIndex = Math.max(requiredBars, endIndex - barCount + 1);

        for (int i = startIndex; i <= endIndex; i++) {
            BarSeries subSeries = series.getSubSeries(0, i);
            Map<String, Double> values = calculateIndicatorsForSeries(subSeries);
            if (values.isEmpty()) {
                continue;
            }

            double lastPrice = subSeries.getLastBar().getClosePrice().doubleValue();
            long timestamp = subSeries.getLastBar().getEndTime().toEpochMilli();

            snapshots.add(new IndicatorSnapshot(symbol, timestamp, lastPrice, values));
        }

        return snapshots;
    }

    /**
     * Calculates all indicator families for the given symbol.
     */
    public Map<String, Double> calculateIndicators(String symbol) {
        BarSeries series = barAggregator.getSeries(symbol);
        if (series.isEmpty() || series.getEndIndex() < requiredBarCount()) {
            return Map.of();
        }
        return calculateIndicatorsForSeries(series);
    }

    private Map<String, Double> calculateIndicatorsForSeries(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        Map<String, Double> values = new LinkedHashMap<>();
        int last = series.getEndIndex();

        int maxRequired = requiredBarCount();
        if (last < maxRequired) {
            return Map.of();
        }

        try {
            SMAIndicator sma = new SMAIndicator(closePrice, config.smaPeriod());
            values.put("SMA(%d)".formatted(config.smaPeriod()), sma.getValue(last).doubleValue());

            EMAIndicator ema = new EMAIndicator(closePrice, config.emaPeriod());
            values.put("EMA(%d)".formatted(config.emaPeriod()), ema.getValue(last).doubleValue());

            RSIIndicator rsi = new RSIIndicator(closePrice, config.rsiPeriod());
            values.put("RSI(%d)".formatted(config.rsiPeriod()), rsi.getValue(last).doubleValue());

            MACDIndicator macd = new MACDIndicator(closePrice, config.macdFast(), config.macdSlow());
            values.put("MACD(%d,%d)".formatted(config.macdFast(), config.macdSlow()), macd.getValue(last).doubleValue());

            BollingerBandsMiddleIndicator bbMiddle = new BollingerBandsMiddleIndicator(sma);
            StandardDeviationIndicator sd = new StandardDeviationIndicator(closePrice, config.bbPeriod());
            Num k = series.numFactory().numOf(config.bbStdDevMultiplier());
            BollingerBandsLowerIndicator bbLower = new BollingerBandsLowerIndicator(bbMiddle, sd, k);
            BollingerBandsUpperIndicator bbUpper = new BollingerBandsUpperIndicator(bbMiddle, sd, k);

            values.put("BB_Lower(%d)".formatted(config.bbPeriod()), bbLower.getValue(last).doubleValue());
            values.put("BB_Middle(%d)".formatted(config.bbPeriod()), bbMiddle.getValue(last).doubleValue());
            values.put("BB_Upper(%d)".formatted(config.bbPeriod()), bbUpper.getValue(last).doubleValue());

            ATRIndicator atr = new ATRIndicator(series, config.atrPeriod());
            values.put("ATR(%d)".formatted(config.atrPeriod()), atr.getValue(last).doubleValue());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to compute indicators: " + e.getMessage(), e);
            return Map.of();
        }

        return values;
    }

    private int requiredBarCount() {
        return Math.max(config.macdSlow(), Math.max(config.bbPeriod(), config.atrPeriod())) + 1;
    }
}
