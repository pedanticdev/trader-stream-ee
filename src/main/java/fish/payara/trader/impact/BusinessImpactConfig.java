package fish.payara.trader.impact;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Configuration for business impact calculations. Values are configurable via environment variables or microprofile-config.properties.
 */
@ApplicationScoped
public class BusinessImpactConfig {

    @Inject
    @ConfigProperty(name = "business.impact.per.trade", defaultValue = "25000")
    private int tradeValue;

    @Inject
    @ConfigProperty(name = "business.impact.currency", defaultValue = "USD")
    private String currency;

    @Inject
    @ConfigProperty(name = "business.impact.window.seconds", defaultValue = "60")
    private long windowSeconds;

    @Inject
    @ConfigProperty(name = "business.impact.instances.c4", defaultValue = "3")
    private int instancesNeededC4;

    @Inject
    @ConfigProperty(name = "business.impact.instances.g1", defaultValue = "5")
    private int instancesNeededG1;

    public int tradeValue() {
        return tradeValue;
    }

    public String currency() {
        return currency;
    }

    public long windowSeconds() {
        return windowSeconds;
    }

    public int instancesNeededC4() {
        return instancesNeededC4;
    }

    public int instancesNeededG1() {
        return instancesNeededG1;
    }
}
