package com.netmoni.server.service;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.netmoni.server.entity.*;
import com.netmoni.server.repository.*;
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {
	@Mock AgentRepository agents; @Mock DeviceRepository devices; @Mock MonitoringResultRepository results; @Mock MonitoringResultService resultService;
	@InjectMocks DashboardService service;
	@Test void summarizesCountsAndRecentActivity(){when(agents.count()).thenReturn(3L);when(agents.countByStatus(AgentStatus.ONLINE)).thenReturn(2L);when(agents.countByStatus(AgentStatus.OFFLINE)).thenReturn(1L);when(devices.countByMonitoringEnabledTrue()).thenReturn(7L);when(devices.countByStatus(DeviceStatus.ONLINE)).thenReturn(5L);when(devices.countByStatus(DeviceStatus.OFFLINE)).thenReturn(2L);when(results.countByCheckedAtAfter(any())).thenReturn(4L);when(results.countByAvailableTrueAndCheckedAtAfter(any())).thenReturn(3L);when(resultService.recentActivities(20)).thenReturn(List.of());
		var summary=service.summary();assertEquals(3,summary.totalAgents());assertEquals(2,summary.onlineAgents());assertEquals(75.0,summary.availabilityPercent24h());assertNotNull(summary.generatedAt());}
}
