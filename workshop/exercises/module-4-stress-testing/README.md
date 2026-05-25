# Module 4 exercise: assess production readiness across runtimes

## Problem

For each of the four stress scenarios beyond baseline, write a one-sentence summary of how the two OpenJDK runtimes behave under identical workloads. Back each claim with an event count or timing value pulled from the recordings.

## Inputs

The pre-recorded files:

```
workshop/recordings/zgc-intraday-position-growth.jfr   workshop/recordings/g1-intraday-position-growth.jfr
workshop/recordings/zgc-earnings-spike.jfr             workshop/recordings/g1-earnings-spike.jfr
workshop/recordings/zgc-multi-venue-quote-churn.jfr    workshop/recordings/g1-multi-venue-quote-churn.jfr
workshop/recordings/zgc-long-horizon-position-book.jfr workshop/recordings/g1-long-horizon-position-book.jfr
```

## Steps

For each scenario:

1. Run the CLI comparison:

   ```bash
   ./workshop/scripts/compare-recordings.sh \
       workshop/recordings/zgc-<scenario>.jfr \
       workshop/recordings/g1-<scenario>.jfr
   ```
2. Open both files in JMC side-by-side.
3. Fill in the [comparison-template.md](./comparison-template.md) for the scenario.
4. Write your one-sentence summary in the table below.

## Your summaries

|             Scenario             | One-sentence summary |
|----------------------------------|----------------------|
| `INTRADAY_POSITION_GROWTH`       |                      |
| `EARNINGS_SPIKE`                 |                      |
| `MULTI_VENUE_QUOTE_CHURN`        |                      |
| `LONG_HORIZON_POSITION_BOOK`     |                      |

## Reference summaries (read after you have your own)

<details>
<summary>Reference summaries from a 4 GB heap recording on a 32-core Linux host</summary>

|             Scenario             |                                                                                                  What the recordings show                                                                                                   |
|----------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `INTRADAY_POSITION_GROWTH`       | G1's pause times rise from ~5 ms to ~50 ms as the position book grows from 100 MB to 2 GB, driven by lengthening mixed-collection cycles. ZGC stays under 1 ms throughout.                                                  |
| `EARNINGS_SPIKE`                 | G1 fires several mixed collections in the second half of the recording, each pausing 30-80 ms; `PromoteObjectOutsidePLAB` counts climb. ZGC runs concurrently and produces no visible pause.                                |
| `MULTI_VENUE_QUOTE_CHURN`        | G1 shows region-type churn (`jdk.G1HeapRegionTypeChange` events spike) and occasional 20-30 ms pauses for region compaction. ZGC compacts concurrently and shows no pause increase.                                         |
| `LONG_HORIZON_POSITION_BOOK`     | G1's `Update RS` phase dominates pause time (often 60-80% of each pause); the write barrier cost is visible in `ExecutionSample` stacks pointing to `G1BarrierSet`. ZGC has no remembered set and shows no equivalent phase.|

</details>

## Discussion prompt

Pick the scenario where the gap between the two runtimes is *smallest*. Based on the recordings in front of you, would you ship to production on either runtime for that scenario? What evidence drives the decision?
