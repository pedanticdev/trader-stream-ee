package fish.payara.trader.matching.websocket;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.topic.ITopic;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.Session;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Broadcaster for execution WebSocket messages. Follows the same Hazelcast topic pattern as MarketDataBroadcaster.
 */
@ApplicationScoped
public class ExecutionBroadcaster {

    private static final Logger LOGGER = Logger.getLogger(ExecutionBroadcaster.class.getName());
    private static final String TOPIC_NAME = "execution-broadcast";

    private final Set<Session> sessions = ConcurrentHashMap.newKeySet();

    @Inject
    private HazelcastInstance hazelcastInstance;

    private ITopic<String> clusterTopic;

    private long messagesSent = 0;
    private long lastStatsTime = System.currentTimeMillis();

    @PostConstruct
    public void init() {
        try {
            if (hazelcastInstance != null) {
                clusterTopic = hazelcastInstance.getTopic(TOPIC_NAME);
                clusterTopic.addMessageListener(message -> {
                    broadcastLocal(message.getMessageObject());
                });
                LOGGER.info("ExecutionBroadcaster subscribed to Hazelcast topic: " + TOPIC_NAME);
            } else {
                LOGGER.info("Hazelcast not available - ExecutionBroadcaster running in standalone mode");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize ExecutionBroadcaster Hazelcast topic", e);
        }
    }

    public void addSession(Session session) {
        sessions.add(session);
        LOGGER.info("Execution WebSocket session added. Total: " + sessions.size());
    }

    public void removeSession(Session session) {
        sessions.remove(session);
        LOGGER.info("Execution WebSocket session removed. Total: " + sessions.size());
    }

    public void broadcast(String jsonMessage) {
        if (clusterTopic != null) {
            try {
                clusterTopic.publish(jsonMessage);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to publish execution to Hazelcast topic", e);
                broadcastLocal(jsonMessage);
            }
        } else {
            broadcastLocal(jsonMessage);
        }
    }

    private void broadcastLocal(String jsonMessage) {
        if (sessions.isEmpty()) {
            return;
        }

        sessions.removeIf(session -> {
            if (!session.isOpen()) {
                return true;
            }
            try {
                session.getAsyncRemote().sendText(jsonMessage);
                return false;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to send execution message", e);
                return true;
            }
        });

        messagesSent++;
        logStatistics();
    }

    public int getSessionCount() {
        return sessions.size();
    }

    private void logStatistics() {
        long now = System.currentTimeMillis();
        if (now - lastStatsTime > 10000) {
            LOGGER.info(String.format("Execution WebSocket Stats - Sessions: %d, Messages: %,d (%.1f msg/sec)", sessions.size(), messagesSent,
                            messagesSent / ((now - lastStatsTime) / 1000.0)));
            lastStatsTime = now;
            messagesSent = 0;
        }
    }
}
