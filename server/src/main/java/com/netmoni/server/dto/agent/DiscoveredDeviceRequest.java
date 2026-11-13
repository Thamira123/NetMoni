package com.netmoni.server.dto.agent;

import java.time.Instant;

public record DiscoveredDeviceRequest(String ipAddress, String hostname, Instant discoveredAt,String networkCidr,String interfaceName,String macAddress,String deviceType) {
 public DiscoveredDeviceRequest(String ipAddress,String hostname,Instant discoveredAt){this(ipAddress,hostname,discoveredAt,null,null,null,"UNKNOWN");}
}
