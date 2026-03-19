package fish.payara.trader.pressure;

public enum AllocationMode {
    OFF(0, 0, 0, ScenarioType.NONE, WorkloadType.NONE, "No allocation"),

    STEADY_LOAD(200, // MB/sec allocation rate
                    512, // MB live set size
                    0, // No growth
                    ScenarioType.STEADY, WorkloadType.NONE, "Steady 200 MB/sec allocation, 512 MB live set - Tests baseline GC behavior"),

    GROWING_HEAP(150, // MB/sec allocation rate
                    2048, // MB target live set
                    60, // seconds to reach target
                    ScenarioType.GROWING, WorkloadType.NONE, "Growing live set 100 MB -> 2 GB over 60s - Tests mixed collection scaling"),

    PROMOTION_STORM(300, // MB/sec allocation rate
                    1024, // MB live set
                    0, // No growth
                    ScenarioType.PROMOTION, WorkloadType.NONE, "High promotion rate (50% survival) - Tests old gen collection efficiency"),

    FRAGMENTATION(200, // MB/sec allocation rate
                    1024, // MB live set
                    0, // No growth
                    ScenarioType.FRAGMENTATION, WorkloadType.NONE, "Small objects, random lifetimes - Tests compaction behavior"),

    CROSS_GEN_REFS(150, // MB/sec allocation rate
                    800, // MB live set in old gen
                    0, // No growth
                    ScenarioType.CROSS_REF, WorkloadType.NONE, "Many old->young references - Tests remembered set overhead"),

    COMPRESSION_CPU(300, 1024, 0, ScenarioType.NONE, WorkloadType.COMPRESSION, "Gzip compress/decompress workload - Tests GC under CPU-intensive compression"),

    SERIALIZATION_CPU(250, 1024, 0, ScenarioType.NONE, WorkloadType.SERIALIZATION,
                    "Jakarta JSON serialization/deserialization - Tests GC under object graph materialization"),

    CRYPTO_CPU(200, 1024, 0, ScenarioType.NONE, WorkloadType.CRYPTO, "SHA-256, HMAC-SHA256, AES-GCM encrypt/decrypt - Tests GC under cryptographic operations"),

    COLLECTION_CPU(300, 1024, 0, ScenarioType.NONE, WorkloadType.COLLECTION, "HashMap/TreeMap insert/lookup/remove - Tests GC under collection churn"),

    STRING_CPU(350, 1024, 0, ScenarioType.NONE, WorkloadType.STRING,
                    "Regex, substring, StringBuilder, String.intern - Tests GC under string interning pressure"),

    TRADING_MATCHING(400, 512, 0, ScenarioType.NONE, WorkloadType.TRADING_MATCHING,
                    "High-frequency order matching - Tests GC under Order/Execution object churn with matching engine"),

    TECHNICAL_ANALYSIS(300, 256, 0, ScenarioType.NONE, WorkloadType.TECHNICAL_ANALYSIS,
                    "Continuous ta4j indicator computation - Tests GC under indicator object allocation (SMA, EMA, RSI, MACD, Bollinger, ATR)");

    private final int allocationRateMBPerSec;
    private final int liveSetSizeMB;
    private final int growthDurationSeconds;
    private final ScenarioType scenarioType;
    private final WorkloadType workloadType;
    private final String description;

    AllocationMode(int allocationRateMBPerSec, int liveSetSizeMB, int growthDurationSeconds, ScenarioType scenarioType, WorkloadType workloadType,
                    String description) {
        this.allocationRateMBPerSec = allocationRateMBPerSec;
        this.liveSetSizeMB = liveSetSizeMB;
        this.growthDurationSeconds = growthDurationSeconds;
        this.scenarioType = scenarioType;
        this.workloadType = workloadType;
        this.description = description;
    }

    public int getAllocationRateMBPerSec() {
        return allocationRateMBPerSec;
    }

    public int getLiveSetSizeMB() {
        return liveSetSizeMB;
    }

    public int getGrowthDurationSeconds() {
        return growthDurationSeconds;
    }

    public ScenarioType getScenarioType() {
        return scenarioType;
    }

    public WorkloadType getWorkloadType() {
        return workloadType;
    }

    public String getDescription() {
        return description;
    }
}
