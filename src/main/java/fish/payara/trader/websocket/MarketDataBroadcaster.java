package fish.payara.trader.websocket;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.Session;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Broadcaster for market data WebSocket messages.
 *
 * Maintains a set of active WebSocket sessions and broadcasts
 * JSON messages to all connected clients.
 *
 * Note: JSON string creation intentionally generates garbage
 * to stress-test Azul's Pauseless GC (C4).
 */
@ApplicationScoped
public class MarketDataBroadcaster {

    private static final Logger LOGGER = Logger.getLogger(MarketDataBroadcaster.class.getName());

    private final Set<Session> sessions = ConcurrentHashMap.newKeySet();

    // Statistics
    private long messagesSent = 0;
    private long lastStatsTime = System.currentTimeMillis();

    /**
     * Register a new WebSocket session
     */
    public void addSession(Session session) {
        sessions.add(session);
        LOGGER.info("WebSocket session added. Total sessions: " + sessions.size());
    }

    /**
     * Unregister a WebSocket session
     */
    public void removeSession(Session session) {
        sessions.remove(session);
        LOGGER.info("WebSocket session removed. Total sessions: " + sessions.size());
    }

    /**
     * Broadcast JSON message to all connected clients
     *
     * This method intentionally creates string allocations to
     * generate garbage and stress the garbage collector.
     */
    public void broadcast(String jsonMessage) {
        if (sessions.isEmpty()) {
            return;
        }

        // Iterate through all sessions and send the message
        sessions.removeIf(session -> {
            if (!session.isOpen()) {
                LOGGER.fine("Removing closed session: " + session.getId());
                return true;
            }

            try {
                // Send async to avoid blocking
                session.getAsyncRemote().sendText(jsonMessage);
                messagesSent++;
                return false;

            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to send message to session: " + session.getId(), e);
                return true;  // Remove problematic session
            }
        });

        logStatistics();
    }

    /**
     * Broadcast to all clients with additional garbage generation for GC stress.
     *
     * This method wraps the original message in a larger JSON envelope with padding.
     * This increases both memory allocation (String construction) and network bandwidth usage,
     * simulating a heavier protocol or inefficient data packaging.
     */
    public void broadcastWithArtificialLoad(String jsonMessage) {
        // Generate 1KB of padding to increase payload size and allocation
        String padding = "X".repeat(1024);

        // Wrap the original message in a new JSON structure
        // We use StringBuilder to explicitly construct the new JSON string
        String enrichedMessage = new StringBuilder(jsonMessage.length() + padding.length() + 100)
            .append("{\"wrapped\":true,")
            .append("\"timestamp\":").append(System.nanoTime()).append(",")
            .append("\"padding\":\"").append(padding).append("\",")
            .append("\"data\":").append(jsonMessage)
            .append("}")
            .toString();

        broadcast(enrichedMessage);
    }

    /**
     * Get count of active sessions
     */
    public int getSessionCount() {
        return sessions.size();
    }

    /**
     * Log statistics periodically
     */
    private void logStatistics() {
        long now = System.currentTimeMillis();
        if (now - lastStatsTime > 10000) {  // Log every 10 seconds
            LOGGER.info(String.format(
                "WebSocket Stats - Active sessions: %d, Messages sent: %,d (%.1f msg/sec)",
                sessions.size(),
                messagesSent,
                messagesSent / ((now - lastStatsTime) / 1000.0)
            ));
            lastStatsTime = now;
            messagesSent = 0;
        }
    }
}
