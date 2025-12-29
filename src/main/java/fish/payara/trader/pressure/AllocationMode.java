package fish.payara.trader.pressure;

public enum AllocationMode {
  OFF(0, 0, "No additional allocation"),
  LOW(20, 10240, "2 MB/sec - Light pressure"),
  MEDIUM(200, 10240, "20 MB/sec - Moderate pressure"),
  HIGH(10000, 10240, "1 GB/sec - Heavy pressure"),
  EXTREME(40000, 10240, "4 GB/sec - Extreme pressure");

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
