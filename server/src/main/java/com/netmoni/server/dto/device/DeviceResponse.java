package com.netmoni.server.dto.device;

import java.time.Instant;
import java.util.UUID;
import com.netmoni.server.entity.DeviceStatus;

public record DeviceResponse(UUID deviceId, String name, String ipAddress, String hostname,
		String deviceType, String location, UUID agentId, DeviceStatus status,
		boolean monitoringEnabled, Instant createdAt, Instant updatedAt,String networkCidr,String interfaceName,
		String macAddress,Instant lastSeenAt) {}
