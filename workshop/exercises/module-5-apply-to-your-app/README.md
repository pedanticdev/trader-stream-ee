# Module 5: applying JFR to your own applications

This module is a take-home, not an in-room exercise. Here you decide what to instrument in your own application and how.

## Pattern recognition

You have seen four pathologies in the workshop:

|        Pathology        |                                    Telltale JFR signature                                    |
|-------------------------|----------------------------------------------------------------------------------------------|
| Promotion storm         | Rising `jdk.PromoteObjectOutsidePLAB` count; old gen grows; mixed-collection pauses lengthen |
| Fragmentation           | `jdk.G1HeapRegionTypeChange` spikes; small object sizes in `jdk.ObjectAllocationSample`      |
| Cross-generational refs | `jdk.GCPhasePauseLevel1 → Update RS` dominates pause time                                    |
| Evacuation failure      | `jdk.EvacuationFailed` events; under heap pressure a full GC may follow (JDK 21 reduces this likelihood) |

When you take a production JFR recording home, the [analysis checklist](../../analysis-checklist.md) walks the same triage.

## Event templates

Three reusable event classes in [`event-templates/`](./event-templates/) that map to most application instrumentation needs:

- `LatencyEvent` - wrap any function whose tail latency matters. Uses `begin()/commit()` for automatic duration.
- `ThrottleEvent` - emit when a circuit breaker opens, a rate limit is hit, or backpressure activates.
- `ResourceEvent` - emit when a finite resource (connection pool slot, file handle, cache eviction) is reserved or released.

Copy them into your project, rename them, change the `@Name` annotations to your domain identifiers, and start instrumenting.

## Production-safe JFR

Three rules. The full reference is [`production-jfr-flags.md`](./production-jfr-flags.md).

1. **Use the default settings profile.** `-XX:StartFlightRecording=settings=default` keeps overhead at ~1%. The workshop profile is fine for development; do not run it in production.
2. **Cap recording size and age.** `maxsize=1g,maxage=24h` is a sensible default for a circular recording.
3. **Disable stack traces on hot-path custom events.** `@StackTrace(false)` is the single most important annotation for high-volume events.

## Q&A

Bring your own recording. The last few minutes of the workshop are for walking through real attendee recordings together.
