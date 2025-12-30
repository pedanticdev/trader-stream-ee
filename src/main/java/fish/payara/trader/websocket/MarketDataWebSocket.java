package fish.payara.trader.websocket;

import jakarta.inject.Inject;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * WebSocket endpoint for streaming market data to clients.
 *
 * <p>
 * Clients connect to ws://host:port/context/market-data and receive real-time JSON market data updates.
 */
@ServerEndpoint("/market-data")
public class MarketDataWebSocket {

    private static final Logger LOGGER = Logger.getLogger(MarketDataWebSocket.class.getName());

    @Inject
    private MarketDataBroadcaster broadcaster;

    @Inject
    @ConfigProperty(name = "TRADER_INGESTION_MODE", defaultValue = "DIRECT")
    private String ingestionMode;

    @OnOpen
    public void onOpen(Session session) {
        LOGGER.info("WebSocket connection opened: " + session.getId());
        broadcaster.addSession(session);

        try {
            String welcomeJson = String.format("{\"type\":\"info\",\"message\":\"Connected to TradeStreamEE market data feed\",\"mode\":\"%s\"}",
                            ingestionMode);
            session.getBasicRemote().sendText(welcomeJson);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to send welcome message", e);
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        LOGGER.info("WebSocket connection closed: " + session.getId() + ", reason: " + closeReason.getReasonPhrase());
        broadcaster.removeSession(session);
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        LOGGER.log(Level.WARNING, "WebSocket error for session: " + session.getId(), throwable);
        broadcaster.removeSession(session);
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        LOGGER.fine("Received message from client: " + message);

        try {
            session.getBasicRemote().sendText("{\"type\":\"ack\",\"message\":\"Message received\"}");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to send acknowledgment", e);
        }
    }
}
