package com.netmoni.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netmoni.agent.config.Ipv4Cidr;

class AgentRuntimeTest {
	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	void addsBackendAssignedIdsOnlyForAddressesInsideTheConfiguredSubnet() throws Exception {
		UUID deviceId = UUID.randomUUID();
		Map<String, UUID> mappings = new HashMap<>();
		var response = mapper.readTree("""
				[{"ipAddress":"10.0.0.20","deviceId":"%s"},
				 {"ipAddress":"10.0.1.20","deviceId":"%s"}]
				""".formatted(deviceId, UUID.randomUUID()));

		int accepted = AgentRuntime.mergeDeviceMappings(Ipv4Cidr.parse("10.0.0.0/24"), mappings, response);

		assertEquals(1, accepted);
		assertEquals(Map.of("10.0.0.20", deviceId), mappings);
	}

	@Test
	void rejectsMalformedDiscoveryAcknowledgement() {
		assertThrows(IOException.class,
				() -> AgentRuntime.mergeDeviceMappings(Ipv4Cidr.parse("10.0.0.0/24"), new HashMap<>(), mapper.createObjectNode()));
	}
}
