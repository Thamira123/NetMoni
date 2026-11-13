package com.netmoni.server.dto.device;

import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DeviceRequest(
		@Size(max = 120) @NotBlank String name,
		@Size(max = 45) @NotBlank String ipAddress,
		@Size(max = 253) String hostname,
		@Size(max = 40) @NotBlank String deviceType,
		@Size(max = 160) String location,
		@NotNull UUID agentId) {}
