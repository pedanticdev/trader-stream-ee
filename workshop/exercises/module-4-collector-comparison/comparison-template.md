# Side-by-side comparison template

Use one copy of this template per stress scenario.

## Scenario: ___________________

## Recording files

- C4: `workshop/recordings/c4-________.jfr`
- G1: `workshop/recordings/g1-________.jfr`

## Headline metrics

|           Metric           | C4 | G1 | Why they differ |
|----------------------------|----|----|-----------------|
| Max pause (ms)             |    |    |                 |
| P99 pause (ms)             |    |    |                 |
| P50 pause (ms)             |    |    |                 |
| Pause count                |    |    |                 |
| GC throughput (% app time) |    |    |                 |
| Live heap at end (MB)      |    |    |                 |

## Event counts

|             Event              | C4 count | G1 count |
|--------------------------------|----------|----------|
| `jdk.GarbageCollection`        |          |          |
| `jdk.GCPhasePauseLevel1`       |          |          |
| `jdk.G1HeapRegionTypeChange`   | n/a      |          |
| `jdk.EvacuationFailed`         | n/a      |          |
| `jdk.PromotionFailed`          | n/a      |          |
| `jdk.PromoteObjectOutsidePLAB` |          |          |
| `gc.sla.violation`             |          |          |
| `aeron.backpressure`           |          |          |
| `trade.published`              |          |          |

## Hot stacks

In JMC: `Outline → Threads → Hot Methods`.

C4 top three:

1. 

___________________________________________________________________

2. 

___________________________________________________________________

3. 

___________________________________________________________________

G1 top three:

1. 

___________________________________________________________________

2. 

___________________________________________________________________

3. 

___________________________________________________________________

## One-sentence interpretation

___________________________________________________________________

___________________________________________________________________

## Caveats and noise

What is in this recording that is NOT a clean signal? (Background processes, JIT warmup not yet complete, etc.)

___________________________________________________________________

___________________________________________________________________

