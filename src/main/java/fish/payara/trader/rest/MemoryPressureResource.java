package fish.payara.trader.rest;

import fish.payara.trader.pressure.AllocationMode;
import fish.payara.trader.pressure.MemoryPressureService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.HashMap;
import java.util.Map;

/**
 * REST endpoint for controlling memory pressure testing
 */
@Path("/pressure")
public class MemoryPressureResource {

    @Inject
    private MemoryPressureService pressureService;

    @GET
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("currentMode", pressureService.getCurrentMode().name());
        status.put("description", pressureService.getCurrentMode().getDescription());
        status.put("running", pressureService.isRunning());
        status.put("bytesPerSecond", pressureService.getCurrentMode().getBytesPerSecond());
        return Response.ok(status).build();
    }

    @POST
    @Path("/mode/{mode}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response setMode(@PathParam("mode") String modeStr) {
        try {
            AllocationMode mode = AllocationMode.valueOf(modeStr.toUpperCase());
            pressureService.setAllocationMode(mode);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("mode", mode.name());
            result.put("description", mode.getDescription());
            result.put("bytesPerSecond", mode.getBytesPerSecond());

            return Response.ok(result).build();
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Invalid mode: " + modeStr);
            error.put("validModes", new String[]{"OFF", "LOW", "MEDIUM", "HIGH", "EXTREME"});
            return Response.status(Response.Status.BAD_REQUEST).entity(error).build();
        }
    }

    @GET
    @Path("/modes")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getModes() {
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
