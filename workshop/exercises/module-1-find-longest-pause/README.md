# Module 1 exercise: find the longest GC pause

## Problem

Open the baseline recording. Find the longest pause in the recording. Note its duration, the collector that produced it, and the cause.

## Inputs

Either of:

- A fresh recording you produced with `curl -X POST 'http://localhost:8081/trader-stream-ee/api/jfr/recording/start?name=baseline&durationSeconds=60&settings=tradestream-workshop'` (output appears in `monitoring/recordings/c4-1/`).
- The pre-recorded `workshop/recordings/c4-baseline.jfr`.

## Steps

1. Open the recording in JMC: `jmc -open <path>`.
2. Navigate to `Outline → General → Garbage Collections`.
3. Sort the Duration column descending.
4. Click the first row.

## What to record

Fill in:

```
Recording file:        ____________________
Longest pause (ms):    ____________________
Collector name:        ____________________
GC cause:              ____________________
Approximate heap used: ____________________ MB at the time of the pause
```

## Stretch goal

Repeat the exercise with `g1-baseline.jfr`. Compare the longest pause and the count of pauses over 5 ms. Which collector is doing more work, and which one is doing the work in a way the application can feel?

## Hints (read only if stuck)

<details>
<summary>Hint 1</summary>
The Duration column is in milliseconds. JMC displays it formatted (e.g., "5.20 ms"); the underlying value is nanoseconds.
</details>

<details>
<summary>Hint 2</summary>
For C4, the longest pause is usually 0 ms or near it. The "longest" might still be a sub-millisecond entry. That itself is the data point: there is nothing to optimise.
</details>

<details>
<summary>Hint 3</summary>
For G1, look at the "Name" column. Distinguish "G1 Young Generation" from "G1 Concurrent Cycle" (the latter is not a stop-the-world pause).
</details>

## Discussion prompt

If you found a sub-1ms longest pause, is the recording too short to be meaningful? How long would it need to be to capture a representative tail?
