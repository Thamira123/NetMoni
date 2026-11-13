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
		List<DiscoveredDeviceRequest> devices,
		UUID scanId,
		Integer progress,
		Integer checked,
		Integer discovered,
		Integer failed,
		Long durationMs,
		Integer newDevices,
		Integer existingDevices) {
	public AgentTcpMessage(String type, UUID agentId, String name, String segmentCidr, String version, UUID deviceId,
		String checkType, Long responseTimeMs, Boolean available, BigDecimal packetLossPercent, Integer portNumber,
		Boolean portOpen, String snmpData, Instant checkedAt, String errorMessage, List<DiscoveredDeviceRequest> devices) {
		this(type,agentId,name,segmentCidr,version,deviceId,checkType,responseTimeMs,available,packetLossPercent,portNumber,portOpen,snmpData,checkedAt,errorMessage,devices,null,null,null,null,null,null,null,null);
	}
}
