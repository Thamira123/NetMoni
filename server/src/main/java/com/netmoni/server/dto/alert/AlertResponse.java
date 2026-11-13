package com.netmoni.server.dto.alert;
import java.time.Instant;
import java.util.UUID;
import com.netmoni.server.entity.AlertSeverity;
import com.netmoni.server.entity.AlertStatus;
public record AlertResponse(UUID alertId,String type,AlertSeverity severity,AlertStatus status,String message,
	UUID deviceId,UUID agentId,Instant createdAt,Instant resolvedAt) {}
