package fish.payara.trader.rest;

import fish.payara.trader.demo.DemoPresetService;
import fish.payara.trader.demo.DemoPresetService.PresetExecutionContext;
import fish.payara.trader.dto.DemoPresetResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * REST endpoints for demo preset management and execution. Presets are executed client-side via step-by-step REST calls (hybrid approach).
 */
@Path("/demo")
public class DemoPresetResource {

    private static final Logger LOGGER = Logger.getLogger(DemoPresetResource.class.getName());

    @Inject
    private DemoPresetService presetService;

    @GET
    @Path("/presets")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllPresets() {
        LOGGER.info("GET /api/demo/presets - Listing all demo presets");
        return Response.ok(presetService.getAllPresets()).build();
    }

    @GET
    @Path("/preset/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPreset(@PathParam("id") String id) {
        LOGGER.info("GET /api/demo/preset/" + id + " - Getting demo preset");
        DemoPresetResponse preset = presetService.getPreset(id);
        if (preset == null) {
            return Response.status(Response.Status.NOT_FOUND).entity(error("Preset not found: " + id)).build();
        }
        return Response.ok(preset).build();
    }

    @POST
    @Path("/preset/{id}/start")
    @Produces(MediaType.APPLICATION_JSON)
    public Response startPreset(@PathParam("id") String id) {
        LOGGER.info("POST /api/demo/preset/" + id + "/start - Starting demo preset");
        PresetExecutionContext context = presetService.initializeExecution(id);
        if (context == null) {
            return Response.status(Response.Status.NOT_FOUND).entity(error("Preset not found: " + id)).build();
        }
        return Response.ok(toExecutionMap(context)).build();
    }

    @POST
    @Path("/execution/{executionId}/step/{stepIndex}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response executeStep(@PathParam("executionId") String executionId, @PathParam("stepIndex") int stepIndex) {
        LOGGER.info("POST /api/demo/execution/" + executionId + "/step/" + stepIndex + " - Executing step");
        boolean success = presetService.executeStep(executionId, stepIndex);
        if (!success) {
            return Response.status(Response.Status.BAD_REQUEST).entity(error("Failed to execute step " + stepIndex)).build();
        }
        PresetExecutionContext context = presetService.getExecution(executionId);
        return Response.ok(toExecutionMap(context)).build();
    }

    @GET
    @Path("/execution/{executionId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getExecution(@PathParam("executionId") String executionId) {
        LOGGER.info("GET /api/demo/execution/" + executionId + " - Getting execution status");
        PresetExecutionContext context = presetService.getExecution(executionId);
        if (context == null) {
            return Response.status(Response.Status.NOT_FOUND).entity(error("Execution not found: " + executionId)).build();
        }
        return Response.ok(toExecutionMap(context)).build();
    }

    @POST
    @Path("/execution/{executionId}/cancel")
    @Produces(MediaType.APPLICATION_JSON)
    public Response cancelExecution(@PathParam("executionId") String executionId) {
        LOGGER.info("POST /api/demo/execution/" + executionId + "/cancel - Cancelling execution");
        presetService.cancelExecution(executionId);
        return Response.ok(Map.of("status", "cancelled")).build();
    }

    private Map<String, Object> toExecutionMap(PresetExecutionContext context) {
        Map<String, Object> map = new HashMap<>();
        map.put("executionId", context.executionId());
        map.put("presetId", context.presetId());
        map.put("startTime", context.startTime());
        map.put("currentStepIndex", context.currentStepIndex());
        map.put("totalSteps", context.preset().steps().size());
        map.put("isComplete", context.isComplete());
        map.put("isCancelled", context.isCancelled());
        map.put("completedSteps", context.completedSteps());
        return map;
    }

    private Map<String, String> error(String message) {
        return Map.of("error", message);
    }
}
