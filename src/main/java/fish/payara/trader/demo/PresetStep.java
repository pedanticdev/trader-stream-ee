package fish.payara.trader.demo;

import java.io.Serializable;

/**
 * Single step in a demo preset. Defines a memory pressure mode to apply and its duration.
 */
public record PresetStep(String mode, int durationSeconds, String description) implements Serializable {
    /**
     * Creates a preset step.
     */
    public static PresetStep of(String mode, int durationSeconds, String description) {
        return new PresetStep(mode, durationSeconds, description);
    }

    private static final long serialVersionUID = 1L;
}
