package fish.payara.trader.jfr;

import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.Category;
import jdk.jfr.StackTrace;

/**
 * Custom JFR events for Market Data Pipeline monitoring.
 *
 * <p>
 * These events provide domain-specific insights into the HFT trading simulation:
 * <ul>
 * <li>Message publishing rates and timing</li>
 * <li>SBE encoding/decoding performance</li>
 * <li>WebSocket broadcast latency</li>
 * <li>GC SLA violations</li>
 * <li>Aeron backpressure events</li>
 * </ul>
 *
 * <p>
 * <b>Usage:</b> Events are emitted only when enabled. Check {@link Event#isEnabled()} before committing to avoid unnecessary overhead.
 */
@Category("Market Data")
@Label("Market Data Processing")
public class MarketDataEvents {

    /**
     * Emitted when a trade message is published to Aeron or WebSocket. Stack trace disabled for minimal overhead on hot path.
     */
    @Name("trade.published")
    @Label("Trade Published")
    @StackTrace(false)
    public static class TradePublished extends Event {
        public String symbol;
        public long price;
        public int quantity;
        public String side;
    }

    /**
     * Emitted when a quote message is published.
     */
    @Name("quote.published")
    @Label("Quote Published")
    @StackTrace(false)
    public static class QuotePublished extends Event {
        public String symbol;
        public long bidPrice;
        public long askPrice;
        public int bidSize;
        public int askSize;
    }

    /**
     * Emitted when a market depth message is published.
     */
    @Name("marketdepth.published")
    @Label("Market Depth Published")
    @StackTrace(false)
    public static class MarketDepthPublished extends Event {
        public String symbol;
        public int depthLevels;
        public long sequenceNumber;
    }

    /**
     * Emitted after processing a batch of messages. Tracks throughput and latency for the ingestion pipeline.
     */
    @Name("message.batch.processed")
    @Label("Message Batch Processed")
    public static class BatchProcessed extends Event {
        public int messageCount;
        public long processingTimeNanos;
        public String source;
    }

    /**
     * Emitted when broadcasting to WebSocket clients. Tracks client load and message size distribution.
     */
    @Name("websocket.broadcast")
    @Label("WebSocket Broadcast")
    @StackTrace(false)
    public static class WebSocketBroadcast extends Event {
        public int clientCount;
        public int messageSizeBytes;
        public String messageType;
    }

    /**
     * Emitted after SBE encode operation completes. Measures binary encoding performance.
     */
    @Name("sbe.encode")
    @Label("SBE Encode Operation")
    @StackTrace(false)
    public static class SbeEncode extends Event {
        public String messageType;
        public int encodedBytes;
        public long encodeTimeNanos;
    }

    /**
     * Emitted after SBE decode operation completes. Measures binary decoding performance in the fragment handler.
     */
    @Name("sbe.decode")
    @Label("SBE Decode Operation")
    @StackTrace(false)
    public static class SbeDecode extends Event {
        public String messageType;
        public int decodedBytes;
        public long decodeTimeNanos;
    }

    /**
     * Emitted when a GC pause exceeds SLA threshold. Correlates GC behavior with application performance degradation.
     */
    @Name("gc.sla.violation")
    @Label("GC SLA Violation")
    public static class SlaViolation extends Event {
        public long pauseTimeMillis;
        public String threshold;
        public long violationsInWindow;
    }

    /**
     * Emitted when Aeron publication experiences backpressure. Indicates consumer cannot keep up with producer rate.
     */
    @Name("aeron.backpressure")
    @Label("Aeron Backpressure Event")
    public static class BackpressureEvent extends Event {
        public String messageType;
        public int consecutiveFailures;
        public String result;
    }

    /**
     * Emitted during burst mode activation. Tracks when the system enters high-allocation phases for GC stress testing.
     */
    @Name("burst.mode.activated")
    @Label("Burst Mode Activated")
    public static class BurstModeActivated extends Event {
        public int multiplier;
        public String reason;
        public long secondOfMinute;
    }
}
