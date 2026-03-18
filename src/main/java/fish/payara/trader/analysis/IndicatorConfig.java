package fish.payara.trader.analysis;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * MicroProfile configuration for technical analysis indicator parameters. All values configurable via environment variables or microprofile-config.properties.
 */
@ApplicationScoped
public class IndicatorConfig {

    @Inject
    @ConfigProperty(name = "analysis.sma.period", defaultValue = "20")
    private int smaPeriod;

    @Inject
    @ConfigProperty(name = "analysis.ema.period", defaultValue = "12")
    private int emaPeriod;

    @Inject
    @ConfigProperty(name = "analysis.rsi.period", defaultValue = "14")
    private int rsiPeriod;

    @Inject
    @ConfigProperty(name = "analysis.macd.fast", defaultValue = "12")
    private int macdFast;

    @Inject
    @ConfigProperty(name = "analysis.macd.slow", defaultValue = "26")
    private int macdSlow;

    @Inject
    @ConfigProperty(name = "analysis.macd.signal", defaultValue = "9")
    private int macdSignal;

    @Inject
    @ConfigProperty(name = "analysis.bb.period", defaultValue = "20")
    private int bbPeriod;

    @Inject
    @ConfigProperty(name = "analysis.bb.stddev", defaultValue = "2.0")
    private double bbStdDevMultiplier;

    @Inject
    @ConfigProperty(name = "analysis.atr.period", defaultValue = "14")
    private int atrPeriod;

    @Inject
    @ConfigProperty(name = "analysis.bar.duration.seconds", defaultValue = "60")
    private int barDurationSeconds;

    @Inject
    @ConfigProperty(name = "analysis.max.bars", defaultValue = "500")
    private int maxBars;

    @Inject
    @ConfigProperty(name = "analysis.broadcast.interval.ms", defaultValue = "1000")
    private long broadcastIntervalMs;

    public int smaPeriod() {
        return smaPeriod;
    }

    public int emaPeriod() {
        return emaPeriod;
    }

    public int rsiPeriod() {
        return rsiPeriod;
    }

    public int macdFast() {
        return macdFast;
    }

    public int macdSlow() {
        return macdSlow;
    }

    public int macdSignal() {
        return macdSignal;
    }

    public int bbPeriod() {
        return bbPeriod;
    }

    public double bbStdDevMultiplier() {
        return bbStdDevMultiplier;
    }

    public int atrPeriod() {
        return atrPeriod;
    }

    public int barDurationSeconds() {
        return barDurationSeconds;
    }

    public int maxBars() {
        return maxBars;
    }

    public long broadcastIntervalMs() {
        return broadcastIntervalMs;
    }
}
