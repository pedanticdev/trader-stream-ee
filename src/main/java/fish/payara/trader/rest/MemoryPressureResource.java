package fish.payara.trader.rest;

import fish.payara.trader.dto.PressureStatusResponse;
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
        PressureStatusResponse status = PressureStatusResponse.from(pressureService.getCurrentMode(), pressureService.isRunning());
        return Response.ok(status).build();
    }

    @POST
    @Path("/mode/{mode}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response setMode(@PathParam("mode") String modeStr) {
        try {
            AllocationMode mode = AllocationMode.valueOf(modeStr.toUpperCase());
            LOGGER.info("POST /api/pressure/mode/" + modeStr + " - Setting memory pressure mode to: " + mode.name());

            pressureService.setAllocationMode(mode);

            PressureStatusResponse result = PressureStatusResponse.from(mode, true);
            return Response.ok(result).build();
        } catch (IllegalArgumentException e) {
            LOGGER.warning("POST /api/pressure/mode/" + modeStr + " - Invalid mode requested");

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

        Map<String, PressureStatusResponse> modes = new HashMap<>();
        for (AllocationMode mode : AllocationMode.values()) {
            modes.put(mode.name(), PressureStatusResponse.from(mode, false));
        }

        return Response.ok(modes).build();
    }
}
