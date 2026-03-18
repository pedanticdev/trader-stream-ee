package fish.payara.trader.pressure;

public record WorkloadConfig(int payloadSizeBytes, int iterationsPerCycle, int threadsPerWorkload) {

    public WorkloadConfig() {
        this(65536, 100, 4);
    }

    public WorkloadConfig {
        if (payloadSizeBytes < 1) {
            throw new IllegalArgumentException("payloadSizeBytes must be positive");
        }
        if (iterationsPerCycle < 1) {
            throw new IllegalArgumentException("iterationsPerCycle must be positive");
        }
        if (threadsPerWorkload < 1) {
            throw new IllegalArgumentException("threadsPerWorkload must be positive");
        }
    }
}
