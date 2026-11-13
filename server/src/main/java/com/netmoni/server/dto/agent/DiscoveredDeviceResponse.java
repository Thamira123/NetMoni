package com.netmoni.server.dto.agent;

import java.util.UUID;

public record DiscoveredDeviceResponse(String ipAddress, UUID deviceId) {
}
