package fish.payara.trader.websocket;

import fish.payara.trader.matching.websocket.ExecutionBroadcaster;
import jakarta.inject.Inject;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import java.util.logging.Level;
import java.util.logging.Logger;

@ServerEndpoint("/executions")
public class ExecutionWebSocket {

    private static final Logger LOGGER = Logger.getLogger(ExecutionWebSocket.class.getName());

    @Inject
    private ExecutionBroadcaster broadcaster;

    @OnOpen
    public void onOpen(Session session) {
        LOGGER.info("Execution WebSocket opened: " + session.getId());
        broadcaster.addSession(session);
        try {
            session.getBasicRemote().sendText("{\"type\":\"info\",\"message\":\"Connected to execution feed\"}");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to send execution welcome message", e);
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        LOGGER.info("Execution WebSocket closed: " + session.getId());
        broadcaster.removeSession(session);
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        LOGGER.log(Level.WARNING, "Execution WebSocket error: " + session.getId(), throwable);
        broadcaster.removeSession(session);
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        LOGGER.fine("Execution WebSocket received: " + message);
    }
}
