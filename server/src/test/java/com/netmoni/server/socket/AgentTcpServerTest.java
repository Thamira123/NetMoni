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
}
