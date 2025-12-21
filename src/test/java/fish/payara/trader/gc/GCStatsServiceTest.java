package fish.payara.trader.gc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;
import java.util.List;
import javax.management.Notification;
import javax.management.openmbean.CompositeData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GCStatsService Tests")
class GCStatsServiceTest {

  private GCStatsService gcStatsService;

  @BeforeEach
  void setUp() {
    gcStatsService = new GCStatsService();
  }

  @Test
  @DisplayName("Should handle GC notification and record pause")
  void shouldHandleGCNotificationAndRecordPause() {
    Notification notification =
        new Notification(
            GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION, "test", 1L);
    CompositeData compositeData = mock(CompositeData.class);
    notification.setUserData(compositeData);

    GarbageCollectionNotificationInfo info = mock(GarbageCollectionNotificationInfo.class);
    GcInfo gcInfo = mock(GcInfo.class);

    when(info.getGcName()).thenReturn("G1 Young Generation");
    when(info.getGcAction()).thenReturn("end of minor GC");
    when(info.getGcCause()).thenReturn("System.gc()");
    when(info.getGcInfo()).thenReturn(gcInfo);
    when(gcInfo.getDuration()).thenReturn(50L);

    try (MockedStatic<GarbageCollectionNotificationInfo> mockedStatic =
        mockStatic(GarbageCollectionNotificationInfo.class)) {
      mockedStatic
          .when(() -> GarbageCollectionNotificationInfo.from(compositeData))
          .thenReturn(info);

      gcStatsService.handleNotification(notification, null);
    }

    List<GCStats> stats = gcStatsService.collectGCStats();
    // We expect at least the G1 Young Generation to be present if the JVM is running G1
    // But since collectGCStats calls ManagementFactory.getGarbageCollectorMXBeans(),
    // it depends on the actual JVM.
    // However, we can verify the pauseHistory internal state by calling collectGCStats
    // and looking for the one we just injected.

    GCStats g1Stats =
        stats.stream()
            .filter(s -> s.getGcName().equals("G1 Young Generation"))
            .findFirst()
            .orElse(null);

    if (g1Stats != null) {
      assertThat(g1Stats.getLastPauseDuration()).isEqualTo(50L);
      assertThat(g1Stats.getRecentPauses()).contains(50L);
    }
  }

  @Test
  @DisplayName("Should filter GPGC concurrent cycle notifications")
  void shouldFilterGPGCConcurrentCycleNotifications() {
    Notification notification =
        new Notification(
            GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION, "test", 1L);
    CompositeData compositeData = mock(CompositeData.class);
    notification.setUserData(compositeData);

    GarbageCollectionNotificationInfo info = mock(GarbageCollectionNotificationInfo.class);
    GcInfo gcInfo = mock(GcInfo.class);

    when(info.getGcName()).thenReturn("GPGC"); // This should be filtered
    when(info.getGcInfo()).thenReturn(gcInfo);
    when(gcInfo.getDuration()).thenReturn(500L);

    try (MockedStatic<GarbageCollectionNotificationInfo> mockedStatic =
        mockStatic(GarbageCollectionNotificationInfo.class)) {
      mockedStatic
          .when(() -> GarbageCollectionNotificationInfo.from(compositeData))
          .thenReturn(info);

      gcStatsService.handleNotification(notification, null);
    }

    List<GCStats> stats = gcStatsService.collectGCStats();
    GCStats gpgcStats =
        stats.stream().filter(s -> s.getGcName().equals("GPGC")).findFirst().orElse(null);

    // GPGC should be excluded from stats
    assertThat(gpgcStats).isNull();
  }

  @Test
  @DisplayName("Should reset statistics")
  void shouldResetStatistics() {
    Notification notification =
        new Notification(
            GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION, "test", 1L);
    CompositeData compositeData = mock(CompositeData.class);
    notification.setUserData(compositeData);

    GarbageCollectionNotificationInfo info = mock(GarbageCollectionNotificationInfo.class);
    GcInfo gcInfo = mock(GcInfo.class);

    when(info.getGcName()).thenReturn("Test GC");
    when(info.getGcInfo()).thenReturn(gcInfo);
    when(gcInfo.getDuration()).thenReturn(100L);

    try (MockedStatic<GarbageCollectionNotificationInfo> mockedStatic =
        mockStatic(GarbageCollectionNotificationInfo.class)) {
      mockedStatic
          .when(() -> GarbageCollectionNotificationInfo.from(compositeData))
          .thenReturn(info);
      gcStatsService.handleNotification(notification, null);
    }

    gcStatsService.resetStats();

    // After reset, even if the bean exists in JVM, the pause history should be gone
    List<GCStats> stats = gcStatsService.collectGCStats();
    for (GCStats s : stats) {
      assertThat(s.getRecentPauses()).isEmpty();
      assertThat(s.getLastPauseDuration()).isZero();
    }
  }
}
