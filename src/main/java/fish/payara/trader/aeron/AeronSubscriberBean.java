package fish.payara.trader.aeron;

import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.agrona.CloseHelper;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;

/**
 * Aeron Ingress Singleton Bean Launches an embedded MediaDriver and subscribes to market data
 * stream. Uses SBE decoders for zero-copy message processing. Runs in a dedicated thread to
 * continuously poll for messages. IMPORTANT: This must initialize BEFORE MarketDataPublisher
 */
@ApplicationScoped
public class AeronSubscriberBean {

  private static final Logger LOGGER = Logger.getLogger(AeronSubscriberBean.class.getName());

  private static final String CHANNEL = "aeron:ipc";
  private static final int STREAM_ID = 1001;
  private static final int FRAGMENT_LIMIT = 10;

  private MediaDriver mediaDriver;
  private Aeron aeron;
  private Subscription subscription;
  private volatile boolean running = false;
  private Future<?> pollingFuture;

  @Inject private MarketDataFragmentHandler fragmentHandler;

  @Resource private ManagedExecutorService managedExecutorService;

  void contextInitialized(@Observes @Initialized(ApplicationScoped.class) Object event) {
    managedExecutorService.submit(() -> init());
  }

  public void init() {
    LOGGER.info("Initializing Aeron Subscriber Bean...");

    try {
      LOGGER.info("Launching embedded MediaDriver...");
      mediaDriver =
          MediaDriver.launchEmbedded(
              new MediaDriver.Context()
                  .threadingMode(ThreadingMode.SHARED)
                  .dirDeleteOnStart(true)
                  .dirDeleteOnShutdown(true));

      LOGGER.info("MediaDriver launched at: " + mediaDriver.aeronDirectoryName());
      LOGGER.info("Connecting Aeron client...");
      aeron =
          Aeron.connect(
              new Aeron.Context()
                  .aeronDirectoryName(mediaDriver.aeronDirectoryName())
                  .errorHandler(this::onError)
                  .availableImageHandler(
                      image -> LOGGER.info("Available image: " + image.sourceIdentity()))
                  .unavailableImageHandler(
                      image -> LOGGER.info("Unavailable image: " + image.sourceIdentity())));
      LOGGER.info("Adding subscription on channel: " + CHANNEL + ", stream: " + STREAM_ID);
      subscription = aeron.addSubscription(CHANNEL, STREAM_ID);
      startPolling();
      LOGGER.info("Aeron Subscriber Bean initialized successfully");

    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, "Failed to initialize Aeron Subscriber Bean", e);
      cleanup();
      throw new RuntimeException("Failed to initialize Aeron", e);
    }
  }

  /** Start background task to continuously poll for messages */
  private void startPolling() {
    running = true;
    pollingFuture =
        managedExecutorService.submit(
            () -> {
              LOGGER.info("Aeron polling task started");

              final IdleStrategy idleStrategy =
                  new BackoffIdleStrategy(
                      100,
                      10,
                      TimeUnit.MICROSECONDS.toNanos(1),
                      TimeUnit.MICROSECONDS.toNanos(100));

              while (running && !Thread.currentThread().isInterrupted()) {
                try {
                  final int fragmentsRead = subscription.poll(fragmentHandler, FRAGMENT_LIMIT);

                  idleStrategy.idle(fragmentsRead);

                } catch (Exception e) {
                  LOGGER.log(Level.SEVERE, "Error polling subscription", e);
                }
              }

              LOGGER.info("Aeron polling task stopped");
            });
  }

  /** Error handler for Aeron */
  private void onError(Throwable throwable) {
    LOGGER.log(Level.SEVERE, "Aeron error occurred", throwable);
  }

  @PreDestroy
  public void shutdown() {
    LOGGER.info("Shutting down Aeron Subscriber Bean...");
    running = false;

    if (pollingFuture != null) {
      pollingFuture.cancel(true);
    }

    cleanup();
    LOGGER.info("Aeron Subscriber Bean shut down");
  }

  /** Clean up all Aeron resources */
  private void cleanup() {
    CloseHelper.quietClose(subscription);
    CloseHelper.quietClose(aeron);
    CloseHelper.quietClose(mediaDriver);
  }

  /** Get subscription statistics */
  public String getStatus() {
    if (subscription != null) {
      return String.format(
          "Channel: %s, Stream: %d, Images: %d, Running: %b",
          subscription.channel(), subscription.streamId(), subscription.imageCount(), running);
    }
    return "Not initialized";
  }

  /** Get the Aeron directory name for connecting publishers */
  public String getAeronDirectoryName() {
    if (mediaDriver != null) {
      return mediaDriver.aeronDirectoryName();
    }
    return null;
  }

  /** Check if the MediaDriver is ready */
  public boolean isReady() {
    return mediaDriver != null && aeron != null && subscription != null && running;
  }
}
