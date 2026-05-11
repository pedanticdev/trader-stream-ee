# Module 3 exercise: track burst patterns with a custom JFR event

## Problem

The publisher emits a `burst.mode.activated` event when the system *deliberately* enters burst mode (the `MARKET_OPEN_SPIKE` preset, for example). What it does *not* do is detect spontaneous rate spikes - moments where the publisher exceeds its rolling average for reasons we did not plan for.

Add a custom `BurstPatternEvent` that fires whenever the per-second publish rate exceeds the 30-second moving average by 5x or more. Then build a JMC dashboard that overlays your event with `gc.sla.violation` and `aeron.backpressure`.

## What you will produce

1. `src/main/java/fish/payara/trader/jfr/BurstPatternEvent.java` - the event class.
2. A two-line patch in `MarketDataPublisher.detectBurstMode()` (or a new helper) that emits the event.
3. A custom JMC dashboard imported from `jmc-dashboard.xml`.

## Starter code

`BurstPatternEvent.starter.java` in this directory is your template. It has the imports, the class skeleton, and TODOs for the parts you need to fill in.

Once you are happy with your version, run:

```bash
./workshop/scripts/install-exercise.sh module-3-burst-event
```

This copies your file to `src/main/java/fish/payara/trader/jfr/BurstPatternEvent.java` (it refuses to overwrite an existing file there, so you can iterate safely).

## Build and redeploy

The container builds with Maven inside the Docker image, so a quick rebuild loop:

```bash
docker compose -f docker-compose-c4.yml build trader-stream-c4-1
docker compose -f docker-compose-c4.yml up -d --no-deps trader-stream-c4-1
```

This rolls only one instance, so the cluster stays up.

## Verify the event fires

```bash
# Start a fresh recording
curl -X POST 'http://localhost:8081/trader-stream-ee/api/jfr/recording/start?name=burst-test&durationSeconds=120&settings=tradestream-workshop'

# Trigger MARKET_OPEN_SPIKE (a multi-step preset that includes a 5x burst phase)
curl -X POST 'http://localhost:8081/trader-stream-ee/api/demo/presets/MARKET_OPEN_SPIKE/execute'

# Wait for the recording to finish, then open it
ls -lh monitoring/recordings/c4-1/
jmc -open monitoring/recordings/c4-1/burst-test-*.jfr
```

In JMC: `Event Browser → All → burst.pattern.detected`. If your event is there with non-zero count during the spike phase, you have it.

## Build the dashboard

Import `jmc-dashboard.xml`:

1. In JMC, with the recording open, choose `Window → Show View → Other → Mission Control → Custom Dashboard`.
2. Right-click the dashboard area → `Import...` and select `jmc-dashboard.xml`.
3. The dashboard shows three overlaid timelines: your `burst.pattern.detected`, `gc.sla.violation`, and `aeron.backpressure`.

## Solution

`BurstPatternEvent.solution.java` has a working reference implementation. Try the starter first; consult the solution only when stuck. The two are not byte-identical; there is more than one reasonable design choice.

## Discussion prompts

1. Should `BurstPatternEvent` have `@StackTrace(true)`? Why or why not?
2. What is the minimum sampling rate at which detecting a 5x spike becomes statistically meaningful?
3. If you wanted this event to fire only when GC pauses are also rising, how would you correlate the two in code, and what would you avoid?

