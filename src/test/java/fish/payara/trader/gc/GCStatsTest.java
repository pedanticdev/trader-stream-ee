package fish.payara.trader.gc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for GCStats data class */
@DisplayName("GCStats Tests")
class GCStatsTest {

    @Nested
    @DisplayName("Constructor and Basic Properties Tests")
    class ConstructorAndBasicPropertiesTests {

        @Test
        @DisplayName("Should create GCStats with default constructor")
        void shouldCreateGCStatsWithDefaultConstructor() {
            GCStats stats = new GCStats();

            assertNotNull(stats, "GCStats should not be null");
            assertNull(stats.getGcName(), "GC name should be null by default");
            assertEquals(0L, stats.getCollectionCount(), "Collection count should default to 0");
            assertEquals(0L, stats.getCollectionTime(), "Collection time should default to 0");
            assertEquals(0L, stats.getLastPauseDuration(), "Last pause duration should default to 0");
            assertNull(stats.getRecentPauses(), "Recent pauses should be null by default");
            assertNull(stats.getPercentiles(), "Percentiles should be null by default");
            assertEquals(0L, stats.getTotalMemory(), "Total memory should default to 0");
            assertEquals(0L, stats.getUsedMemory(), "Used memory should default to 0");
            assertEquals(0L, stats.getFreeMemory(), "Free memory should default to 0");
        }
    }

    @Nested
    @DisplayName("GC Name Tests")
    class GCNameTests {

        @Test
        @DisplayName("Should set and get GC name correctly")
        void shouldSetAndGetGCNameCorrectly() {
            GCStats stats = new GCStats();
            String gcName = "G1 Young Generation";

            stats.setGcName(gcName);
            assertEquals(gcName, stats.getGcName(), "GC name should be set and retrieved correctly");
        }

        @Test
        @DisplayName("Should handle null GC name")
        void shouldHandleNullGCName() {
            GCStats stats = new GCStats();

            assertDoesNotThrow(() -> stats.setGcName(null), "Setting null GC name should not throw");
            assertNull(stats.getGcName(), "GC name should be null");
        }

        @Test
        @DisplayName("Should handle empty GC name")
        void shouldHandleEmptyGCName() {
            GCStats stats = new GCStats();
            String emptyName = "";

            stats.setGcName(emptyName);
            assertEquals(emptyName, stats.getGcName(), "Empty GC name should be handled");
        }

        @Test
        @DisplayName("Should handle special characters in GC name")
        void shouldHandleSpecialCharactersInGCName() {
            GCStats stats = new GCStats();
            String specialName = "G1/Young (Test) @#$%^&*()";

            stats.setGcName(specialName);
            assertEquals(specialName, stats.getGcName(), "Special characters should be preserved");
        }
    }

    @Nested
    @DisplayName("Collection Statistics Tests")
    class CollectionStatisticsTests {

        @Test
        @DisplayName("Should set and get collection count correctly")
        void shouldSetAndGetCollectionCountCorrectly() {
            GCStats stats = new GCStats();
            long collectionCount = 12345L;

            stats.setCollectionCount(collectionCount);
            assertEquals(collectionCount, stats.getCollectionCount(), "Collection count should be set and retrieved correctly");
        }

        @Test
        @DisplayName("Should handle zero collection count")
        void shouldHandleZeroCollectionCount() {
            GCStats stats = new GCStats();

            stats.setCollectionCount(0L);
            assertEquals(0L, stats.getCollectionCount(), "Zero collection count should be handled");
        }

        @Test
        @DisplayName("Should handle negative collection count")
        void shouldHandleNegativeCollectionCount() {
            GCStats stats = new GCStats();
            long negativeCount = -100L;

            stats.setCollectionCount(negativeCount);
            assertEquals(negativeCount, stats.getCollectionCount(), "Negative collection count should be handled");
        }

        @Test
        @DisplayName("Should handle very large collection count")
        void shouldHandleVeryLargeCollectionCount() {
            GCStats stats = new GCStats();
            long largeCount = Long.MAX_VALUE;

            stats.setCollectionCount(largeCount);
            assertEquals(largeCount, stats.getCollectionCount(), "Very large collection count should be handled");
        }

        @Test
        @DisplayName("Should set and get collection time correctly")
        void shouldSetAndGetCollectionTimeCorrectly() {
            GCStats stats = new GCStats();
            long collectionTime = 98765L;

            stats.setCollectionTime(collectionTime);
            assertEquals(collectionTime, stats.getCollectionTime(), "Collection time should be set and retrieved correctly");
        }

        @Test
        @DisplayName("Should handle zero collection time")
        void shouldHandleZeroCollectionTime() {
            GCStats stats = new GCStats();

            stats.setCollectionTime(0L);
            assertEquals(0L, stats.getCollectionTime(), "Zero collection time should be handled");
        }

        @Test
        @DisplayName("Should handle negative collection time")
        void shouldHandleNegativeCollectionTime() {
            GCStats stats = new GCStats();
            long negativeTime = -500L;

            stats.setCollectionTime(negativeTime);
            assertEquals(negativeTime, stats.getCollectionTime(), "Negative collection time should be handled");
        }
    }

    @Nested
    @DisplayName("Pause Duration Tests")
    class PauseDurationTests {

        @Test
        @DisplayName("Should set and get last pause duration correctly")
        void shouldSetAndGetLastPauseDurationCorrectly() {
            GCStats stats = new GCStats();
            long pauseDuration = 150L;

            stats.setLastPauseDuration(pauseDuration);
            assertEquals(pauseDuration, stats.getLastPauseDuration(), "Last pause duration should be set and retrieved correctly");
        }

        @Test
        @DisplayName("Should handle zero pause duration")
        void shouldHandleZeroPauseDuration() {
            GCStats stats = new GCStats();

            stats.setLastPauseDuration(0L);
            assertEquals(0L, stats.getLastPauseDuration(), "Zero pause duration should be handled");
        }

        @Test
        @DisplayName("Should handle very long pause duration")
        void shouldHandleVeryLongPauseDuration() {
            GCStats stats = new GCStats();
            long longPause = 30000L; // 30 seconds

            stats.setLastPauseDuration(longPause);
            assertEquals(longPause, stats.getLastPauseDuration(), "Very long pause duration should be handled");
        }

        @Test
        @DisplayName("Should handle microsecond-level pause duration")
        void shouldHandleMicrosecondLevelPauseDuration() {
            GCStats stats = new GCStats();
            long microPause = 1L; // 1 millisecond (micro in practice)

            stats.setLastPauseDuration(microPause);
            assertEquals(microPause, stats.getLastPauseDuration(), "Microsecond-level pause duration should be handled");
        }
    }

    @Nested
    @DisplayName("Recent Pauses Tests")
    class RecentPausesTests {

        @Test
        @DisplayName("Should set and get recent pauses list correctly")
        void shouldSetAndGetRecentPausesListCorrectly() {
            GCStats stats = new GCStats();
            List<Long> pauses = Arrays.asList(10L, 25L, 15L, 30L, 12L);

            stats.setRecentPauses(pauses);
            assertEquals(pauses, stats.getRecentPauses(), "Recent pauses list should be set and retrieved correctly");
        }

        @Test
        @DisplayName("Should handle empty recent pauses list")
        void shouldHandleEmptyRecentPausesList() {
            GCStats stats = new GCStats();
            List<Long> emptyList = List.of();

            stats.setRecentPauses(emptyList);
            assertEquals(emptyList, stats.getRecentPauses(), "Empty recent pauses list should be handled");
        }

        @Test
        @DisplayName("Should handle null recent pauses list")
        void shouldHandleNullRecentPausesList() {
            GCStats stats = new GCStats();

            assertDoesNotThrow(() -> stats.setRecentPauses(null), "Setting null recent pauses should not throw");
            assertNull(stats.getRecentPauses(), "Recent pauses should be null");
        }

        @Test
        @DisplayName("Should handle recent pauses with zero values")
        void shouldHandleRecentPausesWithZeroValues() {
            GCStats stats = new GCStats();
            List<Long> zeros = Arrays.asList(0L, 0L, 0L, 0L);

            stats.setRecentPauses(zeros);
            assertEquals(zeros, stats.getRecentPauses(), "Zero pause values should be handled");
        }

        @Test
        @DisplayName("Should handle recent pauses with mixed values")
        void shouldHandleRecentPausesWithMixedValues() {
            GCStats stats = new GCStats();
            List<Long> mixedValues = Arrays.asList(5L, 0L, 1000L, 1L, 500L);

            stats.setRecentPauses(mixedValues);
            assertEquals(mixedValues, stats.getRecentPauses(), "Mixed pause values should be handled");
        }
    }

    @Nested
    @DisplayName("Percentiles Tests")
    class PercentilesTests {

        @Test
        @DisplayName("Should set and get percentiles correctly")
        void shouldSetAndGetPercentilesCorrectly() {
            GCStats stats = new GCStats();
            GCStats.PausePercentiles percentiles = new GCStats.PausePercentiles(10L, 25L, 50L, 100L, 200L);

            stats.setPercentiles(percentiles);
            assertEquals(percentiles, stats.getPercentiles(), "Percentiles should be set and retrieved correctly");
        }

        @Test
        @DisplayName("Should handle null percentiles")
        void shouldHandleNullPercentiles() {
            GCStats stats = new GCStats();

            assertDoesNotThrow(() -> stats.setPercentiles(null), "Setting null percentiles should not throw");
            assertNull(stats.getPercentiles(), "Percentiles should be null");
        }

        @Test
        @DisplayName("Should handle all zero percentile values")
        void shouldHandleAllZeroPercentileValues() {
            GCStats stats = new GCStats();
            GCStats.PausePercentiles zeroPercentiles = new GCStats.PausePercentiles(0L, 0L, 0L, 0L, 0L);

            stats.setPercentiles(zeroPercentiles);
            assertEquals(0L, stats.getPercentiles().getP50(), "P50 should be zero");
            assertEquals(0L, stats.getPercentiles().getP95(), "P95 should be zero");
            assertEquals(0L, stats.getPercentiles().getP99(), "P99 should be zero");
            assertEquals(0L, stats.getPercentiles().getP999(), "P999 should be zero");
            assertEquals(0L, stats.getPercentiles().getMax(), "Max should be zero");
        }

        @Test
        @DisplayName("Should handle percentile with constructor")
        void shouldHandlePercentileWithConstructor() {
            long p50 = 15L, p95 = 50L, p99 = 120L, p999 = 300L, max = 500L;
            GCStats.PausePercentiles percentiles = new GCStats.PausePercentiles(p50, p95, p99, p999, max);

            assertEquals(p50, percentiles.getP50(), "P50 should match constructor value");
            assertEquals(p95, percentiles.getP95(), "P95 should match constructor value");
            assertEquals(p99, percentiles.getP99(), "P99 should match constructor value");
            assertEquals(p999, percentiles.getP999(), "P999 should match constructor value");
            assertEquals(max, percentiles.getMax(), "Max should match constructor value");
        }

        @Test
        @DisplayName("Should handle percentile setters and getters")
        void shouldHandlePercentileSettersAndGetters() {
            GCStats.PausePercentiles percentiles = new GCStats.PausePercentiles();

            long p50 = 25L, p95 = 75L, p99 = 150L, p999 = 400L, max = 800L;

            percentiles.setP50(p50);
            percentiles.setP95(p95);
            percentiles.setP99(p99);
            percentiles.setP999(p999);
            percentiles.setMax(max);

            assertEquals(p50, percentiles.getP50(), "P50 should be set correctly");
            assertEquals(p95, percentiles.getP95(), "P95 should be set correctly");
            assertEquals(p99, percentiles.getP99(), "P99 should be set correctly");
            assertEquals(p999, percentiles.getP999(), "P999 should be set correctly");
            assertEquals(max, percentiles.getMax(), "Max should be set correctly");
        }
    }

    @Nested
    @DisplayName("Memory Statistics Tests")
    class MemoryStatisticsTests {

        @Test
        @DisplayName("Should set and get total memory correctly")
        void shouldSetAndGetTotalMemoryCorrectly() {
            GCStats stats = new GCStats();
            long totalMemory = 1024L * 1024L * 1024L; // 1GB

            stats.setTotalMemory(totalMemory);
            assertEquals(totalMemory, stats.getTotalMemory(), "Total memory should be set and retrieved correctly");
        }

        @Test
        @DisplayName("Should set and get used memory correctly")
        void shouldSetAndGetUsedMemoryCorrectly() {
            GCStats stats = new GCStats();
            long usedMemory = 512L * 1024L * 1024L; // 512MB

            stats.setUsedMemory(usedMemory);
            assertEquals(usedMemory, stats.getUsedMemory(), "Used memory should be set and retrieved correctly");
        }

        @Test
        @DisplayName("Should set and get free memory correctly")
        void shouldSetAndGetFreeMemoryCorrectly() {
            GCStats stats = new GCStats();
            long freeMemory = 256L * 1024L * 1024L; // 256MB

            stats.setFreeMemory(freeMemory);
            assertEquals(freeMemory, stats.getFreeMemory(), "Free memory should be set and retrieved correctly");
        }

        @Test
        @DisplayName("Should maintain memory relationship: total >= used")
        void shouldMaintainMemoryRelationshipTotalGTEUsed() {
            GCStats stats = new GCStats();
            long total = 1000L;
            long used = 750L;

            stats.setTotalMemory(total);
            stats.setUsedMemory(used);

            assertTrue(stats.getTotalMemory() >= stats.getUsedMemory(), "Total memory should be greater than or equal to used memory");
        }

        @Test
        @DisplayName("Should maintain memory relationship: total >= free")
        void shouldMaintainMemoryRelationshipTotalGTFree() {
            GCStats stats = new GCStats();
            long total = 1000L;
            long free = 300L;

            stats.setTotalMemory(total);
            stats.setFreeMemory(free);

            assertTrue(stats.getTotalMemory() >= stats.getFreeMemory(), "Total memory should be greater than or equal to free memory");
        }

        @Test
        @DisplayName("Should maintain memory relationship: total >= used + free")
        void shouldMaintainMemoryRelationshipTotalGEUsedPlusFree() {
            GCStats stats = new GCStats();
            long total = 1000L;
            long used = 600L;
            long free = 400L;

            stats.setTotalMemory(total);
            stats.setUsedMemory(used);
            stats.setFreeMemory(free);

            assertTrue(stats.getTotalMemory() >= stats.getUsedMemory() + stats.getFreeMemory(),
                            "Total memory should be greater than or equal to used + free memory");
        }

        @Test
        @DisplayName("Should handle zero memory values")
        void shouldHandleZeroMemoryValues() {
            GCStats stats = new GCStats();

            stats.setTotalMemory(0L);
            stats.setUsedMemory(0L);
            stats.setFreeMemory(0L);

            assertEquals(0L, stats.getTotalMemory(), "Total memory should handle zero");
            assertEquals(0L, stats.getUsedMemory(), "Used memory should handle zero");
            assertEquals(0L, stats.getFreeMemory(), "Free memory should handle zero");
        }

        @Test
        @DisplayName("Should handle large memory values")
        void shouldHandleLargeMemoryValues() {
            GCStats stats = new GCStats();
            long largeValue = Long.MAX_VALUE / 2; // Half of max long value

            stats.setTotalMemory(largeValue);
            stats.setUsedMemory(largeValue / 2);
            stats.setFreeMemory(largeValue / 4);

            assertEquals(largeValue, stats.getTotalMemory(), "Large total memory should be handled");
            assertEquals(largeValue / 2, stats.getUsedMemory(), "Large used memory should be handled");
            assertEquals(largeValue / 4, stats.getFreeMemory(), "Large free memory should be handled");
        }
    }

    @Nested
    @DisplayName("Consistency Tests")
    class ConsistencyTests {

        @Test
        @DisplayName("Should maintain consistent state across multiple property changes")
        void shouldMaintainConsistentStateAcrossMultiplePropertyChanges() {
            GCStats stats = new GCStats();

            // Set all properties
            stats.setGcName("Test GC");
            stats.setCollectionCount(100L);
            stats.setCollectionTime(5000L);
            stats.setLastPauseDuration(50L);
            stats.setRecentPauses(List.of(10L, 20L, 30L));
            stats.setPercentiles(new GCStats.PausePercentiles(15L, 25L, 40L, 60L, 100L));
            stats.setTotalMemory(1024L);
            stats.setUsedMemory(512L);
            stats.setFreeMemory(256L);

            // Verify all properties are still correct
            assertEquals("Test GC", stats.getGcName());
            assertEquals(100L, stats.getCollectionCount());
            assertEquals(5000L, stats.getCollectionTime());
            assertEquals(50L, stats.getLastPauseDuration());
            assertEquals(List.of(10L, 20L, 30L), stats.getRecentPauses());
            assertEquals(15L, stats.getPercentiles().getP50());
            assertEquals(1024L, stats.getTotalMemory());
            assertEquals(512L, stats.getUsedMemory());
            assertEquals(256L, stats.getFreeMemory());
        }

        @Test
        @DisplayName("Should handle independent property modifications")
        void shouldHandleIndependentPropertyModifications() {
            GCStats stats = new GCStats();

            // Set initial values
            stats.setCollectionCount(100L);
            stats.setCollectionTime(5000L);

            // Modify one property
            stats.setCollectionCount(200L);

            // Verify only the modified property changed
            assertEquals(200L, stats.getCollectionCount(), "Modified property should change");
            assertEquals(5000L, stats.getCollectionTime(), "Unmodified property should remain unchanged");
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle Long.MAX_VALUE for all numeric properties")
        void shouldHandleLongMaxValueForAllNumericProperties() {
            GCStats stats = new GCStats();
            long maxValue = Long.MAX_VALUE;

            stats.setCollectionCount(maxValue);
            stats.setCollectionTime(maxValue);
            stats.setLastPauseDuration(maxValue);
            stats.setTotalMemory(maxValue);
            stats.setUsedMemory(maxValue);
            stats.setFreeMemory(maxValue);

            assertEquals(maxValue, stats.getCollectionCount());
            assertEquals(maxValue, stats.getCollectionTime());
            assertEquals(maxValue, stats.getLastPauseDuration());
            assertEquals(maxValue, stats.getTotalMemory());
            assertEquals(maxValue, stats.getUsedMemory());
            assertEquals(maxValue, stats.getFreeMemory());
        }

        @Test
        @DisplayName("Should handle Long.MIN_VALUE for all numeric properties")
        void shouldHandleLongMinValueForAllNumericProperties() {
            GCStats stats = new GCStats();
            long minValue = Long.MIN_VALUE;

            stats.setCollectionCount(minValue);
            stats.setCollectionTime(minValue);
            stats.setLastPauseDuration(minValue);
            stats.setTotalMemory(minValue);
            stats.setUsedMemory(minValue);
            stats.setFreeMemory(minValue);

            assertEquals(minValue, stats.getCollectionCount());
            assertEquals(minValue, stats.getCollectionTime());
            assertEquals(minValue, stats.getLastPauseDuration());
            assertEquals(minValue, stats.getTotalMemory());
            assertEquals(minValue, stats.getUsedMemory());
            assertEquals(minValue, stats.getFreeMemory());
        }
    }
}
