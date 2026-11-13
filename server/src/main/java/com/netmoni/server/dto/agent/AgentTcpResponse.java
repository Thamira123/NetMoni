package com.netmoni.server.dto.agent;

import java.util.UUID;
import java.util.List;

import com.netmoni.server.entity.AgentStatus;

public record AgentTcpResponse(String type, UUID agentId, AgentStatus status, String message, UUID resultId,
		List<DiscoveredDeviceResponse> devices, UUID scanId, String command) {
	public static AgentTcpResponse registered(AgentResponse agent) {
		return new AgentTcpResponse("agent.registered", agent.agentId(), agent.status(), null, null, null, null, null);
	}

	public static AgentTcpResponse heartbeat(AgentResponse agent) {
		return new AgentTcpResponse("agent.heartbeat.accepted", agent.agentId(), agent.status(), null, null, null, null, null);
	}

	public static AgentTcpResponse error(String message) {
		return new AgentTcpResponse("error", null, null, message, null, null, null, null);
	}
	public static AgentTcpResponse monitoringResult(UUID agentId,UUID resultId){return new AgentTcpResponse("monitoring.result.accepted",agentId,AgentStatus.ONLINE,null,resultId,null,null,null);}
	public static AgentTcpResponse devicesRegistered(UUID agentId, List<DiscoveredDeviceResponse> devices) {
		return new AgentTcpResponse("device.discovery.accepted", agentId, AgentStatus.ONLINE, null, null, devices, null, null);
	}
	public static AgentTcpResponse scanCommand(UUID agentId, UUID scanId) { return new AgentTcpResponse("discovery.command", agentId, AgentStatus.ONLINE, null, null, null, scanId, "scan"); }
	public static AgentTcpResponse scanIdle(UUID agentId) { return new AgentTcpResponse("discovery.idle", agentId, AgentStatus.ONLINE, null, null, null, null, null); }
}
