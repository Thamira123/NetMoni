package com.netmoni.server.dto.agent;

import java.util.UUID;

import com.netmoni.server.entity.AgentStatus;

public record AgentTcpResponse(String type, UUID agentId, AgentStatus status, String message, UUID resultId) {
	public static AgentTcpResponse registered(AgentResponse agent) {
		return new AgentTcpResponse("agent.registered", agent.agentId(), agent.status(), null, null);
	}

	public static AgentTcpResponse heartbeat(AgentResponse agent) {
		return new AgentTcpResponse("agent.heartbeat.accepted", agent.agentId(), agent.status(), null, null);
	}

	public static AgentTcpResponse error(String message) {
		return new AgentTcpResponse("error", null, null, message, null);
	}
	public static AgentTcpResponse monitoringResult(UUID agentId,UUID resultId){return new AgentTcpResponse("monitoring.result.accepted",agentId,AgentStatus.ONLINE,null,resultId);}
}
