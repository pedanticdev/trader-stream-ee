package fish.payara.trader.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

import jakarta.websocket.RemoteEndpoint.Async;
import jakarta.websocket.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketDataBroadcasterTest {

    private MarketDataBroadcaster broadcaster;

    @Mock
    private Session session1;

    @Mock
    private Session session2;

    @Mock
    private Async asyncRemote1;

    @Mock
    private Async asyncRemote2;

    @BeforeEach
    void setUp() {
        broadcaster = new MarketDataBroadcaster();
        lenient().when(session1.getId()).thenReturn("session-1");
        lenient().when(session2.getId()).thenReturn("session-2");
        lenient().when(session1.getAsyncRemote()).thenReturn(asyncRemote1);
        lenient().when(session2.getAsyncRemote()).thenReturn(asyncRemote2);
        lenient().when(session1.isOpen()).thenReturn(true);
        lenient().when(session2.isOpen()).thenReturn(true);
    }

    @Test
    void testAddSession() {
        broadcaster.addSession(session1);

        assertThat(broadcaster.getSessionCount()).isEqualTo(1);
    }

    @Test
    void testAddMultipleSessions() {
        broadcaster.addSession(session1);
        broadcaster.addSession(session2);

        assertThat(broadcaster.getSessionCount()).isEqualTo(2);
    }

    @Test
    void testRemoveSession() {
        broadcaster.addSession(session1);
        broadcaster.addSession(session2);

        broadcaster.removeSession(session1);

        assertThat(broadcaster.getSessionCount()).isEqualTo(1);
    }

    @Test
    void testBroadcastToAllSessions() {
        broadcaster.addSession(session1);
        broadcaster.addSession(session2);

        String message = "{\"type\":\"trade\",\"price\":100}";
        broadcaster.broadcast(message);

        verify(asyncRemote1).sendText(eq(message));
        verify(asyncRemote2).sendText(eq(message));
    }

    @Test
    void testBroadcastRemovesClosedSessions() {
        broadcaster.addSession(session1);
        broadcaster.addSession(session2);

        when(session1.isOpen()).thenReturn(false);

        String message = "{\"type\":\"trade\",\"price\":100}";
        broadcaster.broadcast(message);

        verify(asyncRemote1, never()).sendText(any());
        verify(asyncRemote2).sendText(eq(message));

        assertThat(broadcaster.getSessionCount()).isEqualTo(1);
    }

    @Test
    void testBroadcastHandlesFailure() {
        broadcaster.addSession(session1);
        broadcaster.addSession(session2);

        String message = "{\"type\":\"trade\",\"price\":100}";

        doThrow(new RuntimeException("Send failed")).when(asyncRemote1).sendText(message);

        broadcaster.broadcast(message);

        verify(asyncRemote2).sendText(eq(message));
        assertThat(broadcaster.getSessionCount()).isEqualTo(1);
    }

    @Test
    void testGetSessionCountWhenEmpty() {
        assertThat(broadcaster.getSessionCount()).isEqualTo(0);
    }

    @Test
    void testBroadcastWithArtificialLoad() {
        broadcaster.addSession(session1);

        String baseMessage = "{\"type\":\"trade\",\"price\":100}";
        broadcaster.broadcastWithArtificialLoad(baseMessage);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(asyncRemote1).sendText(messageCaptor.capture());

        String sentMessage = messageCaptor.getValue();
        assertThat(sentMessage).contains(baseMessage);
        assertThat(sentMessage).contains("\"wrapped\"");
        assertThat(sentMessage).contains("\"padding\"");
        assertThat(sentMessage.length()).isGreaterThan(baseMessage.length());
    }

    @Test
    void testConcurrentSessionManagement() throws InterruptedException {
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                Session mockSession = mock(Session.class);
                when(mockSession.getId()).thenReturn("session-" + index);
                when(mockSession.getAsyncRemote()).thenReturn(mock(Async.class));
                when(mockSession.isOpen()).thenReturn(true);
                broadcaster.addSession(mockSession);
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertThat(broadcaster.getSessionCount()).isEqualTo(threadCount);
    }
}
