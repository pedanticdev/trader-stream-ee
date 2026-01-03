package fish.payara.trader.pressure;

public enum AllocationMode {
    OFF(0, 0, 0, ScenarioType.NONE, "No allocation"),

    STEADY_LOAD(200, // MB/sec allocation rate
                    512, // MB live set size
                    0, // No growth
                    ScenarioType.STEADY, "Steady 200 MB/sec allocation, 512 MB live set - Tests baseline GC behavior"),

    GROWING_HEAP(150, // MB/sec allocation rate
                    2048, // MB target live set
                    60, // seconds to reach target
                    ScenarioType.GROWING, "Growing live set 100 MB -> 2 GB over 60s - Tests mixed collection scaling"),

    PROMOTION_STORM(300, // MB/sec allocation rate
                    1024, // MB live set
                    0, // No growth
                    ScenarioType.PROMOTION, "High promotion rate (50% survival) - Tests old gen collection efficiency"),

    FRAGMENTATION(200, // MB/sec allocation rate
                    1024, // MB live set
                    0, // No growth
                    ScenarioType.FRAGMENTATION, "Small objects, random lifetimes - Tests compaction behavior"),

    CROSS_GEN_REFS(150, // MB/sec allocation rate
                    800, // MB live set in old gen
                    0, // No growth
                    ScenarioType.CROSS_REF, "Many old->young references - Tests remembered set overhead");

    private final int allocationRateMBPerSec;
    private final int liveSetSizeMB;
    private final int growthDurationSeconds;
    private final ScenarioType scenarioType;
    private final String description;

    AllocationMode(int allocationRateMBPerSec, int liveSetSizeMB, int growthDurationSeconds, ScenarioType scenarioType, String description) {
        this.allocationRateMBPerSec = allocationRateMBPerSec;
        this.liveSetSizeMB = liveSetSizeMB;
        this.growthDurationSeconds = growthDurationSeconds;
        this.scenarioType = scenarioType;
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

    public String getDescription() {
        return description;
    }
}
