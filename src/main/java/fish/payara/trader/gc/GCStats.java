package fish.payara.trader.gc;

import java.util.List;

public class GCStats {
    private String gcName;
    private long collectionCount;
    private long collectionTime;
    private long lastPauseDuration;
    private List<Long> recentPauses;
    private PausePercentiles percentiles;
    private long totalMemory;
    private long usedMemory;
    private long freeMemory;

    public static class PausePercentiles {
        private long p50;
        private long p95;
        private long p99;
        private long p999;
        private long max;

        public PausePercentiles() {}

        public PausePercentiles(long p50, long p95, long p99, long p999, long max) {
            this.p50 = p50;
            this.p95 = p95;
            this.p99 = p99;
            this.p999 = p999;
            this.max = max;
        }

        public long getP50() { return p50; }
        public void setP50(long p50) { this.p50 = p50; }

        public long getP95() { return p95; }
        public void setP95(long p95) { this.p95 = p95; }

        public long getP99() { return p99; }
        public void setP99(long p99) { this.p99 = p99; }

        public long getP999() { return p999; }
        public void setP999(long p999) { this.p999 = p999; }

        public long getMax() { return max; }
        public void setMax(long max) { this.max = max; }
    }

    public GCStats() {}

    public String getGcName() { return gcName; }
    public void setGcName(String gcName) { this.gcName = gcName; }

    public long getCollectionCount() { return collectionCount; }
    public void setCollectionCount(long collectionCount) { this.collectionCount = collectionCount; }

    public long getCollectionTime() { return collectionTime; }
    public void setCollectionTime(long collectionTime) { this.collectionTime = collectionTime; }

    public long getLastPauseDuration() { return lastPauseDuration; }
    public void setLastPauseDuration(long lastPauseDuration) { this.lastPauseDuration = lastPauseDuration; }

    public List<Long> getRecentPauses() { return recentPauses; }
    public void setRecentPauses(List<Long> recentPauses) { this.recentPauses = recentPauses; }

    public PausePercentiles getPercentiles() { return percentiles; }
    public void setPercentiles(PausePercentiles percentiles) { this.percentiles = percentiles; }

    public long getTotalMemory() { return totalMemory; }
    public void setTotalMemory(long totalMemory) { this.totalMemory = totalMemory; }

    public long getUsedMemory() { return usedMemory; }
    public void setUsedMemory(long usedMemory) { this.usedMemory = usedMemory; }

    public long getFreeMemory() { return freeMemory; }
    public void setFreeMemory(long freeMemory) { this.freeMemory = freeMemory; }
}
