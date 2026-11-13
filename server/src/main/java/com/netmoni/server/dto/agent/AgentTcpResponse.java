package com.netmoni.server.dto.agent;

import java.util.UUID;
import java.util.List;

import com.netmoni.server.entity.AgentStatus;

public record AgentTcpResponse(String type, UUID agentId, AgentStatus status, String message, UUID resultId,
		List<DiscoveredDeviceResponse> devices) {
	public static AgentTcpResponse registered(AgentResponse agent) {
		return new AgentTcpResponse("agent.registered", agent.agentId(), agent.status(), null, null, null);
	}

	public static AgentTcpResponse heartbeat(AgentResponse agent) {
		return new AgentTcpResponse("agent.heartbeat.accepted", agent.agentId(), agent.status(), null, null, null);
	}

	public static AgentTcpResponse error(String message) {
		return new AgentTcpResponse("error", null, null, message, null, null);
	}
	public static AgentTcpResponse monitoringResult(UUID agentId,UUID resultId){return new AgentTcpResponse("monitoring.result.accepted",agentId,AgentStatus.ONLINE,null,resultId,null);}
	public static AgentTcpResponse devicesRegistered(UUID agentId, List<DiscoveredDeviceResponse> devices) {
		return new AgentTcpResponse("device.discovery.accepted", agentId, AgentStatus.ONLINE, null, null, devices);
	}
}
