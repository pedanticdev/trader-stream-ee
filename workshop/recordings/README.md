# Pre-recorded JFR files

Five GC stress scenarios across two collectors, captured against TradeStreamEE running at roughly 100K messages per second.

Each recording is approximately 35 seconds wall-clock (5s warmup + 30s active), using the `tradestream-workshop.jfc` profile, with 2 GB heap (`-Xms2g -Xmx2g`). Recorded on Zulu 25 + ZGC and Temurin 25 + G1GC, each as a single Payara Micro instance with Hazelcast enabled.

|           File           | Collector |     Scenario      |                              What to look for                              |
|--------------------------|-----------|-------------------|----------------------------------------------------------------------------|
| `zgc-baseline.jfr`      | ZGC       | `STEADY_LOAD`     | Sub-millisecond pauses across the recording. The reference.                |
| `g1-baseline.jfr`       | G1        | `STEADY_LOAD`     | Periodic young-gen pauses of 5-15 ms even at steady state.                 |
| `zgc-growing-heap.jfr`  | ZGC       | `GROWING_HEAP`    | Heap grows 100 MB to 2 GB over 30s. ZGC concurrent cycles, no STW impact.  |
| `g1-growing-heap.jfr`   | G1        | `GROWING_HEAP`    | Mixed collection pauses scaling with live set.                             |
| `zgc-promotion-storm.jfr` | ZGC    | `PROMOTION_STORM` | 300 MB/s with 50% survival. No visible pause increase.                     |
| `g1-promotion-storm.jfr` | G1       | `PROMOTION_STORM` | `PromoteObjectOutsidePLAB` events climbing; mixed collections trigger.     |
| `zgc-fragmentation.jfr` | ZGC       | `FRAGMENTATION`   | Small objects (100-1000 B), random lifetimes. Compaction is concurrent.    |
| `g1-fragmentation.jfr`  | G1        | `FRAGMENTATION`   | G1 region freelist pressure. Watch `jdk.G1HeapRegionTypeChange`.           |
| `zgc-cross-gen-refs.jfr` | ZGC      | `CROSS_GEN_REFS`  | No remembered set overhead; load barriers handle references.               |
| `g1-cross-gen-refs.jfr` | G1       | `CROSS_GEN_REFS`  | `jdk.GCPhasePauseLevel1 -> Update RS` time should be the dominant phase.   |

## Regenerating

If you want to capture fresh recordings on your hardware:

```bash
docker compose -f docker-compose-workshop.yml up -d
# wait for both instances healthy
./workshop/scripts/record-scenarios.sh
```

The script overwrites `zgc-*.jfr` and `g1-*.jfr` in this directory.

## Size budget

Workshop recordings target under 5 MB each. The `.jfc` profile disables high-frequency custom events (`trade.published`, `quote.published`, `message.batch.processed`, `sbe.encode`, `sbe.decode`) that would otherwise generate millions of events per recording. Allocation profiling uses `jdk.ObjectAllocationSample` (300/s throttled) instead of per-TLAB events.
