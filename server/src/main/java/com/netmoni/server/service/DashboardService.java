package com.netmoni.server.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.netmoni.server.dto.dashboard.DashboardSummary;
import com.netmoni.server.entity.AgentStatus;
import com.netmoni.server.entity.DeviceStatus;
import com.netmoni.server.repository.AgentRepository;
import com.netmoni.server.repository.DeviceRepository;
import com.netmoni.server.repository.MonitoringResultRepository;

@Service
public class DashboardService {
	private final AgentRepository agents; private final DeviceRepository devices;
	private final MonitoringResultRepository results; private final MonitoringResultService resultService;
	public DashboardService(AgentRepository agents, DeviceRepository devices, MonitoringResultRepository results, MonitoringResultService resultService) {
		this.agents=agents; this.devices=devices; this.results=results; this.resultService=resultService;
	}
	@Transactional(readOnly=true)
	public DashboardSummary summary() {
		Instant since=Instant.now().minus(24, ChronoUnit.HOURS);
		long checked=results.countByCheckedAtAfter(since);
		long up=results.countByAvailableTrueAndCheckedAtAfter(since);
		return new DashboardSummary(agents.count(), agents.countByStatus(AgentStatus.ONLINE), agents.countByStatus(AgentStatus.OFFLINE),
				devices.countByMonitoringEnabledTrue(), devices.countByStatus(DeviceStatus.ONLINE), devices.countByStatus(DeviceStatus.OFFLINE),
				checked==0?0:100.0*up/checked, Instant.now(), resultService.recentActivities(20));
	}
}
