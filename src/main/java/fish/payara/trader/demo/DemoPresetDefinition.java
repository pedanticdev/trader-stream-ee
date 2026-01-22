package fish.payara.trader.demo;

import java.io.Serializable;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Complete definition of a demo preset. Contains metadata and execution steps for client-side orchestration.
 */
public record DemoPresetDefinition(String id, String name, String description, int durationSeconds, String expectedImpact,
                List<PresetStep> steps) implements Serializable {

    private static final long serialVersionUID = 1L;
    /**
     * Calculates total duration across all steps.
     */
    public int totalDuration() {
        return steps.stream().mapToInt(PresetStep::durationSeconds).sum();
    }

    /**
     * Creates a new definition with indexed step descriptions.
     */
    public DemoPresetDefinition withIndexedSteps() {
        List<PresetStep> indexed = IntStream.range(0, steps.size()).mapToObj(i -> {
            PresetStep original = steps.get(i);
            return new PresetStep(original.mode(), original.durationSeconds(), (i + 1) + ". " + original.description());
        }).toList();
        return new DemoPresetDefinition(id, name, description, durationSeconds, expectedImpact, indexed);
    }
}
