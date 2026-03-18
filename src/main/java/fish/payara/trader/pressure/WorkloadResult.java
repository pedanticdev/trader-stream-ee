package fish.payara.trader.pressure;

public record WorkloadResult(String workloadName, long bytesAllocated, long operationsCompleted, long durationNanos, double opsPerSecond, double mbPerSecond) {

    public static WorkloadResult of(String workloadName, long bytesAllocated, long operationsCompleted, long durationNanos) {
        double seconds = durationNanos / 1_000_000_000.0;
        double opsPerSecond = seconds > 0 ? operationsCompleted / seconds : 0.0;
        double mbPerSecond = seconds > 0 ? (bytesAllocated / (1024.0 * 1024.0)) / seconds : 0.0;
        return new WorkloadResult(workloadName, bytesAllocated, operationsCompleted, durationNanos, opsPerSecond, mbPerSecond);
    }
}
