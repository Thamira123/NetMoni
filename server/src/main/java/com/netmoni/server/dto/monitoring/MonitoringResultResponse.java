package com.netmoni.server.dto.monitoring;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MonitoringResultResponse(UUID resultId, UUID deviceId, UUID agentId, String checkType,
		Long responseTimeMs, boolean available, BigDecimal packetLossPercent, Integer portNumber,
		Boolean portOpen, String snmpData, Instant checkedAt, String errorMessage) {}
