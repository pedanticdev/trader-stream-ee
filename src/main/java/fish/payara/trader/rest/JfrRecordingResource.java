package fish.payara.trader.rest;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.RecordingState;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * REST endpoint for Java Flight Recorder (JFR) management.
 *
 * <p>
 * Provides endpoints for:
 * <ul>
 * <li>Checking JFR availability and recording status</li>
 * <li>Starting ad-hoc recordings with configurable duration</li>
 * <li>Listing active and stopped recordings</li>
 * <li>Downloading recordings as .jfr files</li>
 * </ul>
 *
 * <p>
 * Requires JDK Flight Recorder to be enabled (included with Azul Platform Prime and Oracle JDK).
 */
@Path("/api/jfr")
public class JfrRecordingResource {

    private static final Logger LOGGER = Logger.getLogger(JfrRecordingResource.class.getName());
    private static final java.nio.file.Path RECORDINGS_DIR = java.nio.file.Paths.get("/opt/payara/recordings");

    /** Get JFR availability and recording status */
    @GET
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStatus() {
        Map<String, Object> status = new HashMap<>();

        boolean available = FlightRecorder.isAvailable();
        boolean initialized = FlightRecorder.isInitialized();

        status.put("jfrAvailable", available);
        status.put("jfrInitialized", initialized);

        if (!available) {
            status.put("message", "Flight Recorder is not available on this JVM");
            status.put("suggestion", "Use Azul Platform Prime or Oracle JDK with -XX:+FlightRecorder");
            return Response.ok(status).build();
        }

        List<Map<String, Object>> recordings = FlightRecorder.getFlightRecorder()
                        .getRecordings()
                        .stream()
                        .map(this::recordingToMap)
                        .collect(Collectors.toList());

        status.put("recordings", recordings);
        status.put("activeRecordingCount", (int) recordings.stream().filter(r -> "RUNNING".equals(r.get("state"))).count());

        return Response.ok(status).build();
    }

    /** Start a new JFR recording */
    @POST
    @Path("/recording/start")
    @Produces(MediaType.APPLICATION_JSON)
    public Response startRecording(@QueryParam("name") @DefaultValue("ad-hoc") String name,
                    @QueryParam("durationSeconds") @DefaultValue("60") int durationSeconds,
                    @QueryParam("maxSize") @DefaultValue("1073741824") long maxSizeBytes) {

        if (!FlightRecorder.isAvailable()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(Map.of("error", "Flight Recorder is not available")).build();
        }

        Map<String, String> options = new HashMap<>();
        options.put("name", name);
        final long recordingId;
        try (Recording recording = new Recording(options)) {

            recording.setMaxSize(maxSizeBytes);
            recording.setMaxAge(Duration.ofSeconds(durationSeconds));

            recording.enable("jdk.CPUInformation");
            recording.enable("jdk.GCPhaseParallel");
            recording.enable("jdk.ObjectAllocationInNewTLAB");
            recording.enable("jdk.ObjectAllocationOutsideTLAB");
            recording.enable("jdk.VirtualThreadStart");
            recording.enable("jdk.VirtualThreadEnd");
            recording.enable("jdk.ExecutionSample").with("period", "10 ms");

            recording.enable("trade.published");
            recording.enable("quote.published");
            recording.enable("marketdepth.published");
            recording.enable("message.batch.processed");
            recording.enable("websocket.broadcast");
            recording.enable("sbe.encode");
            recording.enable("sbe.decode");
            recording.enable("gc.sla.violation");
            recording.enable("aeron.backpressure");
            recording.enable("burst.mode.activated");

            recording.start();

            recordingId = recording.getId();
        }
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(durationSeconds * 1000L);
                Recording r = findRecording(recordingId);
                if (r != null && r.getState() == RecordingState.RUNNING) {
                    r.stop();
                    dumpRecording(r);
                    LOGGER.info("Auto-stopped recording: " + r.getName() + " (" + recordingId + ")");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warning("Recording stop interrupted for: " + name);
            }
        });

        LOGGER.info("Started JFR recording: " + name + " (" + recordingId + ") for " + durationSeconds + "s");

        return Response.ok(
                        Map.of("recordingId", recordingId, "name", name, "durationSeconds", durationSeconds, "maxSizeBytes", maxSizeBytes, "state", "RUNNING"))
                        .build();
    }

    /** Stop a running recording */
    @POST
    @Path("/recording/stop")
    @Produces(MediaType.APPLICATION_JSON)
    public Response stopRecording(@QueryParam("id") long recordingId) {
        if (!FlightRecorder.isAvailable()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(Map.of("error", "Flight Recorder is not available")).build();
        }

        Recording recording = findRecording(recordingId);
        if (recording == null) {
            return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", "Recording not found", "recordingId", recordingId)).build();
        }

        if (recording.getState() != RecordingState.RUNNING) {
            return Response.ok(Map.of("recordingId", recordingId, "state", recording.getState().toString(), "message", "Recording is not running")).build();
        }

        recording.stop();
        dumpRecording(recording);

        return Response.ok(Map.of("recordingId", recordingId, "state", "STOPPED", "message", "Recording stopped and dumped to disk")).build();
    }

    /** List all recordings with their metadata */
    @GET
    @Path("/recordings")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listRecordings() {
        if (!FlightRecorder.isAvailable()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(Map.of("error", "Flight Recorder is not available")).build();
        }

        List<Map<String, Object>> recordings = FlightRecorder.getFlightRecorder().getRecordings().stream().map(this::recordingToMap).toList();

        return Response.ok(Map.of("recordings", recordings)).build();
    }

    /** Get statistics about recorded events */
    @GET
    @Path("/stats")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getEventStats() {
        if (!FlightRecorder.isAvailable()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(Map.of("error", "Flight Recorder is not available")).build();
        }

        Map<String, Object> stats = new HashMap<>();
        FlightRecorder recorder = FlightRecorder.getFlightRecorder();

        stats.put("recordingCount", recorder.getRecordings().size());
        stats.put("eventTypes", recorder.getEventTypes().size());

        long activeCount = recorder.getRecordings().stream().filter(r -> r.getState() == RecordingState.RUNNING).count();
        stats.put("activeRecordings", activeCount);

        return Response.ok(stats).build();
    }

    /** Dump a recording to disk */
    private void dumpRecording(Recording recording) {
        try {
            Files.createDirectories(RECORDINGS_DIR);
            java.nio.file.Path outputPath = RECORDINGS_DIR.resolve(recording.getName() + "-" + recording.getId() + ".jfr");
            recording.dump(outputPath);
            LOGGER.info("Recording dumped to: " + outputPath);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to dump recording: " + recording.getName(), e);
        }
    }

    /** Find a recording by ID */
    private Recording findRecording(long recordingId) {
        Optional<Recording> found = FlightRecorder.getFlightRecorder().getRecordings().stream().filter(r -> r.getId() == recordingId).findFirst();
        return found.orElse(null);
    }

    /** Convert Recording to Map for JSON serialization */
    private Map<String, Object> recordingToMap(Recording recording) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", recording.getId());
        map.put("name", recording.getName());
        map.put("state", recording.getState().toString());
        map.put("duration", recording.getMaxAge() != null ? recording.getMaxAge().toMillis() + "ms" : "unlimited");
        map.put("maxSize", recording.getMaxSize() + " bytes");
        map.put("startTime", recording.getStartTime());
        map.put("size", recording.getSize());
        return map;
    }
}
