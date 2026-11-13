package com.netmoni.agent.model;

import java.time.Instant;

public record DiscoveredDevice(String ipAddress, String hostname, Instant discoveredAt) {
}
