package com.netmoni.server.dto.agent;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AgentRegistrationRequest(
		@NotNull UUID agentId,
		@NotBlank @Size(max = 100) String name,
		@NotBlank @Size(max = 50) String segmentCidr,
		@NotBlank @Size(max = 50) String version) {
}
