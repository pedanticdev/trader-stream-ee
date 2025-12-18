package fish.payara.trader.pressure;

public enum AllocationMode {
    OFF(0, 0, "No additional allocation"),
    LOW(10, 1024, "10 KB/iteration - Light pressure"),
    MEDIUM(50, 1024, "50 KB/iteration - Moderate pressure"),
    HIGH(200, 1024, "200 KB/iteration - Heavy pressure"),
    EXTREME(1000, 1024, "1 MB/iteration - Extreme pressure");

    private final int allocationsPerIteration;
    private final int bytesPerAllocation;
    private final String description;

    AllocationMode(int allocationsPerIteration, int bytesPerAllocation, String description) {
        this.allocationsPerIteration = allocationsPerIteration;
        this.bytesPerAllocation = bytesPerAllocation;
        this.description = description;
    }

    public int getAllocationsPerIteration() {
        return allocationsPerIteration;
    }

    public int getBytesPerAllocation() {
        return bytesPerAllocation;
    }

    public String getDescription() {
        return description;
    }

    public long getBytesPerSecond() {
        return (long) allocationsPerIteration * bytesPerAllocation * 10; // 10 iterations/sec
    }
}
