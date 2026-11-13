package com.netmoni.agent.model;

import java.time.Instant;

public record DiscoveredDevice(String ipAddress, String hostname, Instant discoveredAt, String networkCidr, String interfaceName, String macAddress, String deviceType) {
    public DiscoveredDevice(String ipAddress,String hostname,Instant discoveredAt){this(ipAddress,hostname,discoveredAt,null,null,null,"UNKNOWN");}
}
