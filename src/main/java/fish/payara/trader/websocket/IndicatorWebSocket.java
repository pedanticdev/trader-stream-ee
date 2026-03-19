package fish.payara.trader.websocket;

import jakarta.inject.Inject;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import java.util.logging.Level;
import java.util.logging.Logger;

@ServerEndpoint("/indicators")
public class IndicatorWebSocket {

    private static final Logger LOGGER = Logger.getLogger(IndicatorWebSocket.class.getName());

    @Inject
    private MarketDataBroadcaster broadcaster;

    @OnOpen
    public void onOpen(Session session) {
        LOGGER.info("Indicator WebSocket opened: " + session.getId());
        broadcaster.addSession(session);
        try {
            session.getBasicRemote().sendText("{\"type\":\"info\",\"message\":\"Connected to indicator feed\"}");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to send indicator welcome message", e);
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        LOGGER.info("Indicator WebSocket closed: " + session.getId());
        broadcaster.removeSession(session);
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        LOGGER.log(Level.WARNING, "Indicator WebSocket error: " + session.getId(), throwable);
        broadcaster.removeSession(session);
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        LOGGER.fine("Indicator WebSocket received: " + message);
    }
}
