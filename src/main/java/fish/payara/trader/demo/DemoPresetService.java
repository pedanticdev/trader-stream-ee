package fish.payara.trader.demo;

import com.hazelcast.core.HazelcastInstance;
import fish.payara.trader.dto.DemoPresetResponse;
import fish.payara.trader.dto.DemoPresetResponse.PresetStepResponse;
import fish.payara.trader.pressure.AllocationMode;
import fish.payara.trader.pressure.MemoryPressureService;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.IntStream;

/**
 * Service for managing demo preset execution. Preset steps are executed client-side via REST calls (hybrid approach).
 */
@ApplicationScoped
public class DemoPresetService {

    private static final Logger LOGGER = Logger.getLogger(DemoPresetService.class.getName());
    private static final String EXECUTIONS_MAP_NAME = "demo-preset-executions";

    @Inject
    private DemoPresetConfig config;

    @Inject
    private MemoryPressureService pressureService;

    @Inject
    private HazelcastInstance hazelcastInstance;

    private Map<String, PresetExecutionContext> getActiveExecutions() {
        return hazelcastInstance.getMap(EXECUTIONS_MAP_NAME);
    }

    /**
     * Returns all available demo presets.
     */
    public List<DemoPresetResponse> getAllPresets() {
        return config.getPresets().stream().map(this::toResponse).toList();
    }

    /**
     * Returns a specific preset by ID.
     */
    public DemoPresetResponse getPreset(String id) {
        DemoPresetDefinition definition = config.getPresetById(id);
        return definition != null ? toResponse(definition) : null;
    }

    /**
     * Initializes a preset execution session. Returns execution context with step details for client-side orchestration.
     */
    public PresetExecutionContext initializeExecution(String presetId) {
        DemoPresetDefinition preset = config.getPresetById(presetId);
        if (preset == null) {
            return null;
        }

        String executionId = java.util.UUID.randomUUID().toString();
        PresetExecutionContext context = new PresetExecutionContext(executionId, presetId, preset, System.currentTimeMillis());

        getActiveExecutions().put(executionId, context);
        LOGGER.info("Initialized preset execution: " + presetId + " (executionId: " + executionId + ")");

        return context;
    }

    /**
     * Executes a single preset step. Called by client for each step in the sequence.
     */
    public boolean executeStep(String executionId, int stepIndex) {
        Map<String, PresetExecutionContext> activeExecutions = getActiveExecutions();
        PresetExecutionContext context = activeExecutions.get(executionId);
        if (context == null) {
            LOGGER.warning("Execution context not found: " + executionId);
            return false;
        }

        if (stepIndex < 0 || stepIndex >= context.preset().steps().size()) {
            LOGGER.warning("Invalid step index: " + stepIndex);
            return false;
        }

        PresetStep step = context.preset().steps().get(stepIndex);
        try {
            AllocationMode mode = AllocationMode.valueOf(step.mode());
            pressureService.setAllocationMode(mode);
            context.markStepCompleted(stepIndex);
            // Put updated context back into distributed map
            activeExecutions.put(executionId, context);
            LOGGER.info("Executed step " + stepIndex + " of preset " + context.presetId() + ": " + step.mode());
            return true;
        } catch (IllegalArgumentException e) {
            LOGGER.warning("Invalid allocation mode: " + step.mode());
            return false;
        }
    }

    /**
     * Cancels an active preset execution.
     */
    public void cancelExecution(String executionId) {
        Map<String, PresetExecutionContext> activeExecutions = getActiveExecutions();
        PresetExecutionContext context = activeExecutions.get(executionId);
        if (context == null) {
            LOGGER.warning("Execution context not found for cancellation: " + executionId);
            return;
        }

        context.cancel();
        pressureService.setAllocationMode(AllocationMode.OFF);
        activeExecutions.remove(executionId);
        LOGGER.info("Cancelled preset execution: " + executionId);
    }

    /**
     * Returns active execution context.
     */
    public PresetExecutionContext getExecution(String executionId) {
        return getActiveExecutions().get(executionId);
    }

    @PreDestroy
    public void cleanup() {
        getActiveExecutions().clear();
    }

    private DemoPresetResponse toResponse(DemoPresetDefinition definition) {
        List<PresetStepResponse> steps = IntStream.range(0, definition.steps().size()).mapToObj(i -> {
            PresetStep step = definition.steps().get(i);
            return PresetStepResponse.of(i, step.mode(), step.durationSeconds(), step.description());
        }).toList();

        return new DemoPresetResponse(definition.id(), definition.name(), definition.description(), definition.durationSeconds(), definition.expectedImpact(),
                        steps);
    }

    /**
     * Execution context for a running demo preset. Must be Serializable for Hazelcast distributed map storage.
     */
    public static class PresetExecutionContext implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String executionId;
        private final String presetId;
        private final DemoPresetDefinition preset;
        private final long startTime;
        private boolean cancelled = false;
        private final Map<Integer, Long> completedSteps = new HashMap<>();

        public PresetExecutionContext(String executionId, String presetId, DemoPresetDefinition preset, long startTime) {
            this.executionId = executionId;
            this.presetId = presetId;
            this.preset = preset;
            this.startTime = startTime;
        }

        public synchronized void markStepCompleted(int stepIndex) {
            completedSteps.put(stepIndex, System.currentTimeMillis());
        }

        public synchronized void cancel() {
            this.cancelled = true;
        }

        public String executionId() {
            return executionId;
        }

        public String presetId() {
            return presetId;
        }

        public DemoPresetDefinition preset() {
            return preset;
        }

        public long startTime() {
            return startTime;
        }

        public synchronized boolean isCancelled() {
            return cancelled;
        }

        public Map<Integer, Long> completedSteps() {
            return Map.copyOf(completedSteps);
        }

        public int currentStepIndex() {
            return completedSteps.size();
        }

        public boolean isComplete() {
            return completedSteps.size() >= preset.steps().size();
        }
    }
}
