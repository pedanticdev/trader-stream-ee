package fish.payara.trader.rest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import fish.payara.trader.pressure.AllocationMode;
import fish.payara.trader.pressure.MemoryPressureService;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemoryPressureResource Tests")
class MemoryPressureResourceTest {

  @Mock private MemoryPressureService pressureService;

  @InjectMocks private MemoryPressureResource resource;

  @Test
  @DisplayName("Should return current status")
  void shouldReturnCurrentStatus() {
    when(pressureService.getCurrentMode()).thenReturn(AllocationMode.LOW);
    when(pressureService.isRunning()).thenReturn(true);

    Response response = resource.getStatus();

    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    @SuppressWarnings("unchecked")
    Map<String, Object> entity = (Map<String, Object>) response.getEntity();
    assertEquals("LOW", entity.get("currentMode"));
    assertEquals(true, entity.get("running"));
  }

  @Test
  @DisplayName("Should set allocation mode successfully")
  void shouldSetAllocationModeSuccessfully() {
    Response response = resource.setMode("high");

    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    verify(pressureService).setAllocationMode(AllocationMode.HIGH);

    @SuppressWarnings("unchecked")
    Map<String, Object> entity = (Map<String, Object>) response.getEntity();
    assertTrue((Boolean) entity.get("success"));
    assertEquals("HIGH", entity.get("mode"));
  }

  @Test
  @DisplayName("Should return bad request for invalid mode")
  void shouldReturnBadRequestForInvalidMode() {
    Response response = resource.setMode("invalid_mode");

    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    @SuppressWarnings("unchecked")
    Map<String, Object> entity = (Map<String, Object>) response.getEntity();
    assertFalse((Boolean) entity.get("success"));
    assertTrue(((String) entity.get("error")).contains("Invalid mode"));
  }

  @Test
  @DisplayName("Should apply scenario successfully")
  void shouldApplyScenarioSuccessfully() {
    Response response = resource.applyScenario("demo_stress");

    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    verify(pressureService).setAllocationMode(AllocationMode.HIGH);

    @SuppressWarnings("unchecked")
    Map<String, Object> entity = (Map<String, Object>) response.getEntity();
    assertEquals("DEMO_STRESS", entity.get("scenario"));
    assertEquals("applied", entity.get("status"));
  }

  @Test
  @DisplayName("Should list all scenarios")
  void shouldListAllScenarios() {
    Response response = resource.listScenarios();

    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    @SuppressWarnings("unchecked")
    List<Map<String, String>> entity = (List<Map<String, String>>) response.getEntity();
    assertFalse(entity.isEmpty());
    assertTrue(entity.stream().anyMatch(s -> s.get("name").equals("DEMO_EXTREME")));
  }

  @Test
  @DisplayName("Should return all modes")
  void shouldReturnAllModes() {
    Response response = resource.getModes();

    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    @SuppressWarnings("unchecked")
    Map<String, Map<String, Object>> entity =
        (Map<String, Map<String, Object>>) response.getEntity();
    assertTrue(entity.containsKey("EXTREME"));
    assertEquals(
        "4 GB/sec - Extreme pressure",
        ((Map<String, Object>) entity.get("EXTREME")).get("description"));
  }
}
