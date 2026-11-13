package com.netmoni.server.dto.agent;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AgentRegistrationRequest(
		@NotNull UUID agentId,
		@NotBlank @Size(max = 100) String name,
		@NotBlank @Size(max = 50) String segmentCidr,
		@NotBlank @Size(max = 50) String version,
		java.util.List<String> authorizedCidrs) {
	public AgentRegistrationRequest(UUID agentId,String name,String segmentCidr,String version){this(agentId,name,segmentCidr,version,segmentCidr==null?java.util.List.of():java.util.List.of(segmentCidr));}
}
