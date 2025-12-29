package fish.payara.trader.rest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import fish.payara.trader.pressure.AllocationMode;
import fish.payara.trader.pressure.MemoryPressureService;
import jakarta.ws.rs.core.Response;
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
    when(pressureService.getCurrentMode()).thenReturn(AllocationMode.STEADY_LOAD);
    when(pressureService.isRunning()).thenReturn(true);

    Response response = resource.getStatus();

    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    @SuppressWarnings("unchecked")
    Map<String, Object> entity = (Map<String, Object>) response.getEntity();
    assertEquals("STEADY_LOAD", entity.get("currentMode"));
    assertEquals(true, entity.get("running"));
  }

  @Test
  @DisplayName("Should set allocation mode successfully")
  void shouldSetAllocationModeSuccessfully() {
    Response response = resource.setMode("steady_load");

    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    verify(pressureService).setAllocationMode(AllocationMode.STEADY_LOAD);

    @SuppressWarnings("unchecked")
    Map<String, Object> entity = (Map<String, Object>) response.getEntity();
    assertTrue((Boolean) entity.get("success"));
    assertEquals("STEADY_LOAD", entity.get("mode"));
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
  @DisplayName("Should return all modes")
  void shouldReturnAllModes() {
    Response response = resource.getModes();

    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    @SuppressWarnings("unchecked")
    Map<String, Map<String, Object>> entity =
        (Map<String, Map<String, Object>>) response.getEntity();
    assertTrue(entity.containsKey("STEADY_LOAD"));
    assertTrue(entity.containsKey("PROMOTION_STORM"));
  }
}
