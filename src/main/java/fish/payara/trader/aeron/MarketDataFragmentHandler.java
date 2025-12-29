package fish.payara.trader.aeron;

import fish.payara.trader.sbe.*;
import fish.payara.trader.websocket.MarketDataBroadcaster;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.agrona.DirectBuffer;

/**
 * FragmentHandler that uses SBE Flyweights for zero-copy message decoding. This handler processes
 * Aeron fragments by: 1. Decoding the SBE message header to identify message type 2. Using the
 * appropriate SBE decoder (flyweight pattern) 3. Extracting data without object allocation 4.
 * Broadcasting as JSON (intentionally creating garbage for GC stress testing)
 */
@ApplicationScoped
public class MarketDataFragmentHandler implements FragmentHandler {

  private static final Logger LOGGER = Logger.getLogger(MarketDataFragmentHandler.class.getName());

  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  private final TradeDecoder tradeDecoder = new TradeDecoder();
  private final QuoteDecoder quoteDecoder = new QuoteDecoder();
  private final MarketDepthDecoder marketDepthDecoder = new MarketDepthDecoder();
  private final OrderAckDecoder orderAckDecoder = new OrderAckDecoder();
  private final HeartbeatDecoder heartbeatDecoder = new HeartbeatDecoder();

  private long messagesProcessed = 0;
  private long messagesBroadcast = 0;
  private long lastLogTime = System.currentTimeMillis();

  private static final int SAMPLE_RATE = 50;
  private long sampleCounter = 0;

  @Inject MarketDataBroadcaster broadcaster;

  private final byte[] symbolBuffer = new byte[128];
  private final StringBuilder sb = new StringBuilder(1024);

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

  /** Process Trade message using SBE decoder (zero-copy) */
  private void processTrade(
      DirectBuffer buffer, int offset, int blockLength, int version, boolean shouldBroadcast) {
    if (!shouldBroadcast) {
      return;
    }

    tradeDecoder.wrap(buffer, offset, blockLength, version);

    final long timestamp = tradeDecoder.timestamp();
    final long tradeId = tradeDecoder.tradeId();
    final long price = tradeDecoder.price();
    final long quantity = tradeDecoder.quantity();
    final Side side = tradeDecoder.side();

    final int symbolLength = tradeDecoder.symbolLength();
    tradeDecoder.getSymbol(symbolBuffer, 0, symbolLength);
    final String symbol = new String(symbolBuffer, 0, symbolLength);

    sb.setLength(0);
    sb.append("{\"type\":\"trade\",\"timestamp\":")
        .append(timestamp)
        .append(",\"tradeId\":")
        .append(tradeId)
        .append(",\"symbol\":\"")
        .append(symbol)
        .append("\"")
        .append(",\"price\":")
        .append(price / 10000.0)
        .append(",\"quantity\":")
        .append(quantity)
        .append(",\"side\":\"")
        .append(side)
        .append("\"}");

    broadcaster.broadcast(sb.toString());
  }

  private void processQuote(
      DirectBuffer buffer, int offset, int blockLength, int version, boolean shouldBroadcast) {
    if (!shouldBroadcast) {
      return;
    }

    quoteDecoder.wrap(buffer, offset, blockLength, version);

    final long timestamp = quoteDecoder.timestamp();
    final long bidPrice = quoteDecoder.bidPrice();
    final long bidSize = quoteDecoder.bidSize();
    final long askPrice = quoteDecoder.askPrice();
    final long askSize = quoteDecoder.askSize();

    final int symbolLength = quoteDecoder.symbolLength();
    quoteDecoder.getSymbol(symbolBuffer, 0, symbolLength);
    final String symbol = new String(symbolBuffer, 0, symbolLength);

    sb.setLength(0);
    sb.append("{\"type\":\"quote\",\"timestamp\":")
        .append(timestamp)
        .append(",\"symbol\":\"")
        .append(symbol)
        .append("\"")
        .append(",\"bid\":{\"price\":")
        .append(bidPrice / 10000.0)
        .append(",\"size\":")
        .append(bidSize)
        .append("}")
        .append(",\"ask\":{\"price\":")
        .append(askPrice / 10000.0)
        .append(",\"size\":")
        .append(askSize)
        .append("}}");

    broadcaster.broadcast(sb.toString());
  }

  private void processMarketDepth(
      DirectBuffer buffer, int offset, int blockLength, int version, boolean shouldBroadcast) {
    if (!shouldBroadcast) {
      return;
    }

    marketDepthDecoder.wrap(buffer, offset, blockLength, version);

    final long timestamp = marketDepthDecoder.timestamp();
    final long sequenceNumber = marketDepthDecoder.sequenceNumber();

    sb.setLength(0);
    sb.append("{\"type\":\"depth\",\"timestamp\":")
        .append(timestamp)
        .append(",\"sequence\":")
        .append(sequenceNumber)
        .append(",\"bids\":[");

    MarketDepthDecoder.BidsDecoder bids = marketDepthDecoder.bids();
    int bidCount = 0;
    while (bids.hasNext()) {
      bids.next();
      if (bidCount > 0) sb.append(",");
      sb.append("{\"price\":")
          .append(bids.price() / 10000.0)
          .append(",\"quantity\":")
          .append(bids.quantity())
          .append("}");
      bidCount++;
    }
    sb.append("],\"asks\":[");

    MarketDepthDecoder.AsksDecoder asks = marketDepthDecoder.asks();
    int askCount = 0;
    while (asks.hasNext()) {
      asks.next();
      if (askCount > 0) sb.append(",");
      sb.append("{\"price\":")
          .append(asks.price() / 10000.0)
          .append(",\"quantity\":")
          .append(asks.quantity())
          .append("}");
      askCount++;
    }
    sb.append("]");

    final int symbolLength = marketDepthDecoder.symbolLength();
    marketDepthDecoder.getSymbol(symbolBuffer, 0, symbolLength);
    final String symbol = new String(symbolBuffer, 0, symbolLength);

    sb.append(",\"symbol\":\"").append(symbol).append("\"}");

    broadcaster.broadcast(sb.toString());
  }

  private void processOrderAck(
      DirectBuffer buffer, int offset, int blockLength, int version, boolean shouldBroadcast) {
    if (!shouldBroadcast) {
      return;
    }

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
    orderAckDecoder.getSymbol(symbolBuffer, 0, symbolLength);
    final String symbol = new String(symbolBuffer, 0, symbolLength);

    sb.setLength(0);
    sb.append("{\"type\":\"orderAck\",\"timestamp\":")
        .append(timestamp)
        .append(",\"orderId\":")
        .append(orderId)
        .append(",\"clientOrderId\":")
        .append(clientOrderId)
        .append(",\"symbol\":\"")
        .append(symbol)
        .append("\"")
        .append(",\"side\":\"")
        .append(side)
        .append("\"")
        .append(",\"orderType\":\"")
        .append(orderType)
        .append("\"")
        .append(",\"price\":")
        .append(price / 10000.0)
        .append(",\"quantity\":")
        .append(quantity)
        .append(",\"execType\":\"")
        .append(execType)
        .append("\"")
        .append(",\"leavesQty\":")
        .append(leavesQty)
        .append(",\"cumQty\":")
        .append(cumQty)
        .append("}");

    broadcaster.broadcast(sb.toString());
  }

  private void processHeartbeat(DirectBuffer buffer, int offset, int blockLength, int version) {
    heartbeatDecoder.wrap(buffer, offset, blockLength, version);

    final long timestamp = heartbeatDecoder.timestamp();
    final long sequenceNumber = heartbeatDecoder.sequenceNumber();

    if (LOGGER.isLoggable(Level.FINE)) {
      LOGGER.fine("Heartbeat: timestamp=" + timestamp + ", seq=" + sequenceNumber);
    }
  }

  private void logStatistics() {
    long now = System.currentTimeMillis();
    if (now - lastLogTime > 5000) {
      double elapsedSeconds = (now - lastLogTime) / 1000.0;
      LOGGER.info(
          String.format(
              "FragmentHandler Stats - Processed: %,d (%.0f msg/sec) | Broadcast to UI: %,d (%.0f msg/sec) | Sample rate: 1 in %d",
              messagesProcessed,
              messagesProcessed / elapsedSeconds,
              messagesBroadcast,
              messagesBroadcast / elapsedSeconds,
              SAMPLE_RATE));
      lastLogTime = now;
      messagesProcessed = 0;
      messagesBroadcast = 0;
    }
  }
}
