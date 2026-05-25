---
marp: true
theme: default
paginate: true
backgroundColor: "#0c0e14"
color: "#e6e6e6"
header: "Low-Latency Trading with Jakarta EE and Payara Micro"
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

# Low-Latency Trading with Jakarta EE

## Build, observe, and stress-test a real trading system on Payara Micro

JNation · 3 hours 30 minutes

<span class="muted">Bring a laptop. We will read recordings together.</span>

---

## Today's question

> Your trading system meets its latency SLA in staging.
> Production peak hits. P99 climbs from 8 ms to 110 ms.
> Support has no recording. Nobody changed the code.
> **What happened, and how do you prove it?**

This workshop builds the answer from JFR data on a real Jakarta EE application.

---

## What you leave with

- The TradeStreamEE source and Docker setup (a real Jakarta EE 11 trading app on Payara Micro 7)
- Ten pre-recorded `.jfr` files across five scenarios on two OpenJDK runtimes
- A printable JFR analysis checklist
- Three reusable JFR event templates
- A diagnostic mental model that transfers to any Java application on any runtime

---

## Five modules

| # |            Module             |  Time  |
|---|-------------------------------|--------|
| 1 | Setup and warm-up             | 30 min |
| 2 | Reading JFR recordings        | 60 min |
| 3 | Custom JFR events             | 45 min |
| 4 | Stress testing & production readiness | 45 min |
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

## One application, two OpenJDK runtimes

```
:8080  ┌─ Runtime A ─────┐         :9080  ┌─ Runtime B ─────┐
       │ Zulu 25 + ZGC   │                │ Temurin 25 + G1 │
       │ Payara Micro 7  │                │ Payara Micro 7  │
       └─────────────────┘                └─────────────────┘

       Identical Jakarta EE 11 application on both sides.
       Identical heap (-Xms2g -Xmx2g, AlwaysPreTouch, THP).
       Identical workload.
       The OpenJDK runtime is the only deliberate variable.
```

The application is the constant; the runtime is the variable.
This workshop will not declare a winner: the recordings will say what they say, and you will read them.

---

<!-- _class: lead -->

# Module 1

## Setup and warm-up

30 minutes

---

## Bring up the instances

```bash
git clone <repo>
cd trader-stream-ee
./workshop/scripts/quickstart.sh
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
# Start a 60-second recording on the ZGC instance
curl -X POST 'http://localhost:8080/trader-stream-ee/api/jfr/recording/start?name=baseline&durationSeconds=60&settings=tradestream-workshop'

# List captured files (auto-dumped on duration expiry)
curl http://localhost:8080/trader-stream-ee/api/jfr/files | jq

# Recordings persist to host via Docker bind mount:
ls -lh monitoring/recordings/workshop-zgc/
```

---

## Open in JMC

```bash
jmc -open monitoring/recordings/workshop-zgc/baseline-*.jfr
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

Compare across the room. What does your baseline recording show on each port?

- Port 8080 (Zulu 25 + ZGC): which collection events fire, and at what pause duration?
- Port 9080 (Temurin 25 + G1): which collection events fire, and at what pause duration?

Same heap, same workload, same Payara Micro application. **What is each runtime doing differently to produce its characteristic pause profile?**

Hold the question. We will answer it in Module 2 by reading the JFR events.

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

ZGC reports its collections under different names (`ZGen Young`, `ZGen Old`, plus brief `Pause Mark Start` / `Pause Relocate Start` events). Both generations are collected predominantly concurrently; the STW events you see in JFR are short coordination phases, not collection work.

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

Fix: concurrent compacting collector (ZGC, C4), or consolidate small objects.

---

## Cross-generational reference signature

- `Update RS` or `Scan RS` dominates pause time
- `ExecutionSample` stacks point into `G1BarrierSet`
- Mutable singletons holding refs to recent young-gen objects

Fix: immutable holders, copy-on-write, or a concurrent collector without remembered sets (ZGC, C4).

---

## Exercise 2.5

**Diagnose EARNINGS_SPIKE.**

```bash
# Pre-recorded:
jmc -open workshop/recordings/g1-earnings-spike.jfr
jmc -open workshop/recordings/zgc-earnings-spike.jfr
```

For each recording, report what you see:

- G1: when does the first mixed collection trigger? What is old gen at that moment? How does pause time change across the recording?
- ZGC: same workload, what events fire and at what pause duration? Does pause time change across the recording?

Bring your findings to the room. The recordings will say what they say; we will read them together.

> Template: `workshop/exercises/module-2-diagnose-earnings-spike/analysis-template.md`

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

# Rebuild and roll one instance (Zulu shown; the same step works for Temurin):
docker compose -f docker-compose-workshop.yml build trader-stream-workshop-zgc
docker compose -f docker-compose-workshop.yml up -d --no-deps trader-stream-workshop-zgc
```

Verify the event appears in JMC's Event Browser.

---

## Module 3 checkpoint

What in your own application is the single hottest code path you currently have no visibility into?

Pick one. Note the fields you would want on the event. We will come back to this in Module 5.

---

<!-- _class: lead -->

# Module 4

## Stress testing & production readiness

45 minutes

---

## The other four scenarios

|             Scenario             |            Stresses            |
|----------------------------------|--------------------------------|
| `INTRADAY_POSITION_GROWTH`       | Mixed collection scaling       |
| `EARNINGS_SPIKE`                 | Old-gen collection efficiency  |
| `MULTI_VENUE_QUOTE_CHURN`        | Compaction overhead            |
| `LONG_HORIZON_POSITION_BOOK`     | Remembered-set / write barrier |

The application and heap are identical across both ports; the OpenJDK runtime is the only variable, and the outcome you will read from JFR follows from that.

---

## Side-by-side

```bash
# Visual:
jmc -open workshop/recordings/zgc-multi-venue-quote-churn.jfr \
    -open workshop/recordings/g1-multi-venue-quote-churn.jfr

# CLI:
./workshop/scripts/compare-recordings.sh \
    workshop/recordings/zgc-multi-venue-quote-churn.jfr \
    workshop/recordings/g1-multi-venue-quote-churn.jfr
```

---

## Exercise 4.4

**For each scenario, write a one-sentence behavioural difference.**

|             Scenario             | Difference |
|----------------------------------|------------|
| `INTRADAY_POSITION_GROWTH`       | ...        |
| `EARNINGS_SPIKE`                 | ...        |
| `MULTI_VENUE_QUOTE_CHURN`        | ...        |
| `LONG_HORIZON_POSITION_BOOK`     | ...        |

> Template: `workshop/exercises/module-4-stress-testing/comparison-template.md`

---

## Module 4 checkpoint

Your trading system needs sub-10 ms P99 latency under adversarial load. Two questions:

1. Based on the recordings in front of you, which runtime configuration would you ship to production? Cite the events that drove the choice.
2. At what allocation rate or live-set size does each runtime start to fall behind the application's needs? Where is that line for *your* production workload?

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

The signatures are the same across applications; only the surrounding code changes.

---

## Allocation vs. collector choice

```
Long GC pauses observed
    │
    ├─ allocation > 500 MB/s?  → reduce allocation FIRST
    │                            (flyweight, off-heap, primitive arrays)
    ├─ heap > 8 GB?            → concurrent collector
    │                            (ZGC, Shenandoah, C4)
    ├─ throughput-sensitive     → C4 (no read barrier overhead)
    │  + low latency?           │
    └─ G1 fits but tail is bad → tune MaxGCPauseMillis,
                                  InitiatingHeapOccupancyPercent
```

Reducing allocation is almost always cheaper than changing collector.

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

1. JFR is the diagnostic of record for production Jakarta EE applications. Ship every container with always-on JFR.
2. The four GC pathologies have signatures you can recognise in any application on any JVM runtime.
3. `event.isEnabled()` and `@StackTrace(false)` are the two rules for production-grade custom JFR events.
4. Stress-test before shipping. JFR tells you what happened; pre-production stress tells you whether it will happen in prod.
5. The runtime is a production decision with measurable consequences. The recording, not the brand, decides.

---

## The Azul stack

You ran community Payara Micro 7 on Azul Zulu OpenJDK in the lab today. The commercial Azul stack consolidates the JDK, the Jakarta EE runtime, and the high-performance JVM under one vendor:

|                          | What it adds beyond what you ran today                                                                                                                            |
|--------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Azul Payara Micro 7**  | Same Jakarta EE 11 runtime, plus monthly security patches, CVE Numbering Authority disclosures, multi-phase lifecycle (Full / Extended / Lifetime), 24-48h bug-fix SLA, and Azul Zulu OpenJDK bundled in the subscription. |
| **Azul Platform Prime**  | The JVM above the OpenJDK pause-time envelope: C4 collector, ReadyNow warmup, Falcon JIT, production support for workloads where ZGC's allocation envelope is the constraint.                                              |

The JFR analysis skills you used today apply unchanged, and the same Payara application runs without modification on either runtime. Only the runtime ceiling changes.

Talk to us if your production workload has outgrown the recordings you read today.

---

## Further reading

- Payara Micro 7 documentation: <https://docs.payara.fish/>
- Jakarta EE 11 specification: <https://jakarta.ee/specifications/>
- JDK Flight Recorder (JEP 328): <https://openjdk.org/jeps/328>
- ZGC documentation: <https://openjdk.org/projects/zgc/>
- G1 ergonomics: <https://docs.oracle.com/en/java/javase/21/gctuning/>
- JMC user guide: <https://docs.oracle.com/en/java/java-components/jdk-mission-control/9/user-guide/>
- Azul Platform Prime documentation: <https://docs.azul.com/prime/>

Workshop materials: `workshop/` in this repo.

---

<!-- _class: lead -->

# Thank you

Questions, recordings, war stories: bring them.
