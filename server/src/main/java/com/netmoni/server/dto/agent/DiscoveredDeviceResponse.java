package com.netmoni.server.dto.agent;

import java.util.UUID;

public record DiscoveredDeviceResponse(String ipAddress, UUID deviceId, boolean created) {
 public DiscoveredDeviceResponse(String ipAddress, UUID deviceId) { this(ipAddress,deviceId,false); }
}
