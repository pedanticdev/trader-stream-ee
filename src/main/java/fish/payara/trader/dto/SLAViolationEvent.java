package fish.payara.trader.dto;

/**
 * WebSocket event for SLA violation alerts. Pushed to frontend for real-time visual feedback (flashing).
 */
public record SLAViolationEvent(String type, long pauseTimeMs, String threshold, long timestamp, String instanceName) {
    public static SLAViolationEvent create(long pauseTimeMs, String threshold, String instanceName) {
        return new SLAViolationEvent("sla-violation", pauseTimeMs, threshold, System.currentTimeMillis(), instanceName);
    }

    /**
     * Converts to JSON for WebSocket transmission. NOTE: Manual JSON construction is intentional - it generates garbage to stress-test the garbage collector
     * for demo purposes.
     */
    public String toJson() {
        return "{\"type\":\"" + type + "\",\"pauseTimeMs\":" + pauseTimeMs + ",\"threshold\":\"" + threshold + "\",\"timestamp\":" + timestamp
                        + ",\"instanceName\":\"" + instanceName + "\"}";
    }
}
