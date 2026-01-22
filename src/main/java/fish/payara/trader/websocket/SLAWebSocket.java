package fish.payara.trader.websocket;

import fish.payara.trader.util.InstanceUtils;
import jakarta.inject.Inject;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * WebSocket endpoint for SLA violation alerts. Pushes real-time notifications when GC pauses exceed thresholds.
 *
 * <p>
 * Connect to ws://host:port/context/sla to receive violation alerts.
 *
 * <p>
 * Message format:
 *
 * <pre>
 * {
 *   "type": "sla-violation",
 *   "pauseTimeMs": 45,
 *   "threshold": ">10ms",
 *   "timestamp": 1234567890,
 *   "instanceName": "c4-1"
 * }
 * </pre>
 *
 * <p>
 * NOTE: Manual JSON construction via STR templates is intentional - it generates garbage to stress-test the garbage collector for demo purposes.
 */
@ServerEndpoint("/sla")
public class SLAWebSocket {

    private static final Logger LOGGER = Logger.getLogger(SLAWebSocket.class.getName());

    @Inject
    private MarketDataBroadcaster broadcaster;

    @OnOpen
    public void onOpen(Session session) {
        LOGGER.info("SLA WebSocket connection opened: " + session.getId());
        broadcaster.addSession(session);

        try {
            String instanceName = InstanceUtils.getInstanceName();
            String welcomeJson = "{\"type\":\"info\",\"message\":\"Connected to TradeStreamEE SLA monitoring\",\"instance\":\"" + instanceName + "\"}";
            session.getBasicRemote().sendText(welcomeJson);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to send SLA welcome message", e);
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        LOGGER.info("SLA WebSocket connection closed: " + session.getId() + ", reason: " + closeReason.getReasonPhrase());
        broadcaster.removeSession(session);
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        LOGGER.log(Level.WARNING, "SLA WebSocket error for session: " + session.getId(), throwable);
        broadcaster.removeSession(session);
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        LOGGER.fine("Received message on SLA WebSocket: " + message);

        try {
            session.getBasicRemote().sendText("{\"type\":\"ack\",\"message\":\"Message received\"}");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to send acknowledgment", e);
        }
    }
}
