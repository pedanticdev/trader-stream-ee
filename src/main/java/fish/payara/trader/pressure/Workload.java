package fish.payara.trader.pressure;

public interface Workload {
    String name();

    void execute(int iterations);

    long bytesAllocated();

    long operationsCompleted();

    void reset();
}
