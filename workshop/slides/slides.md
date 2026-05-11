---
marp: true
theme: default
paginate: true
backgroundColor: "#0c0e14"
color: "#e6e6e6"
header: "Java Flight Recorder for Low-Latency Systems"
footer: "JNation Workshop · TradeStreamEE"
style: |
  section {
    font-family: 'IBM Plex Sans', 'Source Sans 3', sans-serif;
    font-size: 28px;
  }
  h1, h2, h3 {
    font-family: 'Playfair Display', 'Fraunces', serif;
    color: #f5f5f5;
    font-weight: 900;
  }
  h1 { font-size: 56px; letter-spacing: -1px; }
  h2 { font-size: 40px; }
  h3 { font-size: 32px; color: #c4a574; }
  code, pre {
    font-family: 'JetBrains Mono', 'Fira Code', monospace;
    font-size: 22px;
  }
  pre {
    background: #1a1d27;
    border-left: 3px solid #c4a574;
    padding: 16px;
  }
  table { font-size: 22px; }
  th { color: #c4a574; }
  blockquote {
    border-left: 4px solid #c4a574;
    color: #b8b8b8;
    font-style: italic;
  }
  .accent { color: #c4a574; font-weight: 900; }
  .muted { color: #888; }
---

<!-- _class: lead -->

# Java Flight Recorder for Low-Latency Systems

## A Hands-On Workshop

JNation · 3 hours 30 minutes

<span class="muted">Bring a laptop. We will read recordings together.</span>

---

## Today's question

> Your application is fast. Until it isn't.
> A GC pause of 180 ms in the middle of a latency-sensitive operation.
> You did not change the code. The heap did not grow.
> **What happened?**

This workshop builds the answer from JFR data, not from intuition.

---

## What you leave with

- The TradeStreamEE source and Docker setup
- Ten pre-recorded `.jfr` files across five scenarios and two collectors
- A printable JFR analysis checklist
- Three reusable JFR event templates
- A mental model you can carry to any Java application

---

## Five modules

| # |            Module             |  Time  |
|---|-------------------------------|--------|
| 1 | Setup and warm-up             | 30 min |
| 2 | Reading JFR recordings        | 60 min |
| 3 | Custom JFR events             | 45 min |
| 4 | Collector comparison          | 45 min |
| 5 | Applying to your applications | 30 min |

---

## The system you will be running

```
MarketDataPublisher (virtual thread)
       │ SBE binary encode (off-heap)
       ▼
   Aeron IPC (shared memory)
       │
       ▼
MarketDataFragmentHandler (zero-copy decode)
       ├──> MatchingEngine
       ├──> BarAggregator + ta4j
       └──> MarketDataBroadcaster ─> Hazelcast ─> WebSocket
```

Throughput target: 100K messages per second.

---

## Two clusters, one variable

```
:8080  ┌─ C4 cluster ──┐         :9080  ┌─ G1 cluster ──┐
       │ c4-1 (Prime)  │                │ g1-1 (Temurin)│
       │ c4-2          │                │ g1-2          │
       │ c4-3          │                │ g1-3          │
       └───────────────┘                └───────────────┘

       Identical heap (-Xms4g -Xmx4g, AlwaysPreTouch, THP)
       Identical workload
       Only the collector differs.
```

---

<!-- _class: lead -->

# Module 1

## Setup and warm-up

30 minutes

---

## Bring up the clusters

```bash
git clone <repo>
cd trader-stream-ee
./workshop/scripts/verify-setup.sh
./start-comparison.sh all
```

Then:

```bash
curl -s http://localhost:8080/trader-stream-ee/api/health/check | jq
curl -s http://localhost:9080/trader-stream-ee/api/health/check | jq
```

Both must return `"status": "healthy"`.

---

## The JFR REST API

The application exposes JFR control over HTTP. No `jcmd`, no docker exec.

```bash
# Start a 60-second recording
curl -X POST 'http://localhost:8081/trader-stream-ee/api/jfr/recording/start?name=baseline&durationSeconds=60&settings=tradestream-workshop'

# List captured files (auto-dumped on duration expiry)
curl http://localhost:8081/trader-stream-ee/api/jfr/files | jq

# Recordings persist to host via Docker bind mount:
ls -lh monitoring/recordings/c4-1/
```

---

## Open in JMC

```bash
jmc -open monitoring/recordings/c4-1/baseline-*.jfr
```

Three views to read first:

1. `General → Garbage Collections`
2. `Memory → Allocation`
3. `Threads → Hot Methods`

---

## Exercise 1.1

**Find the longest GC pause in your baseline recording.**

Record:

- Duration (ms)
- Collector name
- GC cause

Hint: in JMC, sort the Duration column descending. The first row is your answer.

> Full hints and expected output: `workshop/exercises/module-1-find-longest-pause/`

---

## Module 1 checkpoint

Compare across the room.

- C4 baseline: pauses under 1 ms
- G1 baseline: occasional young-gen pauses around 5-15 ms

The interesting question: **why is there any difference at idle?**

---

<!-- _class: lead -->

# Module 2

## Reading JFR recordings

60 minutes

---

## GC events: read these three first

|          Event           |                  What it tells you                  |
|--------------------------|-----------------------------------------------------|
| `jdk.GarbageCollection`  | Wall-clock pause time, cause                        |
| `jdk.GCPhasePause`       | Where the pause time actually went                  |
| `jdk.GCPhasePauseLevel1` | Per-subphase: Update RS, Evacuate, Object Copy, ... |

For G1: also `jdk.G1HeapSummary`, `jdk.EvacuationFailed`, `jdk.PromotionFailed`.

---

## Average lies. Distributions don't.

| Quantile  |        What it tells you        |
|-----------|---------------------------------|
| P50       | "Typical pause"                 |
| P95       | "Routine bad pause"             |
| **P99**   | **"What 1 in 100 GCs cost"**    |
| **P99.9** | **"Where incidents come from"** |
| Max       | "Worst single pause"            |

Sample size matters. P99 from 10 collections is meaningless.

---

## Young, mixed, full

|           G1 name           |      What it was doing       |         Cost         |
|-----------------------------|------------------------------|----------------------|
| `Young Generation` (Normal) | Young-only collection        | Cheap                |
| `Young Generation` (Mixed)  | Young + some old             | Expensive            |
| `Full GC`                   | Whole-heap stop-the-world    | "You have a problem" |
| `Concurrent Cycle`          | Marking with the application | Not a pause          |

C4 collapses these into `GPGC New` and `GPGC Old`, both concurrent.

---

## The four pathologies

```
Long GC pause
    │
    ├─ pause scales with old gen?      → PROMOTION STORM
    ├─ many small regions, churn?      → FRAGMENTATION
    ├─ Update RS dominates pause?      → CROSS-GEN REFS
    └─ EvacuationFailed events?        → EVAC FAILURE
```

Recognise the JFR signature; you can diagnose any of these in any application.

---

## Promotion storm signature

- `jdk.PromoteObjectOutsidePLAB` count climbing
- Old gen growing monotonically
- Mixed collections appear and lengthen
- High allocation rate + high survival rate

Fix order: reduce allocation → increase young gen → change collector.

---

## Fragmentation signature

- Small objects (size < 1 KB) dominate `jdk.ObjectAllocationSample`
- `jdk.G1HeapRegionTypeChange` events frequent
- Pause time scales with region count, not heap size
- `Object Copy` phase dominates

Fix: compacting concurrent collector, or consolidate small objects.

---

## Cross-generational reference signature

- `Update RS` or `Scan RS` dominates pause time
- `ExecutionSample` stacks point into `G1BarrierSet`
- Mutable singletons holding refs to recent young-gen objects

Fix: immutable holders, copy-on-write, or a collector without remembered sets.

---

## Exercise 2.5

**Diagnose PROMOTION_STORM.**

```bash
# Pre-recorded:
jmc -open workshop/recordings/g1-promotion-storm.jfr
jmc -open workshop/recordings/c4-promotion-storm.jfr
```

For G1: when does the first mixed collection trigger? What was old gen at that moment?

For C4: same workload. Why no pause increase?

> Template: `workshop/exercises/module-2-diagnose-promotion-storm/analysis-template.md`

---

## Module 2 checkpoint

Two questions:

1. P99 = 80 ms, Max = 800 ms. Which do you fix first?
2. What recording duration makes P99 statistically meaningful at 10 collections per minute?

---

<!-- _class: lead -->

# Module 3

## Custom JFR events

45 minutes

---

## Anatomy of a custom event

```java
@Name("trade.published")
@Label("Trade Published")
@StackTrace(false)
public static class TradePublished extends Event {
    public String symbol;
    public long price;
    public int quantity;
    public String side;
}
```

```java
TradePublished event = new TradePublished();
if (event.isEnabled()) {           // ← critical: cheap when JFR is off
    event.symbol = symbol;
    event.price = price;
    event.quantity = qty;
    event.side = side.name();
    event.commit();
}
```

---

## Three rules for hot-path events

1. **`event.isEnabled()`** before any field assignment. The JIT can sometimes elide the `new Event()` allocation when the type is disabled, but only the guard stops your field-population work from running.
2. **`@StackTrace(false)`** on high-volume events. Stack capture is the dominant cost.
3. **Primitive fields over Strings.** Strings are interned in the JFR pool; the lookup is per-emission.

---

## Correlating with GC events

In JMC's Event Browser, overlay timelines:

```
GC pause:        [---50ms---]
SBE encode:  ............    .  .  .   .
                ^             ^
                stalls        catches up
```

If SBE encodes go to zero during the pause, the publisher virtual thread was stopped.
If they don't, you have carrier-thread pinning to investigate.

---

## Exercise 3.4

**Add a `BurstPatternEvent` that fires on 5x spikes.**

```bash
# Starter file:
workshop/exercises/module-3-burst-event/BurstPatternEvent.starter.java

# Install into source tree:
./workshop/scripts/install-exercise.sh module-3-burst-event

# Rebuild and roll instance 1:
docker compose -f docker-compose-c4.yml build trader-stream-c4-1
docker compose -f docker-compose-c4.yml up -d --no-deps trader-stream-c4-1
```

Verify the event appears in JMC's Event Browser.

---

## Module 3 checkpoint

What in your own application is the single hottest code path you currently have no visibility into?

Pick one. Note the fields you would want on the event. We will come back to this in Module 5.

---

<!-- _class: lead -->

# Module 4

## Collector comparison

45 minutes

---

## The other four scenarios

|     Scenario      |            Stresses            |
|-------------------|--------------------------------|
| `GROWING_HEAP`    | Mixed collection scaling       |
| `PROMOTION_STORM` | Old-gen collection efficiency  |
| `FRAGMENTATION`   | Compaction overhead            |
| `CROSS_GEN_REFS`  | Remembered-set / write barrier |

Same code. Same heap. Different collector. Different outcome.

---

## Side-by-side

```bash
# Visual:
jmc -open workshop/recordings/c4-fragmentation.jfr \
    -open workshop/recordings/g1-fragmentation.jfr

# CLI:
./workshop/scripts/compare-recordings.sh \
    workshop/recordings/c4-fragmentation.jfr \
    workshop/recordings/g1-fragmentation.jfr
```

---

## Exercise 4.4

**For each scenario, write a one-sentence behavioural difference.**

|     Scenario      | Difference |
|-------------------|------------|
| `GROWING_HEAP`    | ...        |
| `PROMOTION_STORM` | ...        |
| `FRAGMENTATION`   | ...        |
| `CROSS_GEN_REFS`  | ...        |

> Template: `workshop/exercises/module-4-collector-comparison/comparison-template.md`

---

## Module 4 checkpoint

C4 is not always the right answer. Two questions:

1. For a 200 ms P99.9 budget, is Prime's cost justified? On what does the answer depend?
2. How much of C4 do you get for free with ZGC in OpenJDK 21? Where does ZGC still differ?

---

<!-- _class: lead -->

# Module 5

## Applying to your applications

30 minutes

---

## Pattern matching in the wild

| Workshop pathology |                    Where it shows up                     |
|--------------------|----------------------------------------------------------|
| Promotion storm    | Caches with TTL > young-gen interval; pooled connections |
| Fragmentation      | Long-lived apps with many distinct object sizes          |
| Cross-gen refs     | Mutable singletons holding recent request objects        |
| Evacuation failure | Heap sized too tight; allocation bursts                  |

The signatures are the same. Only the application changes.

---

## Allocation vs. collector choice

```
Long GC pauses observed
    │
    ├─ allocation > 500 MB/s?  → reduce allocation FIRST
    │                            (flyweight, off-heap, primitive arrays)
    ├─ heap > 8 GB?            → consider concurrent collector
    │                            (ZGC, C4, Shenandoah)
    └─ G1 fits but tail is bad → tune MaxGCPauseMillis,
                                  InitiatingHeapOccupancyPercent
```

Reducing allocation is almost always cheaper than changing JVM.

---

## Production-safe JFR

```bash
java \
  -XX:StartFlightRecording=name=production,settings=default,maxsize=1g,maxage=24h,dumponexit=true,filename=/var/log/myapp/jfr/production.jfr \
  -XX:FlightRecorderOptions=stackdepth=64
```

- `settings=default` keeps overhead ~1%.
- Workshop profile is dev-only.
- Cap size and age.
- `jcmd <pid> JFR.dump filename=...` for on-demand snapshots during incidents.

---

## Three event templates

For your own applications:

|    Template     |                           When                           |
|-----------------|----------------------------------------------------------|
| `LatencyEvent`  | Any operation whose tail latency matters                 |
| `ThrottleEvent` | Circuit breaker / rate limit / backpressure state change |
| `ResourceEvent` | Pool / cache / file-handle state crossing a threshold    |

In `workshop/exercises/module-5-apply-to-your-app/event-templates/`.

---

<!-- _class: lead -->

## Q&A and open debugging

Bring your own JFR recordings.

We will walk through real attendee data for the last 10 minutes.

---

## Takeaways

1. JFR is the diagnostic of record for JVM pause behaviour.
2. The four pathologies have characteristic signatures.
3. `event.isEnabled()` and `@StackTrace(false)` are the workshop's two-line summary for production-grade custom events.
4. Reduce allocation before changing collector.
5. Recording duration drives statistical confidence; pick yours deliberately.

---

## Further reading

- *Java Performance: The Definitive Guide* (Scott Oaks), ch. 5
- JEP 328: Flight Recorder
- Azul C4 paper: <https://www.azul.com/products/components/azul-platform-prime/>
- G1 ergonomics: <https://docs.oracle.com/en/java/javase/21/gctuning/>
- JMC user guide: <https://docs.oracle.com/en/java/java-components/jdk-mission-control/9/user-guide/>

Workshop materials: `workshop/` in this repo.

---

<!-- _class: lead -->

# Thank you

Questions, recordings, war stories: bring them.
