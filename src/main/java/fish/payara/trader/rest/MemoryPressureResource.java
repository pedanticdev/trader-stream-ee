package fish.payara.trader.rest;

import fish.payara.trader.pressure.AllocationMode;
import fish.payara.trader.pressure.MemoryPressureService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

@Path("/pressure")
public class MemoryPressureResource {

    private static final Logger LOGGER = Logger.getLogger(MemoryPressureResource.class.getName());

    @Inject
    private MemoryPressureService pressureService;

    @GET
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStatus() {
        AllocationMode currentMode = pressureService.getCurrentMode();

        Map<String, Object> status = new HashMap<>();
        status.put("currentMode", currentMode.name());
        status.put("description", currentMode.getDescription());
        status.put("running", pressureService.isRunning());
        status.put("allocationRateMBPerSec", currentMode.getAllocationRateMBPerSec());
        status.put("liveSetSizeMB", currentMode.getLiveSetSizeMB());
        status.put("scenarioType", currentMode.getScenarioType());
        return Response.ok(status).build();
    }

    @POST
    @Path("/mode/{mode}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response setMode(@PathParam("mode") String modeStr) {
        try {
            AllocationMode mode = AllocationMode.valueOf(modeStr.toUpperCase());
            LOGGER.info(String.format("POST /api/pressure/mode/%s - Setting memory pressure mode to: %s", modeStr, mode.name()));

            pressureService.setAllocationMode(mode);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("mode", mode.name());
            result.put("description", mode.getDescription());
            result.put("allocationRateMBPerSec", mode.getAllocationRateMBPerSec());
            result.put("scenarioType", mode.getScenarioType());

            return Response.ok(result).build();
        } catch (IllegalArgumentException e) {
            LOGGER.warning(String.format("POST /api/pressure/mode/%s - Invalid mode requested", modeStr));

            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Invalid mode: " + modeStr);
            // Valid modes are all values of AllocationMode
            StringBuilder validModes = new StringBuilder();
            for (AllocationMode m : AllocationMode.values()) {
                validModes.append(m.name()).append(", ");
            }
            error.put("validModes", validModes.toString());
            return Response.status(Response.Status.BAD_REQUEST).entity(error).build();
        }
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
            modeInfo.put("allocationRateMBPerSec", mode.getAllocationRateMBPerSec());
            modeInfo.put("liveSetSizeMB", mode.getLiveSetSizeMB());
            modeInfo.put("scenarioType", mode.getScenarioType());
            modes.put(mode.name(), modeInfo);
        }

        return Response.ok(modes).build();
    }
}
