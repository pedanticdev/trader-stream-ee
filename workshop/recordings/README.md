# Pre-recorded JFR files

Five trading-scenario recordings across two OpenJDK runtimes, captured against TradeStreamEE running at roughly 100K messages per second.

Each recording is approximately 35 seconds wall-clock (5s warmup + 30s active), using the `tradestream-workshop.jfc` profile, with 2 GB heap (`-Xms2g -Xmx2g`). Recorded on Zulu 25 + ZGC and Temurin 25 + G1GC, each as a single Payara Micro instance with Hazelcast enabled.

|                  File                    | Runtime |          Scenario             |                              What to look for                                  |
|------------------------------------------|---------|-------------------------------|--------------------------------------------------------------------------------|
| `zgc-baseline.jfr`                       | ZGC     | `STEADY_LOAD`                 | Sub-millisecond pauses across the recording. The reference.                    |
| `g1-baseline.jfr`                        | G1      | `STEADY_LOAD`                 | Periodic young-gen pauses of 5-15 ms even at steady state.                     |
| `zgc-intraday-position-growth.jfr`       | ZGC     | `INTRADAY_POSITION_GROWTH`    | Position book grows 100 MB to 2 GB over 30s. ZGC concurrent cycles, no STW.    |
| `g1-intraday-position-growth.jfr`        | G1      | `INTRADAY_POSITION_GROWTH`    | Mixed collection pauses scaling with live set.                                 |
| `zgc-earnings-spike.jfr`                 | ZGC     | `EARNINGS_SPIKE`              | 300 MB/s with 50% order survival. No visible pause increase.                   |
| `g1-earnings-spike.jfr`                  | G1      | `EARNINGS_SPIKE`              | `PromoteObjectOutsidePLAB` events climbing; mixed collections trigger.         |
| `zgc-multi-venue-quote-churn.jfr`        | ZGC     | `MULTI_VENUE_QUOTE_CHURN`     | Small objects (100-1000 B), random lifetimes. Compaction is concurrent.        |
| `g1-multi-venue-quote-churn.jfr`         | G1      | `MULTI_VENUE_QUOTE_CHURN`     | G1 region freelist pressure. Watch `jdk.G1HeapRegionTypeChange`.               |
| `zgc-long-horizon-position-book.jfr`     | ZGC     | `LONG_HORIZON_POSITION_BOOK`  | No remembered set overhead; load barriers handle references.                   |
| `g1-long-horizon-position-book.jfr`      | G1      | `LONG_HORIZON_POSITION_BOOK`  | `jdk.GCPhasePauseLevel1 -> Update RS` time should be the dominant phase.       |

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
