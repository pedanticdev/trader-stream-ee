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
      assertEquals(5, modes.length, "Should have exactly 5 allocation modes");

      assertTrue(containsMode(modes, AllocationMode.OFF), "Should contain OFF mode");
      assertTrue(containsMode(modes, AllocationMode.LOW), "Should contain LOW mode");
      assertTrue(containsMode(modes, AllocationMode.MEDIUM), "Should contain MEDIUM mode");
      assertTrue(containsMode(modes, AllocationMode.HIGH), "Should contain HIGH mode");
      assertTrue(containsMode(modes, AllocationMode.EXTREME), "Should contain EXTREME mode");
    }

    @Test
    @DisplayName("Should have correct enum order from low to high pressure")
    void shouldHaveCorrectEnumOrderFromLowToHighPressure() {
      AllocationMode[] modes = AllocationMode.values();

      assertEquals(AllocationMode.OFF, modes[0], "OFF should be first");
      assertEquals(AllocationMode.LOW, modes[1], "LOW should be second");
      assertEquals(AllocationMode.MEDIUM, modes[2], "MEDIUM should be third");
      assertEquals(AllocationMode.HIGH, modes[3], "HIGH should be fourth");
      assertEquals(AllocationMode.EXTREME, modes[4], "EXTREME should be last");
    }
  }

  @Nested
  @DisplayName("Allocation Properties Tests")
  class AllocationPropertiesTests {

    @ParameterizedTest
    @EnumSource(AllocationMode.class)
    @DisplayName("Should have correct allocation per iteration values")
    void shouldHaveCorrectAllocationPerIterationValues(AllocationMode mode) {
      int expected =
          switch (mode) {
            case OFF -> 0;
            case LOW -> 20;
            case MEDIUM -> 200;
            case HIGH -> 10000;
            case EXTREME -> 40000;
          };

      assertEquals(
          expected,
          mode.getAllocationsPerIteration(),
          String.format("Mode %s should have %d allocations per iteration", mode.name(), expected));
    }

    @ParameterizedTest
    @EnumSource(AllocationMode.class)
    @DisplayName("Should have correct bytes per allocation values")
    void shouldHaveCorrectBytesPerAllocationValues(AllocationMode mode) {
      int expected =
          switch (mode) {
            case OFF -> 0;
            default -> 10240;
          };
      assertEquals(
          expected,
          mode.getBytesPerAllocation(),
          String.format("Mode %s should have %d bytes per allocation", mode.name(), expected));
    }

    @ParameterizedTest
    @EnumSource(AllocationMode.class)
    @DisplayName("Should have meaningful descriptions")
    void shouldHaveMeaningfulDescriptions(AllocationMode mode) {
      String description = mode.getDescription();

      assertNotNull(description, "Description should not be null");
      assertFalse(description.trim().isEmpty(), "Description should not be empty");
      assertTrue(description.length() > 10, "Description should be descriptive");
    }
  }

  @Nested
  @DisplayName("Bytes Per Second Calculation Tests")
  class BytesPerSecondCalculationTests {

    @Test
    @DisplayName("Should calculate bytes per second correctly for OFF mode")
    void shouldCalculateBytesPerSecondCorrectlyForOffMode() {
      assertEquals(
          0L,
          AllocationMode.OFF.getBytesPerSecond(),
          "OFF mode should allocate 0 bytes per second");
    }

    @Test
    @DisplayName("Should calculate bytes per second correctly for LOW mode")
    void shouldCalculateBytesPerSecondCorrectlyForLowMode() {
      // 20 allocations * 10240 bytes * 10 iterations/sec = 2,048,000 bytes/sec
      assertEquals(
          2_048_000L, AllocationMode.LOW.getBytesPerSecond(), "LOW mode should allocate 2 MB/sec");
    }

    @Test
    @DisplayName("Should calculate bytes per second correctly for MEDIUM mode")
    void shouldCalculateBytesPerSecondCorrectlyForMediumMode() {
      // 200 allocations * 10240 bytes * 10 iterations/sec = 20,480,000 bytes/sec
      assertEquals(
          20_480_000L,
          AllocationMode.MEDIUM.getBytesPerSecond(),
          "MEDIUM mode should allocate 20 MB/sec");
    }

    @Test
    @DisplayName("Should calculate bytes per second correctly for HIGH mode")
    void shouldCalculateBytesPerSecondCorrectlyForHighMode() {
      // 10000 allocations * 10240 bytes * 10 iterations/sec = 1,024,000,000 bytes/sec
      assertEquals(
          1_024_000_000L,
          AllocationMode.HIGH.getBytesPerSecond(),
          "HIGH mode should allocate 1 GB/sec");
    }

    @Test
    @DisplayName("Should calculate bytes per second correctly for EXTREME mode")
    void shouldCalculateBytesPerSecondCorrectlyForExtremeMode() {
      // 40000 allocations * 10240 bytes * 10 iterations/sec = 4,096,000,000 bytes/sec
      assertEquals(
          4_096_000_000L,
          AllocationMode.EXTREME.getBytesPerSecond(),
          "EXTREME mode should allocate 4 GB/sec");
    }
  }

  @Nested
  @DisplayName("Progressive Intensity Tests")
  class ProgressiveIntensityTests {

    @Test
    @DisplayName("Should have progressively increasing allocation rates")
    void shouldHaveProgressivelyIncreasingAllocationRates() {
      long previousRate = -1;

      for (AllocationMode mode : AllocationMode.values()) {
        long currentRate = mode.getBytesPerSecond();

        assertTrue(
            currentRate >= previousRate,
            String.format(
                "%s (%d bytes/sec) should be >= previous rate (%d)",
                mode.name(), currentRate, previousRate));

        previousRate = currentRate;
      }
    }

    @Test
    @DisplayName("Should have monotonic allocation per iteration progression")
    void shouldHaveMonotonicAllocationPerIterationProgression() {
      int previousAllocations = -1;

      for (AllocationMode mode : AllocationMode.values()) {
        int currentAllocations = mode.getAllocationsPerIteration();

        assertTrue(
            currentAllocations >= previousAllocations,
            String.format(
                "%s (%d allocations) should be >= previous (%d)",
                mode.name(), currentAllocations, previousAllocations));

        previousAllocations = currentAllocations;
      }
    }

    @Test
    @DisplayName("Should have significant gaps between modes")
    void shouldHaveSignificantGapsBetweenModes() {
      // Verify that there are meaningful differences between modes
      assertTrue(
          AllocationMode.LOW.getBytesPerSecond() > AllocationMode.OFF.getBytesPerSecond(),
          "LOW should be significantly higher than OFF");

      assertTrue(
          AllocationMode.MEDIUM.getBytesPerSecond() > AllocationMode.LOW.getBytesPerSecond() * 5,
          "MEDIUM should be at least 5x higher than LOW");

      assertTrue(
          AllocationMode.HIGH.getBytesPerSecond() > AllocationMode.MEDIUM.getBytesPerSecond() * 40,
          "HIGH should be at least 40x higher than MEDIUM");

      assertTrue(
          AllocationMode.EXTREME.getBytesPerSecond() > AllocationMode.HIGH.getBytesPerSecond() * 2,
          "EXTREME should be at least 2x higher than HIGH");
    }
  }

  @Nested
  @DisplayName("Description Content Tests")
  class DescriptionContentTests {

    @Test
    @DisplayName("Descriptions should contain pressure level indicators")
    void descriptionsShouldContainPressureLevelIndicators() {
      assertTrue(
          AllocationMode.LOW.getDescription().contains("Light"),
          "LOW description should contain 'Light'");

      assertTrue(
          AllocationMode.MEDIUM.getDescription().contains("Moderate"),
          "MEDIUM description should contain 'Moderate'");

      assertTrue(
          AllocationMode.HIGH.getDescription().contains("Heavy"),
          "HIGH description should contain 'Heavy'");

      assertTrue(
          AllocationMode.EXTREME.getDescription().contains("Extreme"),
          "EXTREME description should contain 'Extreme'");

      assertTrue(
          AllocationMode.OFF.getDescription().contains("No"),
          "OFF description should contain 'No'");
    }

    @Test
    @DisplayName("Descriptions should contain rate information")
    void descriptionsShouldContainRateInformation() {
      assertTrue(
          AllocationMode.LOW.getDescription().contains("2 MB/sec"),
          "LOW description should contain rate information");

      assertTrue(
          AllocationMode.MEDIUM.getDescription().contains("20 MB/sec"),
          "MEDIUM description should contain rate information");

      assertTrue(
          AllocationMode.HIGH.getDescription().contains("1 GB/sec"),
          "HIGH description should contain rate information");

      assertTrue(
          AllocationMode.EXTREME.getDescription().contains("4 GB/sec"),
          "EXTREME description should contain rate information");
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Should handle zero allocations correctly")
    void shouldHandleZeroAllocationsCorrectly() {
      assertEquals(
          0,
          AllocationMode.OFF.getAllocationsPerIteration(),
          "OFF mode should have zero allocations");

      assertEquals(
          0L,
          AllocationMode.OFF.getBytesPerSecond(),
          "OFF mode should result in zero bytes per second");
    }

    @Test
    @DisplayName("Should handle large allocations correctly")
    void shouldHandleLargeAllocationsCorrectly() {
      long extremeRate = AllocationMode.EXTREME.getBytesPerSecond();

      assertTrue(
          extremeRate > 1_000_000_000L, // 1GB
          "EXTREME mode should handle large allocations (>1GB/sec)");

      assertEquals(4_096_000_000L, extremeRate, "EXTREME mode should handle exactly 4GB/sec");
    }

    @ParameterizedTest
    @ValueSource(strings = {"OFF", "LOW", "MEDIUM", "HIGH", "EXTREME"})
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

  @Nested
  @DisplayName("Constants Validation Tests")
  class ConstantsValidationTests {

    @Test
    @DisplayName("Should validate constant values")
    void shouldValidateConstantValues() {
      // Validate that bytes per allocation follows expected pattern
      for (AllocationMode mode : AllocationMode.values()) {
        int expectedBytes = (mode == AllocationMode.OFF) ? 0 : 10240;
        assertEquals(
            expectedBytes,
            mode.getBytesPerAllocation(),
            String.format("%s should use %d bytes per allocation", mode.name(), expectedBytes));
      }

      // Validate that calculation formula is consistent
      for (AllocationMode mode : AllocationMode.values()) {
        long expected =
            (long) mode.getAllocationsPerIteration() * mode.getBytesPerAllocation() * 10;
        assertEquals(
            expected,
            mode.getBytesPerSecond(),
            String.format("Calculation should be consistent for %s", mode.name()));
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
