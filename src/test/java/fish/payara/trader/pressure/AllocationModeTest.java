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
      assertEquals(6, modes.length, "Should have exactly 6 allocation modes");

      assertTrue(containsMode(modes, AllocationMode.OFF), "Should contain OFF mode");
      assertTrue(
          containsMode(modes, AllocationMode.STEADY_LOAD), "Should contain STEADY_LOAD mode");
      assertTrue(
          containsMode(modes, AllocationMode.GROWING_HEAP), "Should contain GROWING_HEAP mode");
      assertTrue(
          containsMode(modes, AllocationMode.PROMOTION_STORM),
          "Should contain PROMOTION_STORM mode");
      assertTrue(
          containsMode(modes, AllocationMode.FRAGMENTATION), "Should contain FRAGMENTATION mode");
      assertTrue(
          containsMode(modes, AllocationMode.CROSS_GEN_REFS), "Should contain CROSS_GEN_REFS mode");
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
      assertEquals(ScenarioType.GROWING, AllocationMode.GROWING_HEAP.getScenarioType());
      assertEquals(ScenarioType.PROMOTION, AllocationMode.PROMOTION_STORM.getScenarioType());
      assertEquals(ScenarioType.FRAGMENTATION, AllocationMode.FRAGMENTATION.getScenarioType());
      assertEquals(ScenarioType.CROSS_REF, AllocationMode.CROSS_GEN_REFS.getScenarioType());
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
    @ValueSource(strings = {"OFF", "STEADY_LOAD", "GROWING_HEAP", "PROMOTION_STORM"})
    @DisplayName("Should handle enum valueOf correctly")
    void shouldHandleEnumValueOfCorrectly(String modeName) {
      assertDoesNotThrow(
          () -> {
            AllocationMode mode = AllocationMode.valueOf(modeName);
            assertNotNull(mode, "Mode should not be null");
            assertEquals(modeName, mode.name(), "Mode name should match");
          },
          "valueOf should work for all valid mode names");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for invalid mode names")
    void shouldThrowIllegalArgumentExceptionForInvalidModeNames() {
      String[] invalidNames = {"INVALID", "low", "high", "", " ", null};

      for (String invalidName : invalidNames) {
        if (invalidName != null) {
          assertThrows(
              IllegalArgumentException.class,
              () -> {
                AllocationMode.valueOf(invalidName);
              },
              () -> String.format("Should throw for invalid mode name: '%s'", invalidName));
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
