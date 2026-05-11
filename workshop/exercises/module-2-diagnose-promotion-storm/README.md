# Module 2 exercise: diagnose PROMOTION_STORM

## Problem

The `PROMOTION_STORM` scenario allocates at 300 MB/sec with roughly 50% survival to old generation. Use JFR to explain *why* this produces increasing pause times on G1, and *why* C4 does not respond the same way.

## Inputs

- `workshop/recordings/g1-promotion-storm.jfr`
- `workshop/recordings/c4-promotion-storm.jfr`

Or generate fresh against your running clusters:

```bash
# G1
curl -X POST 'http://localhost:9081/trader-stream-ee/api/jfr/recording/start?name=promo-live&durationSeconds=75&settings=tradestream-workshop'
sleep 10
curl -X POST 'http://localhost:9081/trader-stream-ee/api/pressure/mode/PROMOTION_STORM'
sleep 60
curl -X POST 'http://localhost:9081/trader-stream-ee/api/pressure/mode/OFF'
```

## Steps

Open the G1 recording in JMC and check these views in order:

1. **`Outline → General → Garbage Collections`** sorted by Start Time. Watch the pause duration column over the recording duration. Does it climb?
2. **`Outline → Memory → Heap Usage After GC`** — line chart of old-gen occupancy over time. Where does old gen start saturating?
3. **`Event Browser → jdk.PromoteObjectOutsidePLAB`** — events fire when an object is promoted to old gen but the PLAB (promotion buffer) didn't have room. High frequency means the application is over-allocating into PLABs, which is the promotion-storm signature.
4. **`Outline → General → Garbage Collections → row detail`** for a mixed collection late in the recording. Look at the `Name` column for the `Mixed` qualifier and the per-phase breakdown.

Repeat for the C4 recording. The same heap behaviour should be present (objects are being promoted), but pause time should not move.

## Fill this in

```
G1 PROMOTION_STORM observations:
  Pause time at second 10:       _____ ms
  Pause time at second 50:       _____ ms
  Old gen at second 10:          _____ MB
  Old gen at second 50:          _____ MB
  PromoteObjectOutsidePLAB count: _____
  First mixed collection at:     second _____

C4 PROMOTION_STORM observations:
  Max pause across recording:    _____ ms
  Old gen at second 10:          _____ MB
  Old gen at second 50:          _____ MB
```

## Why the difference?

G1's old-gen collection is stop-the-world (the mixed phases). Once old gen fills, G1 must run mixed collections, and their cost scales with the live data they have to evacuate. The promotion storm builds the old gen up faster than concurrent marking can keep up, so mixed collections trigger and produce pauses.

C4's old-gen collection (GPGC Old) runs concurrently with the application. Promotion into old gen does not cause a stop-the-world phase. Heap fills the same way; the application doesn't pause.

## Discussion prompt

If you doubled the heap size on the G1 side, would the problem go away? If yes, why; if not, why not?

## Hint (read only if stuck)

<details>
<summary>Hint</summary>
Doubling the heap delays the problem but does not remove it. G1's pause time on a mixed collection scales with the number of regions being collected; a bigger heap means more regions to evacuate per mixed pause. The problem returns at the new larger heap size, just later in the recording.
</details>

