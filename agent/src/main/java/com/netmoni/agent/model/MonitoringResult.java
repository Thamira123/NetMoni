package com.netmoni.agent.model;

import java.math.BigDecimal;
import java.time.Instant;

public record MonitoringResult(
        String checkType,
        Long responseTimeMs,
        boolean available,
        BigDecimal packetLossPercent,
        Integer portNumber,
        Boolean portOpen,
        String snmpData,
        Instant checkedAt,
        String errorMessage) {
}
