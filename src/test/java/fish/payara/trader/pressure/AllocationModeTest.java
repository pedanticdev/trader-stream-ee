package fish.payara.trader.pressure;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Unit tests for AllocationMode enum */
@DisplayName("AllocationMode Tests")
class AllocationModeTest {

    @Nested
    @DisplayName("Enum Values Tests")
    class EnumValuesTests {

        @Test
        @DisplayName("Should have all required allocation modes")
        void shouldHaveAllRequiredAllocationModes() {
            AllocationMode[] modes = AllocationMode.values();
            assertEquals(13, modes.length, "Should have exactly 13 allocation modes");

            assertTrue(containsMode(modes, AllocationMode.OFF), "Should contain OFF mode");
            assertTrue(containsMode(modes, AllocationMode.STEADY_LOAD), "Should contain STEADY_LOAD mode");
            assertTrue(containsMode(modes, AllocationMode.INTRADAY_POSITION_GROWTH), "Should contain INTRADAY_POSITION_GROWTH mode");
            assertTrue(containsMode(modes, AllocationMode.EARNINGS_SPIKE), "Should contain EARNINGS_SPIKE mode");
            assertTrue(containsMode(modes, AllocationMode.MULTI_VENUE_QUOTE_CHURN), "Should contain MULTI_VENUE_QUOTE_CHURN mode");
            assertTrue(containsMode(modes, AllocationMode.LONG_HORIZON_POSITION_BOOK), "Should contain LONG_HORIZON_POSITION_BOOK mode");
        }
    }

    @Nested
    @DisplayName("Scenario Properties Tests")
    class ScenarioPropertiesTests {

        @ParameterizedTest
        @EnumSource(AllocationMode.class)
        @DisplayName("Should have non-null scenario type")
        void shouldHaveNonNullScenarioType(AllocationMode mode) {
            assertNotNull(mode.getScenarioType(), "ScenarioType should not be null");
        }

        @Test
        @DisplayName("Should have correct scenario types")
        void shouldHaveCorrectScenarioTypes() {
            assertEquals(ScenarioType.NONE, AllocationMode.OFF.getScenarioType());
            assertEquals(ScenarioType.STEADY, AllocationMode.STEADY_LOAD.getScenarioType());
            assertEquals(ScenarioType.INTRADAY_GROWTH, AllocationMode.INTRADAY_POSITION_GROWTH.getScenarioType());
            assertEquals(ScenarioType.EARNINGS_SPIKE, AllocationMode.EARNINGS_SPIKE.getScenarioType());
            assertEquals(ScenarioType.QUOTE_CHURN, AllocationMode.MULTI_VENUE_QUOTE_CHURN.getScenarioType());
            assertEquals(ScenarioType.POSITION_BOOK, AllocationMode.LONG_HORIZON_POSITION_BOOK.getScenarioType());
        }

        @ParameterizedTest
        @EnumSource(AllocationMode.class)
        @DisplayName("Should have valid allocation rates")
        void shouldHaveValidAllocationRates(AllocationMode mode) {
            assertTrue(mode.getAllocationRateMBPerSec() >= 0, "Allocation rate should be non-negative");
        }

        @ParameterizedTest
        @EnumSource(AllocationMode.class)
        @DisplayName("Should have meaningful descriptions")
        void shouldHaveMeaningfulDescriptions(AllocationMode mode) {
            String description = mode.getDescription();

            assertNotNull(description, "Description should not be null");
            assertFalse(description.trim().isEmpty(), "Description should not be empty");
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle OFF mode correctly")
        void shouldHandleOffModeCorrectly() {
            assertEquals(0, AllocationMode.OFF.getAllocationRateMBPerSec());
            assertEquals(0, AllocationMode.OFF.getLiveSetSizeMB());
            assertEquals(ScenarioType.NONE, AllocationMode.OFF.getScenarioType());
        }

        @ParameterizedTest
        @ValueSource(strings = {"OFF", "STEADY_LOAD", "INTRADAY_POSITION_GROWTH", "EARNINGS_SPIKE"})
        @DisplayName("Should handle enum valueOf correctly")
        void shouldHandleEnumValueOfCorrectly(String modeName) {
            assertDoesNotThrow(() -> {
                AllocationMode mode = AllocationMode.valueOf(modeName);
                assertNotNull(mode, "Mode should not be null");
                assertEquals(modeName, mode.name(), "Mode name should match");
            }, "valueOf should work for all valid mode names");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for invalid mode names")
        void shouldThrowIllegalArgumentExceptionForInvalidModeNames() {
            String[] invalidNames = {"INVALID", "low", "high", "", " ", null};

            for (String invalidName : invalidNames) {
                if (invalidName != null) {
                    assertThrows(IllegalArgumentException.class, () -> {
                        AllocationMode.valueOf(invalidName);
                    }, () -> String.format("Should throw for invalid mode name: '%s'", invalidName));
                }
            }
        }
    }

    /** Helper method to check if array contains specific mode */
    private boolean containsMode(AllocationMode[] modes, AllocationMode targetMode) {
        for (AllocationMode mode : modes) {
            if (mode == targetMode) {
                return true;
            }
        }
        return false;
    }
}
