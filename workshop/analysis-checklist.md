# JFR Analysis Checklist

Two pages. Print double-sided. Keep at your desk.

## Capture

- [ ] Recording length: at least 10 minutes for stable percentiles; longer for rare events.
- [ ] Settings profile: `default.jfc` in production (~1%), `profile.jfc` for dev (~2%), workshop `.jfc` only when reproducing a specific scenario.
- [ ] `maxsize` and `maxage` set; recording is circular, not unbounded.
- [ ] Application is in steady state when capture begins (JIT warmed, caches populated). Skip the first 60 seconds.
- [ ] If you control the workload, freeze it during capture. If you don't, note the request rate at start and end.
- [ ] Filename includes timestamp, host, and recording purpose.

## Triage: read in this order

1. **Recording duration and event coverage.** `Outline → General → Recording Information`. Confirm the timeline matches what you expected.
2. **GC pause headline.** `Outline → General → Garbage Collections`, sort Duration descending. Read the top 5 rows.
3. **Heap shape.** `Outline → Memory → Heap Usage After GC`. Is old gen growing? Plateau? Sawtooth?
4. **Allocation hot stacks.** `Outline → Memory → Allocations → Hot Methods`. Top 3 methods by bytes allocated.
5. **Custom application events.** `Event Browser → Custom`. Are domain events firing at expected rates?

If you only have time for one view, choose #2.

## Pause time distributions

| Quantile |                         What it tells you                          |
|----------|--------------------------------------------------------------------|
| P50      | "Typical pause." Mostly informational.                             |
| P95      | "Routine bad pause." Should be near the SLA threshold.             |
| P99      | "What 1 in 100 GCs cost." Most operational alerts live here.       |
| P99.9    | "What 1 in 1000 GCs cost." Where incidents come from.              |
| Max      | "Worst single pause." Investigate even one if it exceeds your SLA. |

Sample size matters. P99 from 10 collections is not statistically meaningful. Aim for hundreds of pause events in the window.

## Pathology triage tree

```
Pause time > SLA?
├─ Yes → check Name column:
│        ├─ "G1 Young Generation (Normal)"
│        │  └─ check Allocation rate. > 500 MB/s? → reduce allocation.
│        │     Otherwise → check survivor sizing.
│        ├─ "G1 Young Generation (Mixed)"
│        │  └─ check old gen growth. Filling fast? → PROMOTION STORM.
│        │     check region count being mixed. High? → FRAGMENTATION.
│        ├─ "G1 Full GC"
│        │  └─ EvacuationFailed events present? → to-space exhausted.
│        │     PromotionFailed present? → old gen full.
│        │     Neither? → System.gc() called, or metaspace.
│        └─ Pause's biggest phase (jdk.GCPhasePauseLevel1):
│           ├─ "Update RS"     → cross-gen ref pressure
│           ├─ "Scan RS"       → cross-gen ref pressure
│           ├─ "Evacuate CS"   → promotion / object copy cost
│           ├─ "Code Roots"    → many classes being recompiled
│           └─ "Ref Proc"      → too many soft/weak/phantom refs
└─ No → recording shows healthy GC. If app feels slow, look elsewhere
        (lock contention, virtual-thread pinning, I/O).
```

## Promotion storm signature

- `jdk.PromoteObjectOutsidePLAB` count climbs across recording.
- Old gen usage curve rises monotonically.
- Mixed collections appear and lengthen over time.
- Allocation rate is high (> 200 MB/s) AND survival rate is high (> 30%).

**Fixes, in order of preference:**
1. Reduce allocation in the survival-heavy code path (object pooling, primitive arrays).
2. Increase young gen so more objects die young (`-XX:NewRatio`, region count).
3. Switch to a concurrent collector (ZGC, C4, Shenandoah).

## Fragmentation signature

- Many small objects (`jdk.ObjectAllocationSample`, size < 1 KB).
- `jdk.G1HeapRegionTypeChange` events frequent.
- Pause time correlates with region count being evacuated, not heap size.
- `jdk.GCPhasePauseLevel2 → Object Copy` is the dominant phase.

**Fixes:**
1. Switch to a compacting concurrent collector (C4 compacts continuously, ZGC fragments less).
2. Consolidate small objects into arrays or off-heap buffers.
3. Increase `G1HeapRegionSize` so fewer regions hold equivalent data.

## Cross-generational reference signature

- `jdk.GCPhasePauseLevel1` events with name "Update RS" or "Scan RS" dominating pause time.
- `ExecutionSample` stacks pointing into `G1BarrierSet::write_ref_field_post`.
- Old-gen objects holding references to young-gen objects (mutable singletons, caches).

**Fixes:**
1. Make old-gen holders immutable or copy-on-write.
2. Switch to a collector without remembered sets (C4) or with cheaper barriers (ZGC's load barrier).
3. Reduce write rate to old-gen mutable fields.

## Evacuation failure signature

- `jdk.EvacuationFailed` events present.
- May be followed by a full GC if the heap remains constrained; JDK 21's Tenuring Anywhere change reduces this likelihood by letting subsequent young collections reclaim old-gen regions.
- All available heap regions are committed and none remain free for promotion or survivor copy when the failure fires.

**Fixes:**
1. Increase `MaxHeapSize`.
2. Reduce allocation rate.
3. Lower `InitiatingHeapOccupancyPercent` so concurrent marking starts earlier.

## Virtual thread red flags

- `jdk.VirtualThreadPinned` events with duration over 100 ms.
- Pinned stacks usually point to `synchronized` blocks or JNI calls.

**Fixes:**
1. Replace `synchronized` with `ReentrantLock` where the carrier thread would otherwise be pinned.
2. Move JNI-heavy work off virtual threads (use platform threads for native code paths).

## CLI quick reference

```bash
# Summary
jfr summary recording.jfr

# All GC events
jfr print --events jdk.GarbageCollection recording.jfr

# Custom events
jfr print --events 'trade.*,gc.sla.violation' recording.jfr

# Compare two recordings (workshop helper)
./workshop/scripts/compare-recordings.sh zgc.jfr g1.jfr

# Open in JMC
jmc -open recording.jfr
```

## Don't

- Don't conclude from a 60-second recording that you have a GC problem in production. Take a 10-minute recording first.
- Don't enable `jdk.ObjectAllocationInNewTLAB` with stack traces in production. It is a development-only tool.
- Don't change collector before reducing allocation. The cheapest fix is almost always upstream.
- Don't tune `MaxGCPauseMillis` to a value below your P99 measurement. G1 will respond by collecting more often, not faster.

