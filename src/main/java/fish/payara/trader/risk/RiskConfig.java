package fish.payara.trader.risk;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * MicroProfile configuration for risk management parameters.
 */
@ApplicationScoped
public class RiskConfig {

    @Inject
    @ConfigProperty(name = "risk.position.limit.default", defaultValue = "10000")
    private double defaultPositionLimit;

    @Inject
    @ConfigProperty(name = "risk.var.lookback.bars", defaultValue = "252")
    private int varLookbackBars;

    @Inject
    @ConfigProperty(name = "risk.var.confidence", defaultValue = "0.95")
    private double varConfidence;

    @Inject
    @ConfigProperty(name = "risk.stress.crash.percent", defaultValue = "20")
    private double stressCrashPercent;

    @Inject
    @ConfigProperty(name = "risk.stress.vol.spike", defaultValue = "3.0")
    private double stressVolSpike;

    @Inject
    @ConfigProperty(name = "risk.stress.liquidity.widen", defaultValue = "50")
    private double stressLiquidityWiden;

    public double defaultPositionLimit() {
        return defaultPositionLimit;
    }

    public int varLookbackBars() {
        return varLookbackBars;
    }

    public double varConfidence() {
        return varConfidence;
    }

    public double stressCrashPercent() {
        return stressCrashPercent;
    }

    public double stressVolSpike() {
        return stressVolSpike;
    }

    public double stressLiquidityWiden() {
        return stressLiquidityWiden;
    }
}
