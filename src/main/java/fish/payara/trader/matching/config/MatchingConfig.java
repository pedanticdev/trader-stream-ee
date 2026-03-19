package fish.payara.trader.matching.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class MatchingConfig {

    @Inject
    @ConfigProperty(name = "matching.max.history.size", defaultValue = "10000")
    private int maxHistorySize;

    @Inject
    @ConfigProperty(name = "matching.max.book.depth", defaultValue = "50")
    private int maxBookDepth;

    @Inject
    @ConfigProperty(name = "matching.default.tif", defaultValue = "GTC")
    private String defaultTimeInForce;

    @Inject
    @ConfigProperty(name = "matching.price.scale", defaultValue = "10000")
    private int priceScale;

    public int maxHistorySize() {
        return maxHistorySize;
    }

    public int maxBookDepth() {
        return maxBookDepth;
    }

    public String defaultTimeInForce() {
        return defaultTimeInForce;
    }

    public int priceScale() {
        return priceScale;
    }
}
