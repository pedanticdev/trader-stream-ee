# Module 4 exercise: document the behavioural differences

## Problem

For each of the four stress scenarios beyond baseline, write a one-sentence summary of how ZGC and G1 differ under identical workloads. Back each claim with an event count or timing value pulled from the recordings.

## Inputs

The pre-recorded files:

```
workshop/recordings/zgc-growing-heap.jfr      workshop/recordings/g1-growing-heap.jfr
workshop/recordings/zgc-promotion-storm.jfr   workshop/recordings/g1-promotion-storm.jfr
workshop/recordings/zgc-fragmentation.jfr     workshop/recordings/g1-fragmentation.jfr
workshop/recordings/zgc-cross-gen-refs.jfr    workshop/recordings/g1-cross-gen-refs.jfr
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

|     Scenario      | One-sentence summary |
|-------------------|----------------------|
| `GROWING_HEAP`    |                      |
| `PROMOTION_STORM` |                      |
| `FRAGMENTATION`   |                      |
| `CROSS_GEN_REFS`  |                      |

## Reference summaries (read after you have your own)

<details>
<summary>Reference summaries from a 4 GB heap recording on a 32-core Linux host</summary>

|     Scenario      |                                                                                                  What the recordings show                                                                                                   |
|-------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `GROWING_HEAP`    | G1's pause times rise from ~5 ms to ~50 ms as the live set grows from 100 MB to 2 GB, driven by lengthening mixed-collection cycles. ZGC stays under 1 ms throughout.                                                        |
| `PROMOTION_STORM` | G1 fires several mixed collections in the second half of the recording, each pausing 30-80 ms; `PromoteObjectOutsidePLAB` counts climb. ZGC runs concurrently and produces no visible pause.                      |
| `FRAGMENTATION`   | G1 shows region-type churn (`jdk.G1HeapRegionTypeChange` events spike) and occasional 20-30 ms pauses for region compaction. ZGC compacts concurrently and shows no pause increase.                                          |
| `CROSS_GEN_REFS`  | G1's `Update RS` phase dominates pause time (often 60-80% of each pause); the write barrier cost is visible in `ExecutionSample` stacks pointing to `G1BarrierSet`. ZGC has no remembered set and shows no equivalent phase. |

</details>

## Discussion prompt

Pick the scenario where the ZGC-vs-G1 gap is *smallest*. Is there a tuning change to G1 that would close most of that gap? What is the cost of that tuning?
