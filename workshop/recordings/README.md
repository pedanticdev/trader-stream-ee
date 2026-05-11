# Pre-recorded JFR files

Five GC stress scenarios across two collectors, captured against TradeStreamEE running at roughly 50K-80K messages per second.

Each recording is approximately 60 seconds wall-clock, using the `tradestream-workshop.jfc` profile, with 4 GB heap (`-Xms4g -Xmx4g`). The pressure framework gives each scenario 10 seconds of warmup before the recording window opens.

|           File           | Cluster |     Scenario      |                              What to look for                              |
|--------------------------|---------|-------------------|----------------------------------------------------------------------------|
| `c4-baseline.jfr`        | C4      | `STEADY_LOAD`     | Sub-millisecond pauses across the recording. The reference.                |
| `g1-baseline.jfr`        | G1      | `STEADY_LOAD`     | Periodic young-gen pauses of 5-15 ms even at steady state.                 |
| `c4-growing-heap.jfr`    | C4      | `GROWING_HEAP`    | Heap grows 100 MB → 2 GB over 60s. Look at GPGC concurrent old-gen cycles. |
| `g1-growing-heap.jfr`    | G1      | `GROWING_HEAP`    | Mixed collection pauses scaling with live set.                             |
| `c4-promotion-storm.jfr` | C4      | `PROMOTION_STORM` | 300 MB/s with 50% survival. No visible pause increase.                     |
| `g1-promotion-storm.jfr` | G1      | `PROMOTION_STORM` | `PromoteObjectOutsidePLAB` events climbing; mixed collections trigger.     |
| `c4-fragmentation.jfr`   | C4      | `FRAGMENTATION`   | Small objects (100-1000 B), random lifetimes. Compaction is concurrent.    |
| `g1-fragmentation.jfr`   | G1      | `FRAGMENTATION`   | G1 region freelist pressure. Watch `jdk.G1HeapRegionTypeChange`.           |
| `c4-cross-gen-refs.jfr`  | C4      | `CROSS_GEN_REFS`  | Write barrier is cheap, no remembered set.                                 |
| `g1-cross-gen-refs.jfr`  | G1      | `CROSS_GEN_REFS`  | `jdk.GCPhasePauseLevel1 → Update RS` time should be the dominant phase.    |

## Regenerating

If you want to capture fresh recordings on your hardware:

```bash
./start-comparison.sh
./workshop/scripts/record-scenarios.sh
```

The script overwrites `c4-*.jfr` and `g1-*.jfr` in this directory.

## Size budget

Workshop recordings target under 30 MB each. The `.jfc` profile disables per-TLAB stack traces (the biggest source of recording bloat under sustained allocation) and uses `jdk.ObjectAllocationSample` instead. If you change the JFC and recordings balloon past 100 MB, check whether `jdk.ObjectAllocationInNewTLAB` is on.
