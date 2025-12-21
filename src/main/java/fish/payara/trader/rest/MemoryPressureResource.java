package fish.payara.trader.rest;

import fish.payara.trader.pressure.AllocationMode;
import fish.payara.trader.pressure.MemoryPressureService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/** REST endpoint for controlling memory pressure testing */
@Path("/pressure")
public class MemoryPressureResource {

  private static final Logger LOGGER = Logger.getLogger(MemoryPressureResource.class.getName());

  @Inject private MemoryPressureService pressureService;

  public enum StressScenario {
    DEMO_BASELINE(AllocationMode.OFF, "Baseline - No artificial stress"),
    DEMO_NORMAL(AllocationMode.LOW, "Normal Trading - Light load to show steady-state"),
    DEMO_STRESS(AllocationMode.HIGH, "High Stress - Heavy allocation + burst patterns"),
    DEMO_EXTREME(AllocationMode.EXTREME, "Extreme Stress - Maximum pressure + tenured pollution");

    private final AllocationMode mode;
    private final String description;

    StressScenario(AllocationMode mode, String description) {
      this.mode = mode;
      this.description = description;
    }

    public AllocationMode getMode() {
      return mode;
    }

    public String getDescription() {
      return description;
    }
  }

  @GET
  @Path("/status")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getStatus() {
    AllocationMode currentMode = pressureService.getCurrentMode();
    LOGGER.fine(String.format("GET /api/pressure/status - Current mode: %s", currentMode.name()));

    Map<String, Object> status = new HashMap<>();
    status.put("currentMode", currentMode.name());
    status.put("description", currentMode.getDescription());
    status.put("running", pressureService.isRunning());
    status.put("bytesPerSecond", currentMode.getBytesPerSecond());
    return Response.ok(status).build();
  }

  @POST
  @Path("/mode/{mode}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response setMode(@PathParam("mode") String modeStr) {
    try {
      AllocationMode mode = AllocationMode.valueOf(modeStr.toUpperCase());
      LOGGER.info(
          String.format(
              "POST /api/pressure/mode/%s - Setting memory pressure mode to: %s (%.2f MB/sec)",
              modeStr, mode.name(), mode.getBytesPerSecond() / (1024.0 * 1024.0)));

      pressureService.setAllocationMode(mode);

      Map<String, Object> result = new HashMap<>();
      result.put("success", true);
      result.put("mode", mode.name());
      result.put("description", mode.getDescription());
      result.put("bytesPerSecond", mode.getBytesPerSecond());

      return Response.ok(result).build();
    } catch (IllegalArgumentException e) {
      LOGGER.warning(String.format("POST /api/pressure/mode/%s - Invalid mode requested", modeStr));

      Map<String, Object> error = new HashMap<>();
      error.put("success", false);
      error.put("error", "Invalid mode: " + modeStr);
      error.put("validModes", new String[] {"OFF", "LOW", "MEDIUM", "HIGH", "EXTREME"});
      return Response.status(Response.Status.BAD_REQUEST).entity(error).build();
    }
  }

  @POST
  @Path("/scenario/{scenario}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response applyScenario(@PathParam("scenario") String scenarioName) {
    try {
      StressScenario scenario = StressScenario.valueOf(scenarioName.toUpperCase());
      LOGGER.info("Applying scenario: " + scenario.name());

      pressureService.setAllocationMode(scenario.getMode());

      return Response.ok(
              Map.of(
                  "scenario", scenario.name(),
                  "description", scenario.getDescription(),
                  "mode", scenario.getMode(),
                  "status", "applied"))
          .build();

    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("error", "Invalid scenario: " + scenarioName))
          .build();
    }
  }

  @GET
  @Path("/scenarios")
  @Produces(MediaType.APPLICATION_JSON)
  public Response listScenarios() {
    List<Map<String, String>> scenarios =
        Arrays.stream(StressScenario.values())
            .map(
                s ->
                    Map.of(
                        "name", s.name(),
                        "description", s.getDescription(),
                        "mode", s.getMode().toString()))
            .collect(Collectors.toList());

    return Response.ok(scenarios).build();
  }

  @GET
  @Path("/modes")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getModes() {
    LOGGER.fine("GET /api/pressure/modes - Listing all allocation modes");

    Map<String, Map<String, Object>> modes = new HashMap<>();

    for (AllocationMode mode : AllocationMode.values()) {
      Map<String, Object> modeInfo = new HashMap<>();
      modeInfo.put("name", mode.name());
      modeInfo.put("description", mode.getDescription());
      modeInfo.put("bytesPerSecond", mode.getBytesPerSecond());
      modes.put(mode.name(), modeInfo);
    }

    return Response.ok(modes).build();
  }
}
