package com.netmoni.server.dto.agent;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AgentTcpMessage(
		String type,
		UUID agentId,
		String name,
		String segmentCidr,
		String version,
		UUID deviceId,
		String checkType,
		Long responseTimeMs,
		Boolean available,
		BigDecimal packetLossPercent,
		Integer portNumber,
		Boolean portOpen,
		String snmpData,
		Instant checkedAt,
		String errorMessage,
		List<DiscoveredDeviceRequest> devices) {
}
