package fish.payara.trader.dto;

import fish.payara.trader.pressure.AllocationMode;

/**
 * Response DTO for memory pressure status. Provides type-safe API contract replacing raw Map usage.
 */
public record PressureStatusResponse(String currentMode, String description, boolean running, int allocationRateMBPerSec, int liveSetSizeMB,
                String scenarioType, String workloadType) {
    /**
     * Creates response from current AllocationMode state.
     */
    public static PressureStatusResponse from(AllocationMode mode, boolean running) {
        return new PressureStatusResponse(mode.name(), mode.getDescription(), running, mode.getAllocationRateMBPerSec(), mode.getLiveSetSizeMB(),
                        mode.getScenarioType().name(), mode.getWorkloadType().name());
    }
}
