package com.netmoni.server.dto.monitoring;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import jakarta.validation.constraints.*;

public record MonitoringResultRequest(
		@NotNull UUID deviceId, @NotNull UUID agentId,
		@NotBlank @Size(max = 40) String checkType,
		@PositiveOrZero Long responseTimeMs, boolean available,
		@DecimalMin("0.0") @DecimalMax("100.0") BigDecimal packetLossPercent,
		@Min(1) @Max(65535) Integer portNumber, Boolean portOpen,
		@Size(max = 10000) String snmpData, Instant checkedAt,
		@Size(max = 1000) String errorMessage) {}
