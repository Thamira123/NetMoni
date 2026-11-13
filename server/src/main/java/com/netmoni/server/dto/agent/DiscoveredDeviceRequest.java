package com.netmoni.server.dto.agent;

import java.time.Instant;

public record DiscoveredDeviceRequest(String ipAddress, String hostname, Instant discoveredAt) {
}
