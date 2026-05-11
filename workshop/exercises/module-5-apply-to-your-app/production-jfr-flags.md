# Production-safe JFR flags

Reference settings for always-on JFR in production.

## Recommended flags

```bash
java \
  -XX:StartFlightRecording=name=production,settings=default,maxsize=1g,maxage=24h,dumponexit=true,filename=/var/log/myapp/jfr/production.jfr \
  -XX:FlightRecorderOptions=stackdepth=64 \
  -Xlog:jfr*=warning \
  -jar myapp.jar
```

### What each option does

|                    Option                    |                                                                        Why                                                                        |
|----------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| `settings=default`                           | The JDK-shipped profile at ~1% overhead. Workshop's `tradestream-workshop.jfc` is roughly 1-2%; do not use it for always-on production recording. |
| `maxsize=1g`                                 | Hard cap on disk usage. JFR keeps a circular buffer at this size.                                                                                 |
| `maxage=24h`                                 | Anything older than 24 hours is dropped. Sized to a typical incident-response window.                                                             |
| `dumponexit=true`                            | Writes a final dump if the JVM shuts down cleanly. Lost on `kill -9`; not lost on graceful shutdown.                                              |
| `filename=/var/log/myapp/jfr/production.jfr` | Predictable filename. Rotate via your existing log rotation if you want history beyond `maxage`.                                                  |
| `stackdepth=64`                              | Default is 64 frames. For most enterprise stacks this is enough; deep reactive pipelines may need 128.                                            |
| `-Xlog:jfr*=warning`                         | Suppresses verbose JFR logging on stdout.                                                                                                         |

## When to dump on demand

For incident response, dump the current recording without stopping JFR:

```bash
jcmd <pid> JFR.dump filename=/tmp/incident-$(date +%s).jfr
```

This grabs the current circular buffer (up to `maxsize`) without disrupting the running recording. Useful for capturing the state during a live incident.

## Custom event hygiene

For every custom event you ship to production:

1. **`@StackTrace(false)`** on hot events. Stack capture is the dominant cost.
2. **Primitive fields over Strings.** Strings are interned in the JFR pool; the lookup costs per emission.
3. **`event.isEnabled()` gate around field assignment.** Skip the work entirely when the recording is off or this type is filtered.

```java
TradePublished event = new TradePublished();
if (event.isEnabled()) {                    // cheap check
    event.symbol = symbol;                  // skipped when JFR is off
    event.price = price;
    event.quantity = qty;
    event.side = side.name();
    event.commit();
}
```

## What not to do

- Do not enable `jdk.ObjectAllocationInNewTLAB` with stack traces in production. Under any non-trivial allocation rate, recording size explodes.
- Do not enable `jdk.ExecutionSample` at a sampling period below 10 ms in production. The default 20 ms is enough for most diagnosis.
- Do not ship custom events with deeply-nested object graphs as fields. Flatten to primitives or short strings.

## Reading the dump after the incident

```bash
# Quick summary
jfr summary /tmp/incident-1234.jfr

# Print all GC events
jfr print --events jdk.GarbageCollection /tmp/incident-1234.jfr

# Custom events only
jfr print --events 'trade.*,gc.sla.violation,aeron.*' /tmp/incident-1234.jfr

# Open in JMC for visual analysis
jmc -open /tmp/incident-1234.jfr
```

