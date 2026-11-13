package com.netmoni.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.netmoni.server.dto.agent.AgentTcpMessage;
import com.netmoni.server.dto.agent.DiscoveredDeviceRequest;
import com.netmoni.server.dto.agent.DiscoveredDeviceResponse;
import com.netmoni.server.service.AgentRegistrationService;
import com.netmoni.server.service.DeviceService;
import com.netmoni.server.service.MonitoringResultService;
import com.netmoni.server.service.DiscoveryScanService;

import tools.jackson.databind.ObjectMapper;

class AgentTcpServerTest {
	@Test
	void registersDiscoveredBatchAndReturnsDeviceIdMappings() {
		UUID agentId = UUID.randomUUID();
		UUID deviceId = UUID.randomUUID();
		var discovered = List.of(new DiscoveredDeviceRequest("10.0.0.20", "printer", null));
		var registered = List.of(new DiscoveredDeviceResponse("10.0.0.20", deviceId));
		DeviceService deviceService = mock(DeviceService.class);
		when(deviceService.registerDiscovered(agentId, discovered)).thenReturn(registered);
		AgentTcpServer server = new AgentTcpServer(mock(ObjectMapper.class),
				mock(AgentRegistrationService.class), mock(MonitoringResultService.class), deviceService,
				"127.0.0.1", 9001);
		AgentTcpMessage message = new AgentTcpMessage("device.discovered", agentId, null, null, null,
				null, null, null, null, null, null, null, null, null, null, discovered);

		var response = server.processMessage(message);

		assertEquals("device.discovery.accepted", response.type());
		assertEquals(agentId, response.agentId());
		assertEquals(registered, response.devices());
		verify(deviceService).registerDiscovered(agentId, discovered);
		server.stop();
	}

	@Test void agentPollReceivesPersistedQueuedScanCommand() {
		UUID agentId=UUID.randomUUID(), scanId=UUID.randomUUID();
		DiscoveryScanService scans=mock(DiscoveryScanService.class); when(scans.poll(agentId)).thenReturn(scanId);
		AgentRegistrationService registrations=mock(AgentRegistrationService.class);
		when(registrations.getAgent(agentId)).thenReturn(new com.netmoni.server.dto.agent.AgentResponse(agentId,"edge","10.0.0.0/24","1",com.netmoni.server.entity.AgentStatus.ONLINE,java.time.Instant.now(),java.time.Instant.now()));
		AgentTcpServer server=new AgentTcpServer(mock(ObjectMapper.class),registrations,mock(MonitoringResultService.class),mock(DeviceService.class),scans,"127.0.0.1",9001);
		var response=server.processMessage(new AgentTcpMessage("discovery.poll",agentId,null,null,null,null,null,null,null,null,null,null,null,null,null,null));
		assertEquals("discovery.command",response.type()); assertEquals(scanId,response.scanId());
		server.stop();
	}
}
