package fish.payara.trader.aeron;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import fish.payara.trader.sbe.*;
import fish.payara.trader.websocket.MarketDataBroadcaster;
import io.aeron.logbuffer.Header;
import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketDataFragmentHandlerTest {

    private MarketDataFragmentHandler handler;

    @Mock
    private MarketDataBroadcaster broadcaster;

    @Mock
    private Header header;

    @BeforeEach
    void setUp() {
        handler = new MarketDataFragmentHandler();
        handler.broadcaster = broadcaster;
    }

    @Test
    void testProcessTradeMessage() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(256);
        UnsafeBuffer buffer = new UnsafeBuffer(byteBuffer);

        MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
        TradeEncoder tradeEncoder = new TradeEncoder();

        // Send 50 messages to ensure sampling triggers
        for (int i = 0; i < 50; i++) {
            tradeEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
            tradeEncoder.timestamp(System.currentTimeMillis());
            tradeEncoder.symbol("AAPL");
            tradeEncoder.price(15000);
            tradeEncoder.quantity(100);

            int encodedLength = headerEncoder.encodedLength() + tradeEncoder.encodedLength();
            handler.onFragment(buffer, 0, encodedLength, header);
        }

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(broadcaster, atLeastOnce()).broadcast(messageCaptor.capture());

        String broadcastedMessage = messageCaptor.getValue();
        assertThat(broadcastedMessage).contains("\"type\":\"trade\"");
        assertThat(broadcastedMessage).contains("\"symbol\":\"AAPL\"");
        assertThat(broadcastedMessage).contains("\"price\":1.5");
        assertThat(broadcastedMessage).contains("\"quantity\":100");
    }

    @Test
    void testProcessQuoteMessage() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(256);
        UnsafeBuffer buffer = new UnsafeBuffer(byteBuffer);

        MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
        QuoteEncoder quoteEncoder = new QuoteEncoder();

        // Send 50 messages to ensure sampling triggers
        for (int i = 0; i < 50; i++) {
            quoteEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
            quoteEncoder.timestamp(System.currentTimeMillis());
            quoteEncoder.symbol("MSFT");
            quoteEncoder.bidPrice(30000);
            quoteEncoder.askPrice(30100);
            quoteEncoder.bidSize(200);
            quoteEncoder.askSize(150);

            int encodedLength = headerEncoder.encodedLength() + quoteEncoder.encodedLength();
            handler.onFragment(buffer, 0, encodedLength, header);
        }

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(broadcaster, atLeastOnce()).broadcast(messageCaptor.capture());

        String broadcastedMessage = messageCaptor.getValue();
        assertThat(broadcastedMessage).contains("\"type\":\"quote\"");
        assertThat(broadcastedMessage).contains("\"symbol\":\"MSFT\"");
        assertThat(broadcastedMessage).contains("\"bid\":{\"price\":3.0");
        assertThat(broadcastedMessage).contains("\"ask\":{\"price\":3.01");
    }

    @Test
    void testProcessHeartbeatMessage() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(256);
        UnsafeBuffer buffer = new UnsafeBuffer(byteBuffer);

        MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
        HeartbeatEncoder heartbeatEncoder = new HeartbeatEncoder();

        heartbeatEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
        heartbeatEncoder.timestamp(System.currentTimeMillis());

        int encodedLength = headerEncoder.encodedLength() + heartbeatEncoder.encodedLength();

        handler.onFragment(buffer, 0, encodedLength, header);

        verify(broadcaster, never()).broadcast(any());
    }

    @Test
    void testMessageSampling() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(256);
        UnsafeBuffer buffer = new UnsafeBuffer(byteBuffer);

        MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
        TradeEncoder tradeEncoder = new TradeEncoder();

        for (int i = 0; i < 100; i++) {
            tradeEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
            tradeEncoder.timestamp(System.currentTimeMillis());
            tradeEncoder.symbol("TEST");
            tradeEncoder.price(10000 + i);
            tradeEncoder.quantity(100);

            int encodedLength = headerEncoder.encodedLength() + tradeEncoder.encodedLength();
            handler.onFragment(buffer, 0, encodedLength, header);
        }

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(broadcaster, times(2)).broadcast(messageCaptor.capture());
    }

    @Test
    void testInvalidTemplateId() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(256);
        UnsafeBuffer buffer = new UnsafeBuffer(byteBuffer);

        MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
        headerEncoder.wrap(buffer, 0);
        headerEncoder.blockLength(0);
        headerEncoder.templateId(9999);
        headerEncoder.schemaId(1);
        headerEncoder.version(0);

        int encodedLength = headerEncoder.encodedLength();

        handler.onFragment(buffer, 0, encodedLength, header);

        verify(broadcaster, never()).broadcast(any());
    }

    @Test
    void testPriceConversion() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(256);
        UnsafeBuffer buffer = new UnsafeBuffer(byteBuffer);

        MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
        TradeEncoder tradeEncoder = new TradeEncoder();

        // Send 50 messages to ensure sampling triggers
        for (int i = 0; i < 50; i++) {
            tradeEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
            tradeEncoder.timestamp(System.currentTimeMillis());
            tradeEncoder.symbol("XYZ");
            tradeEncoder.price(123456);
            tradeEncoder.quantity(1);

            int encodedLength = headerEncoder.encodedLength() + tradeEncoder.encodedLength();
            handler.onFragment(buffer, 0, encodedLength, header);
        }

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(broadcaster, atLeastOnce()).broadcast(messageCaptor.capture());

        String broadcastedMessage = messageCaptor.getValue();
        assertThat(broadcastedMessage).contains("\"price\":12.3456");
    }
}
