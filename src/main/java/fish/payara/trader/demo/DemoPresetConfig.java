package fish.payara.trader.demo;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Loads and provides access to demo preset configurations from demo-presets.yml.
 */
@ApplicationScoped
public class DemoPresetConfig {

    private static final Logger LOGGER = Logger.getLogger(DemoPresetConfig.class.getName());
    private static final String CONFIG_PATH = "/demo-presets.yml";

    private List<DemoPresetDefinition> presets;

    @PostConstruct
    public void init() {
        this.presets = loadPresets();
        LOGGER.info("Loaded " + presets.size() + " demo presets from configuration");
    }

    /**
     * Loads demo presets from YAML configuration file.
     */
    private List<DemoPresetDefinition> loadPresets() {
        try (InputStream input = getClass().getResourceAsStream(CONFIG_PATH)) {
            if (input == null) {
                LOGGER.warning("Demo presets configuration file not found: " + CONFIG_PATH);
                return List.of();
            }

            LoadSettings settings = LoadSettings.builder().build();
            Load yaml = new Load(settings);

            @SuppressWarnings("unchecked")
            Map<String, Object> root = (Map<String, Object>) yaml.loadFromInputStream(input);
            List<Map<String, Object>> presetList = (List<Map<String, Object>>) root.get("presets");

            List<DemoPresetDefinition> result = new ArrayList<>();
            for (Map<String, Object> presetData : presetList) {
                result.add(parsePreset(presetData));
            }

            return result;

        } catch (YamlEngineException e) {
            LOGGER.log(Level.SEVERE, "Failed to parse demo presets YAML configuration", e);
            return List.of();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load demo presets configuration", e);
            return List.of();
        }
    }

    /**
     * Parses a single preset from YAML map.
     */
    @SuppressWarnings("unchecked")
    private DemoPresetDefinition parsePreset(Map<String, Object> data) {
        String id = (String) data.get("id");
        String name = (String) data.get("name");
        String description = (String) data.get("description");
        int durationSeconds = ((Number) data.get("durationSeconds")).intValue();
        String expectedImpact = (String) data.get("expectedImpact");

        List<Map<String, Object>> stepsData = (List<Map<String, Object>>) data.get("steps");
        List<PresetStep> steps = stepsData.stream().map(this::parseStep).collect(Collectors.toList());

        return new DemoPresetDefinition(id, name, description, durationSeconds, expectedImpact, steps);
    }

    /**
     * Parses a single step from YAML map.
     */
    private PresetStep parseStep(Map<String, Object> data) {
        String mode = (String) data.get("mode");
        int durationSeconds = ((Number) data.get("durationSeconds")).intValue();
        String description = (String) data.get("description");
        return PresetStep.of(mode, durationSeconds, description);
    }

    /**
     * Returns all available demo presets.
     */
    public List<DemoPresetDefinition> getPresets() {
        return List.copyOf(presets);
    }

    /**
     * Finds a preset by ID.
     */
    public DemoPresetDefinition getPresetById(String id) {
        return presets.stream().filter(p -> p.id().equals(id)).findFirst().orElse(null);
    }

    /**
     * Checks if a preset ID exists.
     */
    public boolean hasPreset(String id) {
        return presets.stream().anyMatch(p -> p.id().equals(id));
    }
}
