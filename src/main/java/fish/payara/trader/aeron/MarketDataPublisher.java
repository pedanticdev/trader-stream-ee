package fish.payara.trader.aeron;

import java.util.concurrent.ThreadLocalRandom;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cp.IAtomicLong;
import fish.payara.trader.concurrency.VirtualThreadExecutor;
import fish.payara.trader.sbe.*;
import fish.payara.trader.websocket.MarketDataBroadcaster;
import io.aeron.Aeron;
import io.aeron.Publication;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.ejb.DependsOn;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.enterprise.concurrent.ManagedExecutorDefinition;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.agrona.concurrent.UnsafeBuffer;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.ByteBuffer;

import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Market Data Publisher Simulator
 * Generates synthetic market data and publishes via Aeron using SBE encoding.
 * This simulates a high-frequency data feed for testing the ingestion pipeline.
 * IMPORTANT: Depends on AeronSubscriberBean to initialize first (provides MediaDriver)
 */
@ApplicationScoped
public class MarketDataPublisher {

    private static final Logger LOGGER = Logger.getLogger(MarketDataPublisher.class.getName());

    private static final String CHANNEL = "aeron:ipc";
    private static final int STREAM_ID = 1001;
    private static final int BUFFER_SIZE = 4096;
    private static final int SAMPLE_RATE = 50; // Broadcast 1 in 50 messages to prevent flooding

    private static final String[] SYMBOLS = {"AAPL", "GOOGL", "MSFT", "AMZN", "TSLA", "NVDA", "META", "NFLX"};

    private Aeron aeron;
    private Publication publication;

    // SBE encoders (reusable flyweights)
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final TradeEncoder tradeEncoder = new TradeEncoder();
    private final QuoteEncoder quoteEncoder = new QuoteEncoder();
    private final MarketDepthEncoder marketDepthEncoder = new MarketDepthEncoder();
    private final HeartbeatEncoder heartbeatEncoder = new HeartbeatEncoder();

    // Buffer for encoding messages
    private final UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(BUFFER_SIZE));


    private final AtomicLong sequenceNumber = new AtomicLong(0);
    private final AtomicLong tradeIdGenerator = new AtomicLong(1000);

    private final AtomicLong messagesPublished = new AtomicLong(0);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private static final int MAX_CONSECUTIVE_FAILURES = 50;
    private long sampleCounter = 0;
    private volatile boolean initialized = false;
    private volatile boolean running = false;
    private boolean isDirectMode;
    private long lastWarningLogTime = 0;
    private static final long WARNING_LOG_INTERVAL_MS = 5000; // Log warnings at most once per 5 seconds
    
    private Future<?> publisherFuture;
    private Future<?> statsFuture;

    @Inject
    private AeronSubscriberBean subscriberBean;

    @Inject
    @ConfigProperty(name = "TRADER_INGESTION_MODE", defaultValue = "AERON")
    private String ingestionMode;

    @Inject
    @ConfigProperty(name = "ENABLE_PUBLISHER", defaultValue = "true")
    String enablePublisherEnv;

    @Inject
    private MarketDataBroadcaster broadcaster;

    @Inject
    private HazelcastInstance hazelcastInstance;

    @Inject
    @VirtualThreadExecutor
    private ManagedExecutorService managedExecutorService;

    // Cluster-wide message counter (shared across all instances)
    private IAtomicLong clusterMessageCounter;

    void contextInitialized(@Observes @Initialized(ApplicationScoped.class) Object event) {
//        managedExecutorService.submit(this::init);
        init();
    }

    public void init() {
        // Check if publisher should be enabled on this instance
        if (enablePublisherEnv != null && !"true".equalsIgnoreCase(enablePublisherEnv)) {
            LOGGER.info("Market Data Publisher DISABLED on this instance (ENABLE_PUBLISHER=" + enablePublisherEnv + ")");
            LOGGER.info("This instance will only consume messages from the cluster topic via Hazelcast");
            return;
        }

        LOGGER.info("Initializing Market Data Publisher (ENABLE_PUBLISHER=" + enablePublisherEnv + "). Mode: " + ingestionMode);

        if ("DIRECT".equalsIgnoreCase(ingestionMode)) {
            LOGGER.info("Running in DIRECT mode - Bypassing Aeron/SBE setup.");
            initialized = true;
            isDirectMode = true;

            // Initialize cluster-wide message counter
            if (hazelcastInstance != null) {
                try {
                    clusterMessageCounter = hazelcastInstance.getCPSubsystem().getAtomicLong("cluster-message-count");
                    LOGGER.info("Initialized cluster-wide message counter (DIRECT mode)");
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to initialize cluster message counter, using local counter only", e);
                }
            }

            startPublishing();
            return;
        }

        try {
            // Wait for AeronSubscriberBean to be ready (both observers fire at roughly same time)
            LOGGER.info("Waiting for AeronSubscriberBean to be ready...");
            int waitAttempts = 0;
            while (!subscriberBean.isReady() && waitAttempts < 60) {
                Thread.sleep(500);
                waitAttempts++;
            }

            if (!subscriberBean.isReady()) {
                LOGGER.severe("AeronSubscriberBean did not become ready in time");
                return;
            }

            String aeronDir = subscriberBean.getAeronDirectoryName();
            LOGGER.info("Connecting to embedded MediaDriver at: " + aeronDir);

            aeron = Aeron.connect(new Aeron.Context()
                .aeronDirectoryName(aeronDir)
                .errorHandler(throwable ->
                    LOGGER.log(Level.SEVERE, "Aeron publisher error", throwable))
            );

            LOGGER.info("Connected to Aeron. Adding publication...");

            publication = aeron.addPublication(CHANNEL, STREAM_ID);

            LOGGER.info("Waiting for subscriber to connect to publication...");
            int attempts = 0;
            while (!publication.isConnected() && attempts < 20) {
                Thread.sleep(500);
                attempts++;
            }

            if (publication.isConnected()) {
                LOGGER.info("Market Data Publisher initialized successfully");
                initialized = true;

                // Initialize cluster-wide message counter
                if (hazelcastInstance != null) {
                    try {
                        clusterMessageCounter = hazelcastInstance.getCPSubsystem().getAtomicLong("cluster-message-count");
                        LOGGER.info("Initialized cluster-wide message counter");
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Failed to initialize cluster message counter, using local counter only", e);
                    }
                }

                startPublishing();
            } else {
                LOGGER.warning("Publisher not connected after waiting");
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize publisher", e);
        }
    }

    /**
     * Start background thread to continuously publish market data at high throughput
     */
    private void startPublishing() {
        running = true;
        publisherFuture = managedExecutorService.submit(() -> {
            LOGGER.info("Market data publisher task started - targeting 50k-100k messages/sec");

            final int BURST_SIZE = 500;
            final long PARK_NANOS = 5_000; // 5 microseconds = ~100k messages/sec

            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    for (int i = 0; i < BURST_SIZE && running; i++) {
                        publishTrade();
                        publishQuote();
                        publishMarketDepth();

                        if (i % 100 == 0) {
                            publishHeartbeat();
                        }
                    }

                    LockSupport.parkNanos(PARK_NANOS);

                } catch (Throwable e) {
                    LOGGER.log(Level.SEVERE, "Critical error in publisher loop", e);
                    // Brief pause on error, then continue
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(100));
                }
            }

            LOGGER.info("Market data publisher task stopped");
        });

        statsFuture = managedExecutorService.submit(() -> {
            long lastCount = 0;
            long lastTime = System.currentTimeMillis();

            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(5000);

                    long currentCount = messagesPublished.get();
                    long currentTime = System.currentTimeMillis();
                    long messagesSinceLastLog = currentCount - lastCount;
                    double elapsedSeconds = (currentTime - lastTime) / 1000.0;
                    double messagesPerSecond = messagesSinceLastLog / elapsedSeconds;

                    LOGGER.info(String.format(
                        "Publisher Stats - Total: %,d | Last 5s: %,d (%.0f msg/sec)",
                        currentCount,
                        messagesSinceLastLog,
                        messagesPerSecond
                    ));

                    String statsJson = String.format("{\"type\":\"stats\",\"total\":%d,\"rate\":%.0f}", currentCount, messagesPerSecond);
                    broadcaster.broadcast(statsJson);

                    lastCount = currentCount;
                    lastTime = currentTime;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    /**
     * Publish a Trade message
     */
    private void publishTrade() {
        if (isDirectMode) {
            final ThreadLocalRandom currentRandom = ThreadLocalRandom.current(); // Use ThreadLocalRandom
            final String symbol = SYMBOLS[currentRandom.nextInt(SYMBOLS.length)];
            final double price = 100.0 + currentRandom.nextDouble() * 400.0;
            final int quantity = currentRandom.nextInt(1000) + 100;
            final String side = currentRandom.nextBoolean() ? "BUY" : "SELL";
            
            String json = String.format(
                "{\"type\":\"trade\",\"timestamp\":%d,\"tradeId\":%d,\"symbol\":\"%s\",\"price\":%.4f,\"quantity\":%d,\"side\":\"%s\"}",
                System.currentTimeMillis(),
                tradeIdGenerator.incrementAndGet(),
                symbol,
                price,
                quantity,
                side
            );
            if (++sampleCounter % SAMPLE_RATE == 0) {
                broadcaster.broadcastWithArtificialLoad(json);
            }
            messagesPublished.incrementAndGet();
            // Increment cluster-wide counter
            if (clusterMessageCounter != null) {
                try {
                    clusterMessageCounter.incrementAndGet();
                } catch (Exception e) {
                    // Ignore cluster counter errors, not critical
                }
            }
            return;
        }

        int bufferOffset = 0;
        final ThreadLocalRandom currentRandom = ThreadLocalRandom.current();
        final String symbol = SYMBOLS[currentRandom.nextInt(SYMBOLS.length)];

        headerEncoder.wrap(buffer, bufferOffset)
            .blockLength(tradeEncoder.sbeBlockLength())
            .templateId(tradeEncoder.sbeTemplateId())
            .schemaId(tradeEncoder.sbeSchemaId())
            .version(tradeEncoder.sbeSchemaVersion());

        bufferOffset += headerEncoder.encodedLength();

        tradeEncoder.wrap(buffer, bufferOffset)
            .timestamp(System.currentTimeMillis())
            .tradeId(tradeIdGenerator.incrementAndGet())
            .price((long) ((100.0 + currentRandom.nextDouble() * 400.0) * 10000))  // $100-$500
            .quantity(currentRandom.nextInt(1000) + 100)
            .side(currentRandom.nextBoolean() ? Side.BUY : Side.SELL)
            .symbol(symbol);

        final int length = headerEncoder.encodedLength() + tradeEncoder.encodedLength();
        offer(buffer, 0, length, "Trade");
    }

    /**
     * Publish a Quote message
     */
    private void publishQuote() {
        if (isDirectMode) {
            final ThreadLocalRandom currentRandom = ThreadLocalRandom.current();
            final String symbol = SYMBOLS[currentRandom.nextInt(SYMBOLS.length)];
            final double basePrice = 100.0 + currentRandom.nextDouble() * 400.0;
            final double bidPrice = basePrice - 0.01;
            final double askPrice = basePrice + 0.01;
            final int bidSize = currentRandom.nextInt(10000) + 100;
            final int askSize = currentRandom.nextInt(10000) + 100;

            String json = String.format(
                "{\"type\":\"quote\",\"timestamp\":%d,\"symbol\":\"%s\",\"bid\":{\"price\":%.4f,\"size\":%d},\"ask\":{\"price\":%.4f,\"size\":%d}}",
                System.currentTimeMillis(), symbol,
                bidPrice, bidSize,
                askPrice, askSize
            );
            if (++sampleCounter % SAMPLE_RATE == 0) {
                broadcaster.broadcastWithArtificialLoad(json);
            }
            messagesPublished.incrementAndGet();
            // Increment cluster-wide counter
            if (clusterMessageCounter != null) {
                try {
                    clusterMessageCounter.incrementAndGet();
                } catch (Exception e) {
                    // Ignore cluster counter errors, not critical
                }
            }
            return;
        }

        int bufferOffset = 0;
        final ThreadLocalRandom currentRandom = ThreadLocalRandom.current();
        final String symbol = SYMBOLS[currentRandom.nextInt(SYMBOLS.length)];
        final double basePrice = 100.0 + currentRandom.nextDouble() * 400.0;

        headerEncoder.wrap(buffer, bufferOffset)
            .blockLength(quoteEncoder.sbeBlockLength())
            .templateId(quoteEncoder.sbeTemplateId())
            .schemaId(quoteEncoder.sbeSchemaId())
            .version(quoteEncoder.sbeSchemaVersion());

        bufferOffset += headerEncoder.encodedLength();

        quoteEncoder.wrap(buffer, bufferOffset)
            .timestamp(System.currentTimeMillis())
            .bidPrice((long) ((basePrice - 0.01) * 10000))
            .bidSize(currentRandom.nextInt(10000) + 100)
            .askPrice((long) ((basePrice + 0.01) * 10000))
            .askSize(currentRandom.nextInt(10000) + 100)
            .symbol(symbol);

        final int length = headerEncoder.encodedLength() + quoteEncoder.encodedLength();
        offer(buffer, 0, length, "Quote");
    }

    /**
     * Publish a MarketDepth message
     */
    private void publishMarketDepth() {
        if (isDirectMode) {
            final ThreadLocalRandom currentRandom = ThreadLocalRandom.current();
            final String symbol = SYMBOLS[currentRandom.nextInt(SYMBOLS.length)];
            final double basePrice = 100.0 + currentRandom.nextDouble() * 400.0;
            final long timestamp = System.currentTimeMillis();
            final long seq = sequenceNumber.incrementAndGet();

            StringBuilder bidsJson = new StringBuilder("[");
            for (int i = 0; i < 5; i++) {
                if (i > 0) bidsJson.append(",");
                bidsJson.append(String.format("{\"price\":%.4f,\"quantity\":%d}",
                    basePrice - (i + 1) * 0.01, currentRandom.nextInt(5000) + 100));
            }
            bidsJson.append("]");

            StringBuilder asksJson = new StringBuilder("[");
            for (int i = 0; i < 5; i++) {
                if (i > 0) asksJson.append(",");
                asksJson.append(String.format("{\"price\":%.4f,\"quantity\":%d}",
                    basePrice + (i + 1) * 0.01, currentRandom.nextInt(5000) + 100));
            }
            asksJson.append("]");

            String json = String.format(
                "{\"type\":\"depth\",\"timestamp\":%d,\"symbol\":\"%s\",\"sequence\":%d,\"bids\":%s,\"asks\":%s}",
                timestamp, symbol, seq, bidsJson, asksJson
            );
            if (++sampleCounter % SAMPLE_RATE == 0) {
                broadcaster.broadcastWithArtificialLoad(json);
            }
            messagesPublished.incrementAndGet();
            // Increment cluster-wide counter
            if (clusterMessageCounter != null) {
                try {
                    clusterMessageCounter.incrementAndGet();
                } catch (Exception e) {
                    // Ignore cluster counter errors, not critical
                }
            }
            return;
        }

        int bufferOffset = 0;
        final ThreadLocalRandom currentRandom = ThreadLocalRandom.current();
        final String symbol = SYMBOLS[currentRandom.nextInt(SYMBOLS.length)];
        final double basePrice = 100.0 + currentRandom.nextDouble() * 400.0;

        headerEncoder.wrap(buffer, bufferOffset)
            .blockLength(marketDepthEncoder.sbeBlockLength())
            .templateId(marketDepthEncoder.sbeTemplateId())
            .schemaId(marketDepthEncoder.sbeSchemaId())
            .version(marketDepthEncoder.sbeSchemaVersion());

        bufferOffset += headerEncoder.encodedLength();

        marketDepthEncoder.wrap(buffer, bufferOffset)
            .timestamp(System.currentTimeMillis())
            .sequenceNumber(sequenceNumber.incrementAndGet());

        MarketDepthEncoder.BidsEncoder bidsEncoder = marketDepthEncoder.bidsCount(5);
        for (int i = 0; i < 5; i++) {
            bidsEncoder.next()
                .price((long) ((basePrice - (i + 1) * 0.01) * 10000))
                .quantity(currentRandom.nextInt(5000) + 100);
        }

        MarketDepthEncoder.AsksEncoder asksEncoder = marketDepthEncoder.asksCount(5);
        for (int i = 0; i < 5; i++) {
            asksEncoder.next()
                .price((long) ((basePrice + (i + 1) * 0.01) * 10000))
                .quantity(currentRandom.nextInt(5000) + 100);
        }

        marketDepthEncoder.symbol(symbol);

        final int length = headerEncoder.encodedLength() + marketDepthEncoder.encodedLength();
        offer(buffer, 0, length, "MarketDepth");
    }

    /**
     * Publish a Heartbeat message
     */
    private void publishHeartbeat() {
        if (isDirectMode) {
            // In DIRECT mode, we just increment counter but don't broadcast heartbeats to UI
            // as they are mainly for system health checks in the Aeron log
            messagesPublished.incrementAndGet();
            return;
        }

        int bufferOffset = 0;

        headerEncoder.wrap(buffer, bufferOffset)
            .blockLength(heartbeatEncoder.sbeBlockLength())
            .templateId(heartbeatEncoder.sbeTemplateId())
            .schemaId(heartbeatEncoder.sbeSchemaId())
            .version(heartbeatEncoder.sbeSchemaVersion());

        bufferOffset += headerEncoder.encodedLength();

        heartbeatEncoder.wrap(buffer, bufferOffset)
            .timestamp(System.currentTimeMillis())
            .sequenceNumber(sequenceNumber.get());

        final int length = headerEncoder.encodedLength() + heartbeatEncoder.encodedLength();
        offer(buffer, 0, length, "Heartbeat");
    }

    /**
     * Offer buffer to Aeron publication with retry logic
     */
    private void offer(UnsafeBuffer buffer, int offset, int length, String messageType) {
        long result;
        int retries = 3;

        while (retries > 0) {
            result = publication.offer(buffer, offset, length);

            if (result > 0) {
                messagesPublished.incrementAndGet();
                // Increment cluster-wide counter
                if (clusterMessageCounter != null) {
                    try {
                        clusterMessageCounter.incrementAndGet();
                    } catch (Exception e) {
                        // Ignore cluster counter errors, not critical
                    }
                }
                consecutiveFailures.set(0);
                return;
            } else if (result == Publication.BACK_PRESSURED) {
                // Back pressure is normal at high throughput, don't log
                retries--;
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            } else if (result == Publication.NOT_CONNECTED) {
                logWarningRateLimited("Publication not connected");
                handlePublishFailure(messageType);
                return;
            } else {
                logWarningRateLimited("Offer failed for " + messageType + ": " + result);
                handlePublishFailure(messageType);
                return;
            }
        }

        logWarningRateLimited("Failed to publish " + messageType + " after retries");
        handlePublishFailure(messageType);
    }

    private void logWarningRateLimited(String message) {
        long now = System.currentTimeMillis();
        if (now - lastWarningLogTime >= WARNING_LOG_INTERVAL_MS) {
            LOGGER.warning(message);
            lastWarningLogTime = now;
        }
    }

    private void handlePublishFailure(String messageType) {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= MAX_CONSECUTIVE_FAILURES) {
            LOGGER.severe(String.format(
                "Circuit breaker triggered: %d consecutive publish failures (last attempted: %s). Stopping message generation.",
                failures, messageType
            ));
            running = false;
        }
    }

    @PreDestroy
    public void shutdown() {
        LOGGER.info("Shutting down Market Data Publisher...");

        running = false;
        
        if (publisherFuture != null) {
            publisherFuture.cancel(true);
        }
        
        if (statsFuture != null) {
            statsFuture.cancel(true);
        }

        if (publication != null) {
            publication.close();
        }
        if (aeron != null) {
            aeron.close();
        }

        LOGGER.info("Market Data Publisher shut down. Total messages published: " + messagesPublished.get());
    }

    public long getMessagesPublished() {
        return messagesPublished.get();
    }

    public long getClusterMessagesPublished() {
        if (clusterMessageCounter != null) {
            try {
                return clusterMessageCounter.get();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to read cluster message counter", e);
            }
        }
        return 0;
    }
}
