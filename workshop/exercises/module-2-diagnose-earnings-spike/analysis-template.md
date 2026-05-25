# EARNINGS_SPIKE analysis template

Fill this in as you work through the recording. Bring it to the Module 2 discussion.

## Recording metadata

- File: ___________
- Cluster: ___________
- Duration: ___________
- Settings profile: ___________

## Timeline observations

| Second | Pause time (ms) | Old gen (MB) | Notes |
|--------|-----------------|--------------|-------|
| 5      |                 |              |       |
| 15     |                 |              |       |
| 30     |                 |              |       |
| 45     |                 |              |       |
| 60     |                 |              |       |

## Event counts across the recording window

|                       Event                        | Count |
|----------------------------------------------------|-------|
| `jdk.GarbageCollection`                            |       |
| `jdk.GCPhasePauseLevel1 (Evacuate Collection Set)` |       |
| `jdk.PromoteObjectOutsidePLAB`                     |       |
| `jdk.G1HeapRegionTypeChange (Old → Free)`          |       |
| `gc.sla.violation` (custom)                        |       |
| `trade.published` (custom)                         |       |

## Hypothesis

In one sentence, what is the dominant cause of pause growth in this recording?

___________________________________________________________________

___________________________________________________________________

## Cross-check

Open the equivalent recording on the other collector. Did the same events fire? At the same rate? Why or why not?

___________________________________________________________________

___________________________________________________________________

