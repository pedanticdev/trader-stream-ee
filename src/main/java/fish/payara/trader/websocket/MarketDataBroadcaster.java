package fish.payara.trader.websocket;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.topic.ITopic;
import com.hazelcast.topic.Message;
import com.hazelcast.topic.MessageListener;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.Session;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Broadcaster for market data WebSocket messages.
 *
 * <p>Maintains a set of active WebSocket sessions and broadcasts JSON messages to all connected
 * clients.
 *
 * <p>In clustered mode, uses Hazelcast distributed topics to broadcast messages across all cluster
 * members, ensuring all WebSocket clients receive data regardless of which instance they connect
 * to.
 *
 * <p>Note: JSON string creation intentionally generates garbage to stress-test Azul's Pauseless GC
 * (C4).
 */
@ApplicationScoped
public class MarketDataBroadcaster {

  private static final Logger LOGGER = Logger.getLogger(MarketDataBroadcaster.class.getName());
  private static final String TOPIC_NAME = "market-data-broadcast";

  private final Set<Session> sessions = ConcurrentHashMap.newKeySet();

  @Inject private HazelcastInstance hazelcastInstance;

  @Inject private fish.payara.trader.monitoring.SLAMonitorService slaMonitor;

  private ITopic<String> clusterTopic;

  // Statistics
  private long messagesSent = 0;
  private long lastStatsTime = System.currentTimeMillis();

  /** Initialize Hazelcast topic subscription for cluster-wide broadcasting */
  @PostConstruct
  public void init() {
    try {
      if (hazelcastInstance != null) {
        clusterTopic = hazelcastInstance.getTopic(TOPIC_NAME);
        clusterTopic.addMessageListener(
            new MessageListener<String>() {
              @Override
              public void onMessage(Message<String> message) {
                // Broadcast to local WebSocket sessions only
                broadcastLocal(message.getMessageObject());
              }
            });
        LOGGER.info("Subscribed to Hazelcast topic: " + TOPIC_NAME + " (clustered mode)");
      } else {
        LOGGER.info("Hazelcast not available - running in standalone mode");
      }
    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, "Failed to initialize Hazelcast topic subscription", e);
      // Continue without clustering
    }
  }

  /** Register a new WebSocket session */
  public void addSession(Session session) {
    sessions.add(session);
    LOGGER.info("WebSocket session added. Total sessions: " + sessions.size());
  }

  /** Unregister a WebSocket session */
  public void removeSession(Session session) {
    sessions.remove(session);
    LOGGER.info("WebSocket session removed. Total sessions: " + sessions.size());
  }

  /**
   * Broadcast JSON message to all connected clients across the cluster.
   *
   * <p>In clustered mode, publishes to Hazelcast topic which distributes to all cluster members.
   * Each member then broadcasts to its local WebSocket sessions.
   *
   * <p>In standalone mode, broadcasts directly to local sessions.
   *
   * <p>This method intentionally creates string allocations to generate garbage and stress the
   * garbage collector.
   */
  public void broadcast(String jsonMessage) {
    if (clusterTopic != null) {
      // Publish to cluster-wide topic
      try {
        clusterTopic.publish(jsonMessage);
      } catch (Exception e) {
        LOGGER.log(
            Level.SEVERE,
            "Failed to publish to Hazelcast topic, falling back to local broadcast",
            e);
        broadcastLocal(jsonMessage);
      }
    } else {
      // Standalone mode - broadcast locally only
      broadcastLocal(jsonMessage);
    }
  }

  /**
   * Broadcast message to local WebSocket sessions only. Called either in standalone mode or by
   * Hazelcast topic listener.
   */
  private void broadcastLocal(String jsonMessage) {
    if (sessions.isEmpty()) {
      return;
    }

    long startTime = System.currentTimeMillis();

    sessions.removeIf(
        session -> {
          if (!session.isOpen()) {
            return true;
          }
          try {
            session.getAsyncRemote().sendText(jsonMessage);
            return false;
          } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to send message", e);
            return true;
          }
        });

    long latency = System.currentTimeMillis() - startTime;
    if (slaMonitor != null) {
      slaMonitor.recordOperation(latency);
    }

    logStatistics();
  }

  /**
   * Broadcast to all clients with additional garbage generation for GC stress.
   *
   * <p>This method wraps the original message in a larger JSON envelope with padding. This
   * increases both memory allocation (String construction) and network bandwidth usage, simulating
   * a heavier protocol or inefficient data packaging.
   */
  public void broadcastWithArtificialLoad(String jsonMessage) {
    // Generate 1KB of padding to increase payload size and allocation
    String padding = "X".repeat(1024);

    // Wrap the original message in a new JSON structure
    // We use StringBuilder to explicitly construct the new JSON string
    String enrichedMessage =
        new StringBuilder(jsonMessage.length() + padding.length() + 100)
            .append("{\"wrapped\":true,")
            .append("\"timestamp\":")
            .append(System.nanoTime())
            .append(",")
            .append("\"padding\":\"")
            .append(padding)
            .append("\",")
            .append("\"data\":")
            .append(jsonMessage)
            .append("}")
            .toString();

    broadcast(enrichedMessage);
  }

  /** Get count of active sessions */
  public int getSessionCount() {
    return sessions.size();
  }

  /** Log statistics periodically */
  private void logStatistics() {
    long now = System.currentTimeMillis();
    if (now - lastStatsTime > 10000) { // Log every 10 seconds
      LOGGER.info(
          String.format(
              "WebSocket Stats - Active sessions: %d, Messages sent: %,d (%.1f msg/sec)",
              sessions.size(), messagesSent, messagesSent / ((now - lastStatsTime) / 1000.0)));
      lastStatsTime = now;
      messagesSent = 0;
    }
  }
}
