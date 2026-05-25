package fish.payara.trader.rest;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jdk.jfr.Configuration;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.RecordingState;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
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
@Path("/jfr")
public class JfrRecordingResource {

    private static final Logger LOGGER = Logger.getLogger(JfrRecordingResource.class.getName());
    private static final java.nio.file.Path RECORDINGS_DIR = java.nio.file.Paths.get("/opt/payara/recordings");
    private static final java.nio.file.Path JFC_SETTINGS_DIR = java.nio.file.Paths.get("/opt/payara/jfr-settings");

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

    /**
     * Start a new JFR recording.
     *
     * @param name
     *            human-readable recording name; used as the dumped filename prefix
     * @param durationSeconds
     *            wall-clock duration before auto-stop and dump to disk
     * @param maxSizeBytes
     *            hard cap on recording size; older events are evicted past this
     * @param settings
     *            optional JFC profile: 'default', 'profile', or a workshop name such as 'tradestream-workshop' loaded from /opt/payara/jfr-settings. When
     *            omitted, an opinionated workshop event set is enabled programmatically.
     */
    @POST
    @Path("/recording/start")
    @Produces(MediaType.APPLICATION_JSON)
    public Response startRecording(@QueryParam("name") @DefaultValue("ad-hoc") String name,
                    @QueryParam("durationSeconds") @DefaultValue("60") int durationSeconds,
                    @QueryParam("maxSize") @DefaultValue("1073741824") long maxSizeBytes, @QueryParam("settings") String settings) {

        if (!FlightRecorder.isAvailable()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(Map.of("error", "Flight Recorder is not available")).build();
        }

        Recording recording;
        try {
            recording = createRecording(name, settings);
        } catch (IOException | ParseException e) {
            LOGGER.log(Level.WARNING, "Failed to load JFC settings: " + settings, e);
            return Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of("error", "Failed to load settings", "settings", settings, "message", e.getMessage()))
                            .build();
        }

        recording.setMaxSize(maxSizeBytes);
        recording.setDuration(Duration.ofSeconds(durationSeconds));
        recording.start();

        final long recordingId = recording.getId();
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep((durationSeconds + 2) * 1000L);
                Recording r = findRecording(recordingId);
                if (r == null) {
                    return;
                }
                if (r.getState() == RecordingState.RUNNING) {
                    r.stop();
                }
                if (r.getState() == RecordingState.STOPPED) {
                    dumpRecording(r);
                    LOGGER.info("Dumped recording: " + r.getName() + " (" + recordingId + ")");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warning("Recording stop interrupted for: " + name);
            }
        });

        LOGGER.info("Started JFR recording: " + name + " (" + recordingId + ") for " + durationSeconds + "s, settings="
                        + (settings == null ? "<workshop-default>" : settings));

        Map<String, Object> response = new HashMap<>();
        response.put("recordingId", recordingId);
        response.put("name", name);
        response.put("durationSeconds", durationSeconds);
        response.put("maxSizeBytes", maxSizeBytes);
        response.put("settings", settings == null ? "workshop-default" : settings);
        response.put("state", "RUNNING");
        return Response.ok(response).build();
    }

    /**
     * Build a recording with either a named JFC profile or the workshop default event set.
     *
     * <p>
     * Resolution order for {@code settings}:
     * <ol>
     * <li>{@code null} or {@code "workshop-default"} — programmatic event list (back-compat).</li>
     * <li>{@code "default"} or {@code "profile"} — JDK-bundled JFC profile via {@link Configuration#getConfiguration(String)}.</li>
     * <li>Anything else — looked up as {@code /opt/payara/jfr-settings/<name>.jfc}.</li>
     * </ol>
     */
    private Recording createRecording(String name, String settings) throws IOException, ParseException {
        if (settings == null || settings.isBlank() || "workshop-default".equalsIgnoreCase(settings)) {
            Recording recording = new Recording();
            recording.setName(name);
            applyWorkshopDefaultEvents(recording);
            return recording;
        }

        if ("default".equalsIgnoreCase(settings) || "profile".equalsIgnoreCase(settings)) {
            Recording recording = new Recording(Configuration.getConfiguration(settings.toLowerCase(Locale.ROOT)));
            recording.setName(name);
            return recording;
        }

        String safeName = settings.endsWith(".jfc") ? settings : settings + ".jfc";
        java.nio.file.Path jfcPath = JFC_SETTINGS_DIR.resolve(safeName).normalize();
        if (!jfcPath.startsWith(JFC_SETTINGS_DIR.normalize())) {
            throw new IOException("Settings path escapes the JFC settings directory: " + settings);
        }
        if (!Files.exists(jfcPath)) {
            throw new IOException("JFC settings file not found: " + jfcPath);
        }
        try (InputStream in = Files.newInputStream(jfcPath)) {
            Recording recording = new Recording(Configuration.create(new java.io.InputStreamReader(in)));
            recording.setName(name);
            return recording;
        }
    }

    private void applyWorkshopDefaultEvents(Recording recording) {
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

    /** List all .jfr files available for download */
    @GET
    @Path("/files")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listFiles() {
        try {
            if (!Files.exists(RECORDINGS_DIR)) {
                return Response.ok(Map.of("files", List.of(), "message", "Recordings directory does not exist")).build();
            }

            List<Map<String, Object>> files = Files.list(RECORDINGS_DIR).filter(p -> p.toString().endsWith(".jfr")).map(p -> {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
                    Map<String, Object> file = new HashMap<>();
                    file.put("filename", p.getFileName().toString());
                    file.put("size", attrs.size());
                    file.put("sizeFormatted", formatBytes(attrs.size()));
                    file.put("lastModified", attrs.lastModifiedTime().toMillis());
                    file.put("lastModifiedFormatted", Instant.ofEpochMilli(attrs.lastModifiedTime().toMillis()).toString());
                    file.put("downloadUrl", "/api/jfr/download/" + p.getFileName().toString());
                    return file;
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to read attributes for: " + p, e);
                    return null;
                }
            }).filter(Objects::nonNull).toList();

            return Response.ok(Map.of("files", files, "count", files.size())).build();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to list recording files", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Map.of("error", "Failed to list files", "message", e.getMessage())).build();
        }
    }

    /** Download a specific JFR file */
    @GET
    @Path("/download/{filename}")
    public Response downloadFile(@PathParam("filename") String filename) {
        if (filename == null || filename.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "Filename is required")).type(MediaType.APPLICATION_JSON).build();
        }

        if (!filename.endsWith(".jfr")) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "Only .jfr files are allowed")).type(MediaType.APPLICATION_JSON).build();
        }

        java.nio.file.Path filePath = RECORDINGS_DIR.resolve(filename).normalize();
        if (!filePath.startsWith(RECORDINGS_DIR.normalize())) {
            return Response.status(Response.Status.FORBIDDEN)
                            .entity(Map.of("error", "Filename escapes the recordings directory", "filename", filename))
                            .type(MediaType.APPLICATION_JSON)
                            .build();
        }

        if (!Files.exists(filePath)) {
            return Response.status(Response.Status.NOT_FOUND)
                            .entity(Map.of("error", "File not found", "filename", filename))
                            .type(MediaType.APPLICATION_JSON)
                            .build();
        }

        try {
            byte[] fileContent = Files.readAllBytes(filePath);
            String contentDisposition = "attachment; filename=\"" + filename + "\"";

            return Response.ok(fileContent)
                            .type("application/octet-stream")
                            .header("Content-Disposition", contentDisposition)
                            .header("Content-Length", fileContent.length)
                            .build();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to read file: " + filename, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity(Map.of("error", "Failed to read file", "message", e.getMessage()))
                            .type(MediaType.APPLICATION_JSON)
                            .build();
        }
    }

    /** Format bytes to human-readable size */
    private String formatBytes(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024)
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
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
