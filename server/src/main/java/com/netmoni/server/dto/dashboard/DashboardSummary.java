package com.netmoni.server.dto.dashboard;

import java.time.Instant;
import java.util.List;
import com.netmoni.server.dto.monitoring.MonitoringResultResponse;

public record DashboardSummary(long totalAgents, long onlineAgents, long offlineAgents,
		long totalMonitoredDevices, long onlineDevices, long offlineDevices,
		double availabilityPercent24h, Instant generatedAt, List<MonitoringResultResponse> recentActivities) {}
