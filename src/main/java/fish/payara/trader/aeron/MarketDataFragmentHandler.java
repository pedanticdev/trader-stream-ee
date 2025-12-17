package fish.payara.trader.aeron;

import fish.payara.trader.sbe.*;
import fish.payara.trader.websocket.MarketDataBroadcaster;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.agrona.DirectBuffer;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * FragmentHandler that uses SBE Flyweights for zero-copy message decoding.
 * This handler processes Aeron fragments by:
 * 1. Decoding the SBE message header to identify message type
 * 2. Using the appropriate SBE decoder (flyweight pattern)
 * 3. Extracting data without object allocation
 * 4. Broadcasting as JSON (intentionally creating garbage for GC stress testing)
 */
@ApplicationScoped
public class MarketDataFragmentHandler implements FragmentHandler {

    private static final Logger LOGGER = Logger.getLogger(MarketDataFragmentHandler.class.getName());

    // SBE Message Header Decoder (reusable flyweight)
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

    // SBE Message Decoders (reusable flyweights)
    private final TradeDecoder tradeDecoder = new TradeDecoder();
    private final QuoteDecoder quoteDecoder = new QuoteDecoder();
    private final MarketDepthDecoder marketDepthDecoder = new MarketDepthDecoder();
    private final OrderAckDecoder orderAckDecoder = new OrderAckDecoder();
    private final HeartbeatDecoder heartbeatDecoder = new HeartbeatDecoder();

    // Statistics
    private long messagesProcessed = 0;
    private long messagesBroadcast = 0;
    private long lastLogTime = System.currentTimeMillis();

    // Sampling: Only broadcast 1 in N messages to avoid overwhelming browser
    private static final int SAMPLE_RATE = 50;
    private long sampleCounter = 0;

    @Inject
    private MarketDataBroadcaster broadcaster;

    @Override
    public void onFragment(DirectBuffer buffer, int offset, int length, Header header) {
        try {
            headerDecoder.wrap(buffer, offset);

            final int templateId = headerDecoder.templateId();
            final int actingBlockLength = headerDecoder.blockLength();
            final int actingVersion = headerDecoder.version();

            offset += headerDecoder.encodedLength();

            sampleCounter++;
            final boolean shouldBroadcast = (sampleCounter % SAMPLE_RATE == 0);

            switch (templateId) {
                case TradeDecoder.TEMPLATE_ID:
                    processTrade(buffer, offset, actingBlockLength, actingVersion, shouldBroadcast);
                    break;

                case QuoteDecoder.TEMPLATE_ID:
                    processQuote(buffer, offset, actingBlockLength, actingVersion, shouldBroadcast);
                    break;

                case MarketDepthDecoder.TEMPLATE_ID:
                    processMarketDepth(buffer, offset, actingBlockLength, actingVersion, shouldBroadcast);
                    break;

                case OrderAckDecoder.TEMPLATE_ID:
                    processOrderAck(buffer, offset, actingBlockLength, actingVersion, shouldBroadcast);
                    break;

                case HeartbeatDecoder.TEMPLATE_ID:
                    processHeartbeat(buffer, offset, actingBlockLength, actingVersion);
                    break;

                default:
                    LOGGER.warning("Unknown message template ID: " + templateId);
            }

            messagesProcessed++;
            if (shouldBroadcast) {
                messagesBroadcast++;
            }
            logStatistics();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error processing fragment", e);
        }
    }

    /**
     * Process Trade message using SBE decoder (zero-copy)
     */
    private void processTrade(DirectBuffer buffer, int offset, int blockLength, int version, boolean shouldBroadcast) {
        tradeDecoder.wrap(buffer, offset, blockLength, version);

        // Extract fields from SBE decoder
        final long timestamp = tradeDecoder.timestamp();
        final long tradeId = tradeDecoder.tradeId();
        final long price = tradeDecoder.price();
        final long quantity = tradeDecoder.quantity();
        final Side side = tradeDecoder.side();

        // Extract variable-length symbol string
        final int symbolLength = tradeDecoder.symbolLength();
        final byte[] symbolBytes = new byte[symbolLength];
        tradeDecoder.getSymbol(symbolBytes, 0, symbolLength);
        final String symbol = new String(symbolBytes);

        // Only broadcast sampled messages to avoid overwhelming browser
        if (shouldBroadcast) {
            // Convert to JSON (intentionally creating garbage for GC testing)
            String json = String.format(
                "{\"type\":\"trade\",\"timestamp\":%d,\"tradeId\":%d,\"symbol\":\"%s\"," +
                "\"price\":%.4f,\"quantity\":%d,\"side\":\"%s\"}",
                timestamp, tradeId, symbol,
                price / 10000.0, quantity, side
            );

            broadcaster.broadcast(json);
        }
    }

    /**
     * Process Quote message using SBE decoder (zero-copy)
     */
    private void processQuote(DirectBuffer buffer, int offset, int blockLength, int version, boolean shouldBroadcast) {
        quoteDecoder.wrap(buffer, offset, blockLength, version);

        final long timestamp = quoteDecoder.timestamp();
        final long bidPrice = quoteDecoder.bidPrice();
        final long bidSize = quoteDecoder.bidSize();
        final long askPrice = quoteDecoder.askPrice();
        final long askSize = quoteDecoder.askSize();

        final int symbolLength = quoteDecoder.symbolLength();
        final byte[] symbolBytes = new byte[symbolLength];
        quoteDecoder.getSymbol(symbolBytes, 0, symbolLength);
        final String symbol = new String(symbolBytes);

        // Only broadcast sampled messages
        if (shouldBroadcast) {
            String json = String.format(
                "{\"type\":\"quote\",\"timestamp\":%d,\"symbol\":\"%s\"," +
                "\"bid\":{\"price\":%.4f,\"size\":%d},\"ask\":{\"price\":%.4f,\"size\":%d}}",
                timestamp, symbol,
                bidPrice / 10000.0, bidSize,
                askPrice / 10000.0, askSize
            );

            broadcaster.broadcast(json);
        }
    }

    /**
     * Process MarketDepth message with repeating groups (zero-copy)
     */
    private void processMarketDepth(DirectBuffer buffer, int offset, int blockLength, int version, boolean shouldBroadcast) {
        marketDepthDecoder.wrap(buffer, offset, blockLength, version);

        final long timestamp = marketDepthDecoder.timestamp();
        final long sequenceNumber = marketDepthDecoder.sequenceNumber();

        // Only broadcast sampled messages (but always decode for GC stress)
        if (shouldBroadcast) {
            // Build JSON for bids
            StringBuilder bidsJson = new StringBuilder("[");
            MarketDepthDecoder.BidsDecoder bids = marketDepthDecoder.bids();
            int bidCount = 0;
            while (bids.hasNext()) {
                bids.next();
                if (bidCount > 0) bidsJson.append(",");
                bidsJson.append(String.format(
                    "{\"price\":%.4f,\"quantity\":%d}",
                    bids.price() / 10000.0, bids.quantity()
                ));
                bidCount++;
            }
            bidsJson.append("]");

            // Build JSON for asks
            StringBuilder asksJson = new StringBuilder("[");
            MarketDepthDecoder.AsksDecoder asks = marketDepthDecoder.asks();
            int askCount = 0;
            while (asks.hasNext()) {
                asks.next();
                if (askCount > 0) asksJson.append(",");
                asksJson.append(String.format(
                    "{\"price\":%.4f,\"quantity\":%d}",
                    asks.price() / 10000.0, asks.quantity()
                ));
                askCount++;
            }
            asksJson.append("]");

            final int symbolLength = marketDepthDecoder.symbolLength();
            final byte[] symbolBytes = new byte[symbolLength];
            marketDepthDecoder.getSymbol(symbolBytes, 0, symbolLength);
            final String symbol = new String(symbolBytes);

            String json = String.format(
                "{\"type\":\"depth\",\"timestamp\":%d,\"symbol\":\"%s\",\"sequence\":%d," +
                "\"bids\":%s,\"asks\":%s}",
                timestamp, symbol, sequenceNumber, bidsJson, asksJson
            );

            broadcaster.broadcast(json);
        }
    }

    /**
     * Process OrderAck message
     */
    private void processOrderAck(DirectBuffer buffer, int offset, int blockLength, int version, boolean shouldBroadcast) {
        orderAckDecoder.wrap(buffer, offset, blockLength, version);

        final long timestamp = orderAckDecoder.timestamp();
        final long orderId = orderAckDecoder.orderId();
        final long clientOrderId = orderAckDecoder.clientOrderId();
        final Side side = orderAckDecoder.side();
        final OrderType orderType = orderAckDecoder.orderType();
        final long price = orderAckDecoder.price();
        final long quantity = orderAckDecoder.quantity();
        final ExecType execType = orderAckDecoder.execType();
        final long leavesQty = orderAckDecoder.leavesQty();
        final long cumQty = orderAckDecoder.cumQty();

        final int symbolLength = orderAckDecoder.symbolLength();
        final byte[] symbolBytes = new byte[symbolLength];
        orderAckDecoder.getSymbol(symbolBytes, 0, symbolLength);
        final String symbol = new String(symbolBytes);

        // Only broadcast sampled messages
        if (shouldBroadcast) {
            String json = String.format(
                "{\"type\":\"orderAck\",\"timestamp\":%d,\"orderId\":%d,\"clientOrderId\":%d," +
                "\"symbol\":\"%s\",\"side\":\"%s\",\"orderType\":\"%s\",\"price\":%.4f," +
                "\"quantity\":%d,\"execType\":\"%s\",\"leavesQty\":%d,\"cumQty\":%d}",
                timestamp, orderId, clientOrderId, symbol, side, orderType,
                price / 10000.0, quantity, execType, leavesQty, cumQty
            );

            broadcaster.broadcast(json);
        }
    }

    /**
     * Process Heartbeat message
     */
    private void processHeartbeat(DirectBuffer buffer, int offset, int blockLength, int version) {
        heartbeatDecoder.wrap(buffer, offset, blockLength, version);

        final long timestamp = heartbeatDecoder.timestamp();
        final long sequenceNumber = heartbeatDecoder.sequenceNumber();

        // Log heartbeats but don't broadcast them
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Heartbeat: timestamp=" + timestamp + ", seq=" + sequenceNumber);
        }
    }

    /**
     * Log statistics periodically
     */
    private void logStatistics() {
        long now = System.currentTimeMillis();
        if (now - lastLogTime > 5000) {  // Log every 5 seconds
            double elapsedSeconds = (now - lastLogTime) / 1000.0;
            LOGGER.info(String.format(
                "FragmentHandler Stats - Processed: %,d (%.0f msg/sec) | Broadcast to UI: %,d (%.0f msg/sec) | Sample rate: 1 in %d",
                messagesProcessed,
                messagesProcessed / elapsedSeconds,
                messagesBroadcast,
                messagesBroadcast / elapsedSeconds,
                SAMPLE_RATE
            ));
            lastLogTime = now;
            messagesProcessed = 0;
            messagesBroadcast = 0;
        }
    }
}
