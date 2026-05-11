# GC pathology catalogue

The four patterns Module 2 introduced, restated as a take-home reference. Each entry includes the runtime signature you can see in JFR, the root cause in plain language, and the fix order — cheapest mitigation first.

## 1. Promotion storm

**JFR signature**
- `jdk.PromoteObjectOutsidePLAB` count climbs across the recording.
- `jdk.GCHeapSummary` shows old-gen growing monotonically.
- Mixed collections start firing in the second half of the recording and lengthen.
- High allocation rate (>200 MB/s) AND high survival ratio (>30%).

**Root cause**
Objects survive young-gen collection faster than the young gen can absorb. Survivor space spills to old gen via promotion; old gen fills; G1 must run mixed collections to reclaim. Pause time scales with old-gen live data because mixed collections are stop-the-world.

**Fix order**
1. Reduce allocation in the surviving code path. Object pooling, primitive arrays, flyweights.
2. Increase young-gen capacity so more garbage dies before promotion (`-XX:NewRatio`, region sizing).
3. Switch to a concurrent collector for old-gen work (ZGC, Shenandoah, C4).

**Real-world analogue:** caches with TTL longer than the young-GC interval. The cache entries promote even though they will be evicted soon.

## 2. Heap fragmentation

**JFR signature**
- `jdk.ObjectAllocationSample` dominated by small objects (<1 KB).
- `jdk.G1HeapRegionTypeChange` events frequent.
- Pause time scales with region count being evacuated, not with heap size.
- `jdk.GCPhasePauseLevel2` shows "Object Copy" as the dominant subphase.

**Root cause**
Long-lived application with many distinct object sizes. Free regions accumulate but each contains small unrelated objects, so the collector spends pause time copying many tiny objects instead of evacuating a few big ones.

**Fix order**
1. Consolidate small allocations into arrays or off-heap buffers.
2. Increase `-XX:G1HeapRegionSize` so fewer regions hold equivalent data (larger evacuation chunks).
3. Switch to a compacting concurrent collector. C4 compacts continuously; ZGC defragments less aggressively but tolerates fragmentation better.

**Real-world analogue:** long-lived applications with verbose logging frameworks, JSON parsing, or many short-lived strings.

## 3. Cross-generational reference pressure

**JFR signature**
- `jdk.GCPhasePauseLevel1` events with name `Update RS` or `Scan RS` dominate pause time (often 60–80% per pause).
- `jdk.ExecutionSample` stacks point into `G1BarrierSet::write_ref_field_post` or similar.
- Old-gen objects holding references to young-gen objects, mutated frequently.

**Root cause**
G1 maintains a remembered set: per-region metadata of all references pointing into that region from elsewhere. Every write to a reference field in an old-gen object that points to a young-gen object triggers the write barrier, which marks the card-table entry and adds to the remembered set. The next collection has to scan all those entries before evacuating.

**Fix order**
1. Make old-gen holders immutable or copy-on-write.
2. Reduce write rate to old-gen mutable fields.
3. Switch to a collector without remembered sets. C4 has no remembered set; ZGC uses load barriers instead.

**Real-world analogue:** mutable singletons (caches, registries) that hold references to recent request objects; long-lived ConcurrentHashMaps that get many puts.

## 4. Evacuation failure

**JFR signature**
- `jdk.EvacuationFailed` events present.
- Heap is at `-Xmx`; survivor and old gen are both near full.
- Often followed by a full GC. JDK 21's Tenuring Anywhere change reduces but does not eliminate this consequence.

**Root cause**
When G1 begins a collection it must copy survivors out of the source region. If no destination region has space, evacuation fails. Failed evacuation typically degrades into a full collection to recover.

**Fix order**
1. Increase `-Xmx` so the heap can absorb the allocation burst.
2. Reduce the allocation rate (especially of large objects).
3. Lower `-XX:InitiatingHeapOccupancyPercent` so concurrent marking starts earlier and keeps old-gen ahead of demand.

**Real-world analogue:** allocation bursts during startup, batch import jobs, or sudden traffic spikes against a heap tuned for normal load.

## How these interact

The four pathologies are not independent. A growing heap (PROMOTION_STORM) eventually fragments, then a fragmented heap with high mutation creates remembered-set pressure, then any of those can culminate in evacuation failure. The triage tree in `workshop/analysis-checklist.md` helps you find the leading symptom; once fixed, the downstream symptoms often resolve on their own.

## Decision framework: when to reduce allocation vs. when to change collector

| Symptom | Try this first | Then this | Then this |
|---|---|---|---|
| P50 pauses are too high | Reduce allocation rate | Size young gen larger | Profile and pool the dominant allocator |
| P99 pauses are too high | Look for promotion storms in JFR | Tune `MaxGCPauseMillis` upward (a smaller target makes G1 collect MORE often, not faster) | Consider a concurrent collector |
| Max pauses are catastrophic | `EvacuationFailed`? Increase `-Xmx` | `Update RS`? Make mutable singletons immutable | Switch to concurrent collector |
| All collectors struggle equally | The allocation rate is the bottleneck | Architectural change required (zero-copy, off-heap) | Consider whether Java is the right tool here |

The hardest call is "reduce allocation or change collector." Allocation reduction is almost always cheaper in production than changing JVM, until you have already optimised the hot paths and the residual GC cost is still material. At that point a concurrent collector is the right answer.
