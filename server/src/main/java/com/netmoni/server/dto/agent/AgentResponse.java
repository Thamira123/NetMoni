package com.netmoni.server.dto.agent;

import java.time.Instant;
import java.util.UUID;

import com.netmoni.server.entity.AgentStatus;

public record AgentResponse(
		UUID agentId,
		String name,
		String segmentCidr,
		String version,
		AgentStatus status,
		Instant registeredAt,
		Instant lastHeartbeat,
		java.util.List<String> authorizedCidrs) {
	public AgentResponse(UUID agentId,String name,String segmentCidr,String version,AgentStatus status,Instant registeredAt,Instant lastHeartbeat){this(agentId,name,segmentCidr,version,status,registeredAt,lastHeartbeat,java.util.List.of(segmentCidr));}
}
