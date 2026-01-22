package fish.payara.trader.dto;

import java.util.List;

/**
 * Response DTO for demo preset information. Contains preset metadata and execution steps for client-side orchestration.
 */
public record DemoPresetResponse(String id, String name, String description, int durationSeconds, String expectedImpact, List<PresetStepResponse> steps) {
    /**
     * Individual step in a demo preset.
     */
    public record PresetStepResponse(int stepIndex, String mode, int durationSeconds, String description) {
        public static PresetStepResponse of(int index, String mode, int duration, String description) {
            return new PresetStepResponse(index, mode, duration, description);
        }
    }
}
